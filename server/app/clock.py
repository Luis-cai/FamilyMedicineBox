"""统一使用北京时间（不带时区），避免服务器时区影响提醒判断。"""

from datetime import datetime
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Asia/Shanghai")


def now_local() -> datetime:
    return datetime.now(TZ).replace(tzinfo=None)
