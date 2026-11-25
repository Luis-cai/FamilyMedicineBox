import logging
from contextlib import asynccontextmanager

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI
from fastapi.responses import RedirectResponse

from . import api
from .db import Base, engine
from .scheduler import tick
from .seed_data import seed_knowledge

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

scheduler = BackgroundScheduler(timezone="Asia/Shanghai")


def ensure_schema() -> None:
    """create_all 不会给已有表加列，老库升级时手动补上字段级 LWW 需要的时间戳列。"""
    with engine.begin() as conn:
        columns = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(plans)")}
        if columns and "field_times" not in columns:
            conn.exec_driver_sql("ALTER TABLE plans ADD COLUMN field_times JSON DEFAULT '{}'")


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    ensure_schema()
    # 药品目录与相互作用规则随版本内置，启动时幂等写入。
    seed_knowledge()
    scheduler.add_job(tick, "interval", seconds=60, id="dose-reminder", replace_existing=True)
    scheduler.start()
    yield
    scheduler.shutdown(wait=False)


app = FastAPI(
    title="MediBox 家庭药箱后端",
    description="家庭组 + 到点提醒的最小后端：到点提醒本人，30 分钟未打卡再提醒家属。",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(api.router)


@app.get("/", include_in_schema=False)
def index():
    """根路径直接跳到接口文档，避免看到 404。"""
    return RedirectResponse("/docs")


@app.get("/health", summary="健康检查")
def health():
    return {"status": "ok"}
