from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class LoginIn(BaseModel):
    nickname: str = Field(min_length=1, max_length=32)
    phone: str | None = None


class LoginOut(BaseModel):
    token: str
    user_id: int
    nickname: str


class MemberOut(BaseModel):
    id: int
    user_id: int
    nickname: str
    relation: str
    role: str
    push_enabled: bool


class FamilyOut(BaseModel):
    id: int
    name: str
    invite_code: str
    owner_user_id: int
    members: list[MemberOut]


class FamilyIn(BaseModel):
    name: str = "我的家庭"


class JoinIn(BaseModel):
    invite_code: str
    relation: str = "家人"


class MemberPatch(BaseModel):
    relation: str | None = None
    push_enabled: bool | None = None


class PlanIn(BaseModel):
    family_id: int
    member_id: int
    medicine_name: str
    dose: str = "1 次用量"
    times: list[str] = Field(default_factory=list)
    start_date: str
    days: int = 7
    # 本次写入的时间与改动字段：只对 changed_fields 里的字段做 LWW 比较，
    # 没改的字段保留服务端当前值，避免整条覆盖掉家人的修改。
    updated_at: datetime | None = None
    changed_fields: list[str] | None = None


class PlanOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    family_id: int
    member_id: int
    medicine_name: str
    dose: str
    times: list[str]
    start_date: str
    days: int
    deleted: bool
    field_times: dict[str, str] = Field(default_factory=dict)
    updated_at: datetime


class DoseCheckIn(BaseModel):
    plan_id: str
    date: str
    time: str
    taken: bool = True


class DoseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    plan_id: str
    family_id: int
    member_id: int
    date: str
    time: str
    status: str
    notified_at: datetime | None


class DeviceIn(BaseModel):
    platform: str = "android"
    push_token: str


class NotificationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    plan_id: str
    date: str
    time: str
    kind: str
    title: str
    body: str
    created_at: datetime


class NotificationAckIn(BaseModel):
    ids: list[int] = Field(default_factory=list)


class FamilyEventIn(BaseModel):
    family_id: int
    type: str = Field(min_length=1, max_length=32)
    plan_id: str = ""
    member_name: str = ""
    medicine_name: str = ""
    time: str = ""


class FamilyEventOut(BaseModel):
    id: int
    type: str
    actor_name: str
    plan_id: str
    member_name: str
    medicine_name: str
    time: str
    created_at: datetime


class DrugOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    generic_name: str
    aliases: list[str] = Field(default_factory=list)
    category: str
    class_group: str
    note: str
    dosage_forms: list[str] = Field(default_factory=list)
    adult_dose: str
    elder_note: str
    child_note: str
    pregnancy_note: str
    cautions: str
    updated_at: datetime


class InteractionRuleOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    group_a: list[str] = Field(default_factory=list)
    group_b: list[str] = Field(default_factory=list)
    severity: str
    title: str
    detail: str


class SafetyCheckIn(BaseModel):
    names: list[str] = Field(min_length=1, max_length=50)


class SafetyMatchOut(BaseModel):
    """一条输入药名 → 命中的通用名（成分），未收录时 matched 为 False。"""

    input: str
    matched: bool
    generic_name: str = ""
    category: str = ""
    class_group: str = ""
    note: str = ""


class SafetyDuplicateOut(BaseModel):
    """同成分重复：多个商品名其实是一种药。"""

    generic_name: str
    names: list[str]
    note: str = ""


class SafetyInteractionOut(BaseModel):
    severity: str
    title: str
    detail: str
    medicine_names: list[str]


class SafetyCheckOut(BaseModel):
    items: list[SafetyMatchOut]
    duplicates: list[SafetyDuplicateOut]
    interactions: list[SafetyInteractionOut]
