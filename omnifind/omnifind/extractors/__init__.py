"""
抽取器插件契约 —— 所有文档类型的正文抽取统一走这个接口。

新增一种文档格式 = 在 extractors/ 下加一个继承 BaseExtractor 的类,
声明支持的扩展名,实现 extract()。核心通过注册表自动发现,不改任何路由代码。
"""
from __future__ import annotations
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path
from typing import Type


@dataclass
class ExtractResult:
    """抽取结果:正文 + 元数据。"""
    text: str = ""
    title: str = ""
    metadata: dict = field(default_factory=dict)
    ok: bool = True
    error: str = ""


class BaseExtractor(ABC):
    """抽取器基类。子类必须声明 EXTENSIONS 并实现 extract()。"""

    # 该抽取器支持的扩展名(小写,含点),如 [".pdf"]
    EXTENSIONS: list[str] = []
    # 优先级:同一扩展名有多个抽取器时,数字大的优先
    PRIORITY: int = 0

    @abstractmethod
    def extract(self, path: Path) -> ExtractResult:
        """抽取正文。实现方需自行捕获异常并返回 ok=False 的结果。"""
        ...

    @classmethod
    def supports(cls, ext: str) -> bool:
        return ext.lower() in cls.EXTENSIONS


# ---- 全局注册表 ----
_REGISTRY: dict[str, BaseExtractor] = {}


def register(extractor_cls: Type[BaseExtractor]) -> Type[BaseExtractor]:
    """装饰器:注册一个抽取器。按扩展名建映射,PRIORITY 高者覆盖。"""
    inst = extractor_cls()
    for ext in extractor_cls.EXTENSIONS:
        ext = ext.lower()
        existing = _REGISTRY.get(ext)
        if existing is None or extractor_cls.PRIORITY >= type(existing).PRIORITY:
            _REGISTRY[ext] = inst
    return extractor_cls


def get_extractor(ext: str) -> BaseExtractor | None:
    return _REGISTRY.get(ext.lower())


def supported_extensions() -> list[str]:
    return sorted(_REGISTRY.keys())


def extract_text(path: Path) -> ExtractResult:
    """便捷入口:按文件扩展名找抽取器并抽取。"""
    ext = path.suffix.lower()
    ex = get_extractor(ext)
    if ex is None:
        return ExtractResult(ok=False, error=f"no extractor for {ext}")
    try:
        return ex.extract(path)
    except Exception as e:  # noqa: BLE001
        return ExtractResult(ok=False, error=f"{type(e).__name__}: {e}")


def load_all_extractors() -> None:
    """自动发现并导入 extractors/ 包下所有抽取器模块,触发 @register。"""
    import importlib
    import pkgutil
    from omnifind import extractors as pkg

    for mod in pkgutil.iter_modules(pkg.__path__):
        if not mod.name.startswith("_"):
            importlib.import_module(f"omnifind.extractors.{mod.name}")
