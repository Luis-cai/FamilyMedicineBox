import logging
from datetime import date, datetime, time, timedelta

from sqlalchemy.orm import Session

from .clock import now_local
from .db import SessionLocal
from .models import Device, DoseLog, FamilyMember, Notification, Plan
from .push import pusher

logger = logging.getLogger("medibox.scheduler")

FOLLOW_UP_MINUTES = 30
PRUNE_DAYS = 3


def plan_active_on(plan: Plan, day: date) -> bool:
    try:
        start = date.fromisoformat(plan.start_date)
    except ValueError:
        return False
    return plan.days > 0 and start <= day < start + timedelta(days=plan.days)


def _queue(
    db: Session,
    plan: Plan,
    day: date,
    slot: str,
    kind: str,
    user_id: int,
    title: str,
    body: str,
) -> int:
    """把提醒放进收件箱（供 App 轮询拉取），并尝试走推送通道。同一槽位只排一次。"""
    exists = (
        db.query(Notification)
        .filter(
            Notification.recipient_user_id == user_id,
            Notification.plan_id == plan.id,
            Notification.date == day.isoformat(),
            Notification.time == slot,
            Notification.kind == kind,
        )
        .first()
    )
    if exists is not None:
        return 0

    db.add(
        Notification(
            recipient_user_id=user_id,
            family_id=plan.family_id,
            plan_id=plan.id,
            date=day.isoformat(),
            time=slot,
            kind=kind,
            title=title,
            body=body,
        )
    )
    db.commit()

    for device in db.query(Device).filter(Device.user_id == user_id).all():
        pusher.send(device.push_token, title, body)
    return 1


def _notify_family(db: Session, plan: Plan, taker: FamilyMember, day: date, slot: str) -> int:
    relatives = (
        db.query(FamilyMember)
        .filter(
            FamilyMember.family_id == plan.family_id,
            FamilyMember.user_id != taker.user_id,
        )
        .all()
    )
    sent = 0
    for relative in relatives:
        if not relative.push_enabled:
            continue
        sent += _queue(
            db,
            plan,
            day,
            slot,
            "escalation",
            relative.user_id,
            f"家人还没打卡 · {slot}",
            f"{taker.relation}「{plan.medicine_name}」还没打卡，可以提醒一下。",
        )
    return sent


def check_and_notify(db: Session, now: datetime | None = None) -> int:
    """到点提醒本人；30 分钟仍未打卡时，再提醒本人和家属。"""
    now = now or now_local()
    today = now.date()
    sent = 0

    plans = db.query(Plan).filter(Plan.deleted.is_(False)).all()
    for plan in plans:
        if not plan_active_on(plan, today):
            continue
        taker = db.get(FamilyMember, plan.member_id)
        if taker is None:
            continue

        for slot in plan.times:
            try:
                hour, minute = (int(part) for part in slot.split(":"))
                due = datetime.combine(today, time(hour, minute))
            except ValueError:
                continue
            if now < due:
                continue

            log = (
                db.query(DoseLog)
                .filter(
                    DoseLog.plan_id == plan.id,
                    DoseLog.date == today.isoformat(),
                    DoseLog.time == slot,
                )
                .one_or_none()
            )
            if log is None:
                log = DoseLog(
                    plan_id=plan.id,
                    family_id=plan.family_id,
                    member_id=plan.member_id,
                    date=today.isoformat(),
                    time=slot,
                )
                db.add(log)
                db.commit()
            if log.status in ("taken", "skipped"):
                continue

            who = f"{taker.relation}：" if taker.relation else ""
            if log.notified_at is None:
                sent += _queue(
                    db,
                    plan,
                    today,
                    slot,
                    "due",
                    taker.user_id,
                    f"该吃药了 · {slot}",
                    f"{who}「{plan.medicine_name}」{plan.dose}，吃过后记得打卡。",
                )
                log.notified_at = now
            elif not log.follow_up_sent and now >= due + timedelta(minutes=FOLLOW_UP_MINUTES):
                sent += _queue(
                    db,
                    plan,
                    today,
                    slot,
                    "overdue",
                    taker.user_id,
                    f"还没打卡 · {slot}",
                    f"{who}「{plan.medicine_name}」已过 30 分钟还没打卡。",
                )
                sent += _notify_family(db, plan, taker, today, slot)
                log.follow_up_sent = True
            log.updated_at = now
            db.commit()
    return sent


def prune_notifications(db: Session, now: datetime | None = None) -> None:
    """清理 3 天前的提醒，避免长期不打开 App 时收件箱无限增长。"""
    cutoff = (now or now_local()) - timedelta(days=PRUNE_DAYS)
    db.query(Notification).filter(Notification.created_at < cutoff).delete()
    db.commit()


def tick() -> None:
    db = SessionLocal()
    try:
        count = check_and_notify(db)
        prune_notifications(db)
        if count:
            logger.info("本轮推送 %s 条", count)
    except Exception:
        logger.exception("提醒任务执行失败")
    finally:
        db.close()
