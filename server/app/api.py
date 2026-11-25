import asyncio
import json
import secrets
import string
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from .clock import TZ, now_local
from .db import SessionLocal, get_db
from .models import (
    Device,
    DoseLog,
    Drug,
    Family,
    FamilyEvent,
    FamilyMember,
    InteractionRule,
    Notification,
    Plan,
    User,
)
from .schemas import (
    DeviceIn,
    DoseCheckIn,
    DoseOut,
    DrugOut,
    FamilyEventIn,
    FamilyEventOut,
    FamilyIn,
    FamilyOut,
    InteractionRuleOut,
    JoinIn,
    LoginIn,
    LoginOut,
    MemberOut,
    MemberPatch,
    NotificationAckIn,
    NotificationOut,
    PlanIn,
    PlanOut,
    SafetyCheckIn,
    SafetyCheckOut,
    SafetyDuplicateOut,
    SafetyInteractionOut,
    SafetyMatchOut,
)
from .security import create_token, get_current_user

router = APIRouter()


# ---------------- 工具函数 ----------------


def _member_out(member: FamilyMember, user: User) -> MemberOut:
    return MemberOut(
        id=member.id,
        user_id=member.user_id,
        nickname=user.nickname,
        relation=member.relation,
        role=member.role,
        push_enabled=member.push_enabled,
    )


def _family_out(db: Session, family: Family) -> FamilyOut:
    members = db.query(FamilyMember).filter(FamilyMember.family_id == family.id).all()
    user_ids = [member.user_id for member in members]
    users = (
        {user.id: user for user in db.query(User).filter(User.id.in_(user_ids)).all()}
        if user_ids
        else {}
    )
    return FamilyOut(
        id=family.id,
        name=family.name,
        invite_code=family.invite_code,
        owner_user_id=family.owner_user_id,
        members=[_member_out(member, users[member.user_id]) for member in members],
    )


def _memberships(db: Session, user: User) -> list[FamilyMember]:
    return db.query(FamilyMember).filter(FamilyMember.user_id == user.id).all()


def _require_family(db: Session, user: User, family_id: int) -> FamilyMember:
    membership = (
        db.query(FamilyMember)
        .filter(FamilyMember.family_id == family_id, FamilyMember.user_id == user.id)
        .one_or_none()
    )
    if membership is None:
        raise HTTPException(403, "你不在该家庭组中")
    return membership


def _new_invite_code(db: Session) -> str:
    alphabet = string.ascii_uppercase + string.digits
    for _ in range(20):
        code = "".join(secrets.choice(alphabet) for _ in range(6))
        if db.query(Family).filter(Family.invite_code == code).first() is None:
            return code
    raise HTTPException(500, "邀请码生成失败，请重试")


def _naive(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt
    return dt.astimezone(TZ).replace(tzinfo=None)


# 可被字段级 LWW 合并的计划字段（body 属性名与 ORM 列名一致）。
PLAN_FIELD_MAP = {
    "medicine_name": "medicine_name",
    "dose": "dose",
    "times": "times",
    "start_date": "start_date",
    "days": "days",
}
PLAN_FIELDS = tuple(PLAN_FIELD_MAP)


def _stamp(value: datetime | None) -> str:
    """把客户端写入时间统一成 ISO 字符串，空值按服务器当前时间处理。"""
    return _naive(value or now_local()).isoformat()


# ---------------- 登录 ----------------


@router.post("/auth/login", response_model=LoginOut, summary="演示登录：昵称换 token")
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = None
    if body.phone:
        user = db.query(User).filter(User.phone == body.phone).one_or_none()
    if user is None:
        user = User(nickname=body.nickname, phone=body.phone)
        db.add(user)
        db.commit()
        db.refresh(user)
    elif user.nickname != body.nickname:
        user.nickname = body.nickname
        db.commit()
    return LoginOut(token=create_token(user.id), user_id=user.id, nickname=user.nickname)


# ---------------- 家庭组 ----------------


@router.post("/families", response_model=FamilyOut, summary="创建家庭组")
def create_family(
    body: FamilyIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    family = Family(name=body.name, invite_code=_new_invite_code(db), owner_user_id=user.id)
    db.add(family)
    db.flush()
    db.add(FamilyMember(family_id=family.id, user_id=user.id, role="owner", relation="我"))
    db.commit()
    db.refresh(family)
    return _family_out(db, family)


@router.post("/families/join", response_model=FamilyOut, summary="用邀请码加入")
def join_family(
    body: JoinIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    family = (
        db.query(Family)
        .filter(Family.invite_code == body.invite_code.strip().upper())
        .one_or_none()
    )
    if family is None:
        raise HTTPException(404, "邀请码无效")
    exists = (
        db.query(FamilyMember)
        .filter(FamilyMember.family_id == family.id, FamilyMember.user_id == user.id)
        .one_or_none()
    )
    if exists is None:
        db.add(FamilyMember(family_id=family.id, user_id=user.id, relation=body.relation))
        db.commit()
    return _family_out(db, family)


@router.get("/families/me", response_model=list[FamilyOut], summary="我的家庭组")
def my_families(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    families = (
        db.query(Family)
        .join(FamilyMember, FamilyMember.family_id == Family.id)
        .filter(FamilyMember.user_id == user.id)
        .all()
    )
    return [_family_out(db, family) for family in families]


@router.patch("/families/members/{member_id}", response_model=MemberOut, summary="改关系/家属推送开关")
def patch_member(
    member_id: int,
    body: MemberPatch,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    member = db.get(FamilyMember, member_id)
    if member is None:
        raise HTTPException(404, "成员不存在")
    _require_family(db, user, member.family_id)
    if body.relation is not None:
        member.relation = body.relation
    if body.push_enabled is not None:
        member.push_enabled = body.push_enabled
    db.commit()
    owner = db.get(User, member.user_id)
    return _member_out(member, owner)


# ---------------- 服药计划 ----------------


@router.get("/plans", response_model=list[PlanOut], summary="增量拉取计划（含已删除墓碑）")
def list_plans(
    since: datetime | None = Query(None, description="只返回该时间之后更新的计划"),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    family_ids = [member.family_id for member in _memberships(db, user)]
    if not family_ids:
        return []
    query = db.query(Plan).filter(Plan.family_id.in_(family_ids))
    if since is not None:
        query = query.filter(Plan.updated_at > _naive(since))
    return query.order_by(Plan.updated_at).all()


@router.put("/plans/{plan_id}", response_model=PlanOut, summary="新增或更新计划（字段级 LWW）")
def upsert_plan(
    plan_id: str,
    body: PlanIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if not body.times:
        raise HTTPException(400, "至少需要一个提醒时间")
    member = db.get(FamilyMember, body.member_id)
    if member is None or member.family_id != body.family_id:
        raise HTTPException(400, "服药成员不属于该家庭组")
    _require_family(db, user, body.family_id)

    plan = db.get(Plan, plan_id)
    created = plan is None
    if created:
        plan = Plan(id=plan_id, family_id=body.family_id)
        db.add(plan)
    else:
        _require_family(db, user, plan.family_id)

    plan.family_id = body.family_id
    plan.member_id = body.member_id

    stamp = _stamp(body.updated_at)
    changed = PLAN_FIELDS if body.changed_fields is None else set(body.changed_fields)
    times = dict(plan.field_times or {})
    applied = 0

    if created:
        # 新计划没有历史值，全部字段都按客户端这次写入落库。
        for field in PLAN_FIELDS:
            times[field] = stamp
        plan.medicine_name = body.medicine_name
        plan.dose = body.dose
        plan.times = body.times
        plan.start_date = body.start_date
        plan.days = body.days
        plan.deleted = False
        applied = len(PLAN_FIELDS)
    else:
        for field in changed:
            attr = PLAN_FIELD_MAP.get(field)
            if attr is None:
                continue
            # 字段级 LWW：只有这次写入的时间不早于该字段上次写入时间才生效。
            if stamp < times.get(field, ""):
                continue
            setattr(plan, attr, getattr(body, attr))
            times[field] = stamp
            applied += 1

    plan.field_times = times
    if applied:
        plan.updated_at = now_local()
    db.commit()
    db.refresh(plan)
    return plan


@router.delete("/plans/{plan_id}", response_model=PlanOut, summary="软删除计划（墓碑同样走 LWW）")
def delete_plan(
    plan_id: str,
    updated_at: datetime | None = Query(None, description="客户端删除时间"),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.get(Plan, plan_id)
    if plan is None:
        raise HTTPException(404, "计划不存在")
    _require_family(db, user, plan.family_id)

    stamp = _stamp(updated_at)
    times = dict(plan.field_times or {})
    # 旧设备离线删除、晚到的删除请求不能盖掉更新的编辑。
    if stamp >= times.get("deleted", ""):
        times["deleted"] = stamp
        plan.field_times = times
        plan.deleted = True
        plan.updated_at = now_local()
        db.commit()
        db.refresh(plan)
    return plan


# ---------------- 打卡 ----------------


@router.post("/doses/check", response_model=DoseOut, summary="上报打卡（幂等）")
def check_dose(
    body: DoseCheckIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.get(Plan, body.plan_id)
    if plan is None:
        raise HTTPException(404, "计划不存在")
    _require_family(db, user, plan.family_id)

    log = (
        db.query(DoseLog)
        .filter(
            DoseLog.plan_id == body.plan_id,
            DoseLog.date == body.date,
            DoseLog.time == body.time,
        )
        .one_or_none()
    )
    if log is None:
        log = DoseLog(
            plan_id=body.plan_id,
            family_id=plan.family_id,
            member_id=plan.member_id,
            date=body.date,
            time=body.time,
        )
        db.add(log)
    log.status = "taken" if body.taken else "pending"
    log.updated_at = now_local()
    db.commit()
    db.refresh(log)
    return log


@router.get("/doses", response_model=list[DoseOut], summary="按日期查打卡记录")
def list_doses(
    day: str | None = Query(None, alias="date"),
    family_id: int | None = None,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    family_ids = [member.family_id for member in _memberships(db, user)]
    if family_id is not None:
        if family_id not in family_ids:
            raise HTTPException(403, "你不在该家庭组中")
        family_ids = [family_id]
    if not family_ids:
        return []
    query = db.query(DoseLog).filter(DoseLog.family_id.in_(family_ids))
    if day:
        query = query.filter(DoseLog.date == day)
    return query.order_by(DoseLog.date.desc(), DoseLog.time).all()


# ---------------- 提醒收件箱 ----------------

@router.get("/notifications/pending", response_model=list[NotificationOut], summary="拉取未读提醒")
def pending_notifications(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return (
        db.query(Notification)
        .filter(
            Notification.recipient_user_id == user.id,
            Notification.delivered_at.is_(None),
        )
        .order_by(Notification.created_at)
        .limit(50)
        .all()
    )


@router.post("/notifications/ack", summary="确认提醒已展示")
def ack_notifications(
    body: NotificationAckIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if not body.ids:
        return {"acked": 0}
    rows = (
        db.query(Notification)
        .filter(
            Notification.id.in_(body.ids),
            Notification.recipient_user_id == user.id,
            Notification.delivered_at.is_(None),
        )
        .all()
    )
    now = now_local()
    for row in rows:
        row.delivered_at = now
    db.commit()
    return {"acked": len(rows)}


# ---------------- 家庭动态 ----------------
# App 端的 SceneEvent 汇总到这里：打卡、撤销等动作追加成事件流，
# 家人设备按 since_id 增量拉取，用于显示「谁在什么时候做了什么」。


def _event_out(event: FamilyEvent) -> FamilyEventOut:
    return FamilyEventOut(
        id=event.id,
        type=event.type,
        actor_name=event.actor_name,
        plan_id=event.plan_id,
        member_name=event.member_name,
        medicine_name=event.medicine_name,
        time=event.slot_time,
        created_at=event.created_at,
    )


def _event_payload(event: FamilyEvent) -> dict:
    return _event_out(event).model_dump(mode="json")


# 进程内的 SSE 订阅者：有新事件时立刻唤醒所有在线的家庭动态流，
# 不用等下一次轮询；跨进程部署时可换成 Redis pub/sub。
_stream_subscribers: dict[asyncio.Queue, asyncio.AbstractEventLoop] = {}
SSE_HEARTBEAT_SECONDS = 15


def _publish_event() -> None:
    """通知所有 SSE 连接去数据库补拉新事件（可在任意线程调用）。"""
    for queue, loop in list(_stream_subscribers.items()):
        try:
            loop.call_soon_threadsafe(queue.put_nowait, True)
        except RuntimeError:
            # 连接所在的事件循环已经关闭（客户端刚断开），忽略即可
            pass


def _events_after(family_ids: list[int], since_id: int, limit: int = 50) -> list[dict]:
    if not family_ids:
        return []
    # 流式响应不能用请求级 Session（会被长时间占用），这里每次读库新开一个。
    with SessionLocal() as session:
        rows = (
            session.query(FamilyEvent)
            .filter(FamilyEvent.family_id.in_(family_ids), FamilyEvent.id > since_id)
            .order_by(FamilyEvent.id)
            .limit(limit)
            .all()
        )
        return [_event_payload(row) for row in rows]


@router.post("/events", response_model=FamilyEventOut, summary="上报一条家庭动态")
def create_event(
    body: FamilyEventIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_family(db, user, body.family_id)
    event = FamilyEvent(
        family_id=body.family_id,
        actor_user_id=user.id,
        actor_name=user.nickname,
        type=body.type,
        plan_id=body.plan_id,
        member_name=body.member_name,
        medicine_name=body.medicine_name,
        slot_time=body.time,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    # 家人设备正在用 SSE 长连接等着，立刻推过去，做到秒级可见。
    _publish_event()
    return _event_out(event)


@router.get("/events", response_model=list[FamilyEventOut], summary="增量拉取家庭动态")
def list_events(
    since_id: int = Query(0, ge=0, description="只返回 id 大于该值的事件"),
    limit: int = Query(30, ge=1, le=100),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    family_ids = [member.family_id for member in _memberships(db, user)]
    if not family_ids:
        return []
    rows = (
        db.query(FamilyEvent)
        .filter(FamilyEvent.family_id.in_(family_ids), FamilyEvent.id > since_id)
        .order_by(FamilyEvent.id.desc())
        .limit(limit)
        .all()
    )
    return [_event_out(row) for row in reversed(rows)]


@router.get("/events/stream", summary="家庭动态实时推送（SSE）")
async def stream_events(
    request: Request,
    since_id: int = Query(0, ge=0, description="只推送 id 大于该值的事件"),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    family_ids = [member.family_id for member in _memberships(db, user)]
    # 后面用独立 Session 轮询，请求级 Session 立刻归还连接池。
    db.close()

    queue: asyncio.Queue = asyncio.Queue()
    _stream_subscribers[queue] = asyncio.get_running_loop()

    async def source():
        cursor = since_id
        try:
            yield ": connected\n\n"
            while True:
                for event in _events_after(family_ids, cursor):
                    cursor = event["id"]
                    yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
                if await request.is_disconnected():
                    break
                try:
                    # 有新事件时立刻被唤醒；空闲时定期发心跳，顺便探测连接是否还活着。
                    await asyncio.wait_for(queue.get(), timeout=SSE_HEARTBEAT_SECONDS)
                except asyncio.TimeoutError:
                    yield ": ping\n\n"
        finally:
            _stream_subscribers.pop(queue, None)

    return StreamingResponse(
        source(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# ---------------- 设备推送 token ----------------


@router.post("/devices", summary="上报推送 token")
def register_device(
    body: DeviceIn,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    device = db.query(Device).filter(Device.push_token == body.push_token).one_or_none()
    if device is None:
        device = Device(user_id=user.id, platform=body.platform, push_token=body.push_token)
        db.add(device)
    else:
        device.user_id = user.id
        device.platform = body.platform
    device.updated_at = now_local()
    db.commit()
    return {"ok": True}


# ---------------- 药品知识库 ----------------
# 药品目录是公共只读数据，不需要登录：App 首次启动或版本升级时增量拉取，
# 「问药」「查重」都基于这里的通用名 / 别名映射。


def _norm(text: str) -> str:
    """统一小写并去掉所有空白，中文药名里常混着空格和全角字符。"""
    return "".join(text.lower().split())


@router.get("/drugs", response_model=list[DrugOut], summary="拉取药品目录（增量）")
def list_drugs(
    since: datetime | None = Query(None, description="只返回该时间之后更新的药品"),
    limit: int = Query(500, ge=1, le=2000),
    db: Session = Depends(get_db),
):
    query = db.query(Drug)
    if since is not None:
        query = query.filter(Drug.updated_at > _naive(since))
    return query.order_by(Drug.id).limit(limit).all()


@router.get("/drugs/search", response_model=list[DrugOut], summary="药品搜索 / 输入联想")
def search_drugs(
    q: str = Query("", max_length=64, description="药名、商品名、分类关键词"),
    limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
):
    keyword = _norm(q)
    if not keyword:
        return []
    ranked: list[tuple[int, str, Drug]] = []
    for drug in db.query(Drug).all():
        keys = [drug.generic_name, *(drug.aliases or []), drug.category, drug.class_group]
        best: int | None = None
        for key in keys:
            norm = _norm(key or "")
            if not norm or keyword not in norm:
                continue
            score = 0 if norm == keyword else 1 if norm.startswith(keyword) else 2
            best = score if best is None or score < best else best
        if best is not None:
            ranked.append((best, drug.generic_name, drug))
    # 完全同名的排最前，其次是前缀命中，最后按通用名稳定排序。
    ranked.sort(key=lambda item: (item[0], item[1]))
    return [drug for _, _, drug in ranked[:limit]]


@router.get("/drugs/{drug_id}", response_model=DrugOut, summary="药品详情")
def get_drug(drug_id: int, db: Session = Depends(get_db)):
    drug = db.get(Drug, drug_id)
    if drug is None:
        raise HTTPException(404, "药品不存在")
    return drug


@router.get("/interactions", response_model=list[InteractionRuleOut], summary="拉取相互作用规则")
def list_interaction_rules(db: Session = Depends(get_db)):
    return db.query(InteractionRule).order_by(InteractionRule.severity, InteractionRule.id).all()


# ---------------- 用药安全校验 ----------------


def _match_drug(text: str, catalog: list[tuple[Drug, list[str]]]) -> Drug | None:
    """在输入文本里找最长的别名命中，避免「泰诺林」被「泰诺」抢先匹配到别的药。"""
    key = _norm(text)
    best: Drug | None = None
    best_len = 0
    for drug, alias_keys in catalog:
        for alias_key in alias_keys:
            if alias_key and alias_key in key and len(alias_key) > best_len:
                best, best_len = drug, len(alias_key)
    return best


def _rule_matches(rule: InteractionRule, text_a: str, text_b: str) -> bool:
    group_a = [_norm(key) for key in (rule.group_a or []) if key.strip()]
    group_b = [_norm(key) for key in (rule.group_b or []) if key.strip()]
    a_in_first = any(key in text_a for key in group_a)
    b_in_first = any(key in text_b for key in group_a)
    if not group_b:
        # 「与任意其他口服药间隔」这类规则：服务端拿不到剂型，另一方有药名即视为口服。
        return a_in_first or b_in_first
    a_in_second = any(key in text_a for key in group_b)
    b_in_second = any(key in text_b for key in group_b)
    return (a_in_first and b_in_second) or (b_in_first and a_in_second)


@router.post("/safety/check", response_model=SafetyCheckOut, summary="用药安全校验（同成分重复 + 相互作用）")
def safety_check(body: SafetyCheckIn, db: Session = Depends(get_db)):
    names = [name.strip() for name in body.names if name.strip()]
    if not names:
        raise HTTPException(400, "请至少输入一种药名")

    drugs = db.query(Drug).all()
    catalog = [
        (drug, [_norm(alias) for alias in [drug.generic_name, *(drug.aliases or [])] if _norm(alias)])
        for drug in drugs
    ]
    drug_by_name = {drug.generic_name: drug for drug in drugs}

    items: list[SafetyMatchOut] = []
    # 送去和规则关键词比对的文本：原始药名 + 命中的通用名和别名，
    # 这样用户只写商品名也能命中「头孢 × 藿香正气水」这类按成分写的规则。
    texts: list[str] = []
    groups: dict[str, list[str]] = {}
    for name in names:
        drug = _match_drug(name, catalog)
        if drug is None:
            items.append(SafetyMatchOut(input=name, matched=False))
            texts.append(_norm(name))
            continue
        items.append(
            SafetyMatchOut(
                input=name,
                matched=True,
                generic_name=drug.generic_name,
                category=drug.category,
                class_group=drug.class_group,
                note=drug.note,
            )
        )
        texts.append(
            " ".join(
                [_norm(name), _norm(drug.generic_name), *(_norm(alias) for alias in drug.aliases or [])]
            )
        )
        bucket = groups.setdefault(drug.generic_name, [])
        if name not in bucket:
            bucket.append(name)

    duplicates = [
        SafetyDuplicateOut(
            generic_name=generic_name,
            names=member_names,
            note=drug_by_name[generic_name].note if generic_name in drug_by_name else "",
        )
        for generic_name, member_names in groups.items()
        if len(member_names) > 1
    ]

    seen: set[tuple[str, tuple[str, ...]]] = set()
    interactions: list[SafetyInteractionOut] = []
    for rule in db.query(InteractionRule).all():
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                if not _rule_matches(rule, texts[i], texts[j]):
                    continue
                key = (rule.title, tuple(sorted((names[i], names[j]))))
                if key in seen:
                    continue
                seen.add(key)
                interactions.append(
                    SafetyInteractionOut(
                        severity=rule.severity,
                        title=rule.title,
                        detail=rule.detail,
                        medicine_names=[names[i], names[j]],
                    )
                )
    # 高风险（high）排前面，和端侧交互提示保持一致
    interactions.sort(key=lambda item: 0 if item.severity == "high" else 1)
    return SafetyCheckOut(items=items, duplicates=duplicates, interactions=interactions)

