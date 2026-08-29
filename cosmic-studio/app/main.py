"""cosmic-studio：COSMIC 度量表生产系统（P0）。

两库模型：cosmic_active（编写，读写）/ cosmic_archive（归档，只读+导入导出）/
cosmic_studio（词库/规则/版本/导入任务元数据）。
"""
import logging

from fastapi import FastAPI

from .routers import dimension, studio

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(
    title="cosmic-studio",
    description="COSMIC 度量表生产系统：编写/归档两库 + 导入导出矩阵 + 推导引擎 + 质量门禁",
    version="0.1.0",
)

app.include_router(studio.r)
app.include_router(dimension.make_dimension_router("active", "cosmic_active", writable=True))
app.include_router(dimension.make_dimension_router("archive", "cosmic_archive", writable=False))
