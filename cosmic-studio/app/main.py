"""cosmic-studio：COSMIC 度量表生产系统。

两库模型：cosmic_active（编写，读写）/ cosmic_archive（归档，只读+导入导出）/
cosmic_studio（词库/规范/版本/用户/LLM配置）。
前端：Vue3+Element Plus 构建产物挂在同源 / 下（static/），/api 为后端。
"""
import logging
import os

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

from .routers import auth, chat, dimension, reviews, studio

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(
    title="cosmic-studio",
    description="COSMIC 度量表生产系统：编写/归档两库 + 导入导出矩阵 + 质量门禁 + 规范中心 + 对话式操作",
    version="0.2.0",
)

app.include_router(studio.pub)
app.include_router(auth.r)
app.include_router(studio.r)
app.include_router(chat.r)
app.include_router(reviews.r)
app.include_router(dimension.make_dimension_router("active", "cosmic_active", writable=True))
app.include_router(dimension.make_dimension_router("archive", "cosmic_archive", writable=False))

# 前端静态资源（Vue3 构建产物），API 路由优先
STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
if os.path.isdir(STATIC_DIR):
    app.mount("/assets", StaticFiles(directory=os.path.join(STATIC_DIR, "assets")), name="assets")

    @app.get("/{full_path:path}", include_in_schema=False)
    def spa(full_path: str):
        target = os.path.join(STATIC_DIR, full_path)
        if full_path and os.path.isfile(target):
            return FileResponse(target)
        return FileResponse(os.path.join(STATIC_DIR, "index.html"))
