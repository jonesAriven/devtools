"""cosmic-studio：COSMIC 度量表生产系统。

两库模型：cosmic_active（编写，读写）/ cosmic_archive（归档，只读+导入导出）/
cosmic_studio（词库/规范/版本/用户/LLM配置）。
前端：Vue3+Element Plus 构建产物挂在同源 / 下（static/），/api 为后端。
"""
import logging
import os

from fastapi import FastAPI, Request
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse, JSONResponse

from .routers import auth, chat, dimension, reviews, studio
from . import config, db

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")

# 启动期配置校验（缺失变量报警，不阻断）
config.validate_config()

app = FastAPI(
    title="cosmic-studio",
    description="COSMIC 度量表生产系统：编写/归档两库 + 导入导出矩阵 + 质量门禁 + 规范中心 + 对话式操作",
    version="0.2.0",
)


@app.exception_handler(Exception)
async def unhandled_exception(request: Request, exc: Exception):
    """兜底：未捕获异常统一回结构化 {detail}，前端才有内容可显示（fix P0-3）。

    HTTPException / RequestValidationError 由 Starlette 专用 handler 处理，不会进入此处。
    """
    logging.error("unhandled exception on %s %s: %s", request.method, request.url.path, exc,
                  exc_info=True)
    return JSONResponse(status_code=500, content={"detail": str(exc) or "Internal Server Error"})

def _ensure_schema():
    """启动期轻量迁移（幂等）。部署流水线只做 git reset + compose up，不跑 init_db，
    新增列在这里自动补齐，避免每次发版手工 ALTER。"""
    col = db.query(config.DB_STUDIO,
                   "SELECT COUNT(*) AS n FROM information_schema.columns "
                   "WHERE table_schema=%s AND table_name='users' AND column_name='menu_perms'",
                   (config.DB_STUDIO,), one=True)["n"]
    if not col:
        db.execute(config.DB_STUDIO,
                   "ALTER TABLE users ADD COLUMN menu_perms JSON NULL AFTER role")
        logging.info("schema migrated: users.menu_perms added")


_ensure_schema()

app.include_router(studio.pub)
app.include_router(auth.r)
app.include_router(studio.r)
app.include_router(chat.r)
app.include_router(reviews.r)
app.include_router(dimension.make_dimension_router("active", "cosmic_active", writable=True))
app.include_router(dimension.make_dimension_router("archive", "cosmic_archive", writable=False))

# 前端静态资源（Vue3 构建产物），API 路由优先
STATIC_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "static"))
STATIC_ABSPATH = STATIC_DIR + os.sep  # 用于 realpath 前缀校验
if os.path.isdir(STATIC_DIR):
    app.mount("/assets", StaticFiles(directory=os.path.join(STATIC_DIR, "assets")), name="assets")

    @app.get("/{full_path:path}", include_in_schema=False)
    def spa(full_path: str):
        # 拼错的 /api/xxx 走 404 JSON，不返回 index.html 误导前端
        if full_path.startswith("api/"):
            from fastapi import HTTPException
            raise HTTPException(status_code=404, detail="Not Found")
        target = os.path.abspath(os.path.join(STATIC_DIR, full_path))
        # 防路径穿越：只服务 STATIC_DIR 内的真实文件，否则回退 index.html
        if (target == STATIC_DIR or target.startswith(STATIC_ABSPATH)) and os.path.isfile(target):
            return FileResponse(target)
        return FileResponse(os.path.join(STATIC_DIR, "index.html"))
