from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from .clock import now_local
from .db import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    nickname: Mapped[str] = mapped_column(String(64))
    phone: Mapped[str | None] = mapped_column(String(32), unique=True, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class Family(Base):
    __tablename__ = "families"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(64), default="我的家庭")
    invite_code: Mapped[str] = mapped_column(String(8), unique=True)
    owner_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class FamilyMember(Base):
    __tablename__ = "family_members"
    __table_args__ = (UniqueConstraint("family_id", "user_id", name="uq_family_user"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    family_id: Mapped[int] = mapped_column(ForeignKey("families.id"))
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    role: Mapped[str] = mapped_column(String(16), default="member")  # owner / member
    relation: Mapped[str] = mapped_column(String(16), default="家人")
    push_enabled: Mapped[bool] = mapped_column(Boolean, default=True)


class Plan(Base):
    __tablename__ = "plans"

    # 计划 id 由 App 生成（UUID），便于离线新增后直接同步。
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    family_id: Mapped[int] = mapped_column(ForeignKey("families.id"))
    member_id: Mapped[int] = mapped_column(ForeignKey("family_members.id"))
    medicine_name: Mapped[str] = mapped_column(String(128))
    dose: Mapped[str] = mapped_column(String(64), default="1 次用量")
    times: Mapped[list] = mapped_column(JSON, default=list)
    start_date: Mapped[str] = mapped_column(String(10))
    days: Mapped[int] = mapped_column(Integer, default=7)
    deleted: Mapped[bool] = mapped_column(Boolean, default=False)
    # 字段级 LWW：{字段名: 最后一次写入的 ISO 时间}，用来判断两台设备谁的值更新。
    field_times: Mapped[dict] = mapped_column(JSON, default=dict)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class DoseLog(Base):
    __tablename__ = "dose_logs"
    __table_args__ = (
        UniqueConstraint("plan_id", "date", "time", name="uq_plan_date_time"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    plan_id: Mapped[str] = mapped_column(String(64))
    family_id: Mapped[int] = mapped_column(Integer)
    member_id: Mapped[int] = mapped_column(Integer)
    date: Mapped[str] = mapped_column(String(10))
    time: Mapped[str] = mapped_column(String(5))
    status: Mapped[str] = mapped_column(String(16), default="pending")  # pending / taken / skipped
    notified_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    follow_up_sent: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class Notification(Base):
    """给某个成员排队的提醒：客户端轮询拉取后本地发通知，再回执确认。"""

    __tablename__ = "notifications"
    __table_args__ = (
        UniqueConstraint(
            "recipient_user_id", "plan_id", "date", "time", "kind",
            name="uq_notification_slot",
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    recipient_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    family_id: Mapped[int] = mapped_column(Integer)
    plan_id: Mapped[str] = mapped_column(String(64))
    date: Mapped[str] = mapped_column(String(10))
    time: Mapped[str] = mapped_column(String(5))
    kind: Mapped[str] = mapped_column(String(16), default="due")  # due / overdue / escalation
    title: Mapped[str] = mapped_column(String(128))
    body: Mapped[str] = mapped_column(String(256))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)
    delivered_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class Device(Base):
    __tablename__ = "devices"
    __table_args__ = (UniqueConstraint("push_token", name="uq_push_token"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    platform: Mapped[str] = mapped_column(String(16), default="android")
    push_token: Mapped[str] = mapped_column(String(256))
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class FamilyEvent(Base):
    """家庭动态事件流：打卡、取消等动作按时间追加，家人端按 id 增量拉取。"""

    __tablename__ = "family_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    family_id: Mapped[int] = mapped_column(ForeignKey("families.id"))
    actor_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    actor_name: Mapped[str] = mapped_column(String(64), default="")
    type: Mapped[str] = mapped_column(String(32))
    plan_id: Mapped[str] = mapped_column(String(64), default="")
    member_name: Mapped[str] = mapped_column(String(64), default="")
    medicine_name: Mapped[str] = mapped_column(String(128), default="")
    slot_time: Mapped[str] = mapped_column(String(8), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class Drug(Base):
    """药品目录：通用名 + 商品名别名，是「同成分查重」和用药安全的底座。

    目录不属于任何家庭组，可随 App 版本增量下发，公共只读。
    """

    __tablename__ = "drugs"
    __table_args__ = (UniqueConstraint("generic_name", name="uq_drug_generic"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    generic_name: Mapped[str] = mapped_column(String(64))
    # 别名 / 常见商品名，如 泰诺、芬必得
    aliases: Mapped[list] = mapped_column(JSON, default=list)
    category: Mapped[str] = mapped_column(String(32), default="")
    # 药理类别，用于「同类药重复」提示
    class_group: Mapped[str] = mapped_column(String(32), default="")
    note: Mapped[str] = mapped_column(String(512), default="")
    dosage_forms: Mapped[list] = mapped_column(JSON, default=list)
    adult_dose: Mapped[str] = mapped_column(String(128), default="")
    elder_note: Mapped[str] = mapped_column(String(256), default="")
    child_note: Mapped[str] = mapped_column(String(256), default="")
    pregnancy_note: Mapped[str] = mapped_column(String(256), default="")
    cautions: Mapped[str] = mapped_column(String(512), default="")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)


class InteractionRule(Base):
    """两条（或一类）药物之间的相互作用规则，关键词按包含匹配。"""

    __tablename__ = "interaction_rules"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    group_a: Mapped[list] = mapped_column(JSON, default=list)
    group_b: Mapped[list] = mapped_column(JSON, default=list)
    severity: Mapped[str] = mapped_column(String(16), default="medium")
    title: Mapped[str] = mapped_column(String(64))
    detail: Mapped[str] = mapped_column(String(512))
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=now_local)
