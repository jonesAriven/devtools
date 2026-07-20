"""
OmniFind 配置系统 —— 配置驱动,所有索引范围/类型/路径不硬编码。

配置优先级:config.local.yaml(用户本地,gitignore) > config.yaml(默认) > 内置默认值。
跨平台:自动探测操作系统,给出平台相关的默认盘符/目录。
"""
from __future__ import annotations
import os
import sys
import platform
from pathlib import Path
from dataclasses import dataclass, field
from typing import Any

import yaml

# 项目根目录(omnifind/core/config.py -> 上两级)
ROOT = Path(__file__).resolve().parent.parent.parent
DATA_DIR = ROOT / "data"
MODELS_DIR = ROOT / "models"
LOGS_DIR = ROOT / "logs"


def is_windows() -> bool:
    return platform.system() == "Windows"


def default_scan_roots() -> list[str]:
    """默认全盘扫描根:Windows 枚举所有盘符,Linux/Mac 从根挂载点。"""
    if is_windows():
        import string
        import ctypes
        drives = []
        bitmask = ctypes.windll.kernel32.GetLogicalDrives()
        for i, letter in enumerate(string.ascii_uppercase):
            if bitmask & (1 << i):
                drives.append(f"{letter}:\\")
        return drives
    # Linux/Mac:默认用户家目录起步(全盘 / 需 root 且噪音大,交给用户配置)
    return [str(Path.home())]


# 语义层(L3)自动圈定的"可读文档"扩展名(分叉点2:2.2 按类型自动圈定)
DEFAULT_SEMANTIC_EXTS = [
    ".txt", ".md", ".pdf", ".docx", ".doc", ".rtf",
    ".pptx", ".ppt", ".xlsx", ".xls", ".csv",
    ".html", ".htm", ".epub",
]

# 全文层(L2)可抽取正文的扩展名(比 L3 更宽,含代码/配置)
DEFAULT_FULLTEXT_EXTS = DEFAULT_SEMANTIC_EXTS + [
    ".py", ".js", ".ts", ".java", ".c", ".cpp", ".h", ".go", ".rs",
    ".json", ".yaml", ".yml", ".xml", ".ini", ".toml", ".log", ".sql",
    ".sh", ".bat", ".ps1",
]

# 全盘扫描时跳过的目录名(系统/噪音,文件名层也跳过以省空间)
DEFAULT_EXCLUDE_DIRS = [
    "$RECYCLE.BIN", "System Volume Information", "Windows", "Program Files",
    "Program Files (x86)", "ProgramData", "AppData",
    "node_modules", ".git", ".venv", "venv", "__pycache__",
    ".cache", "proc", "sys", "dev", "run",
]


@dataclass
class OmniConfig:
    # ---- L1 文件名层 ----
    scan_roots: list[str] = field(default_factory=default_scan_roots)
    exclude_dirs: list[str] = field(default_factory=lambda: list(DEFAULT_EXCLUDE_DIRS))
    # L1 后端:'usn'(Windows 真USN秒搜,需管理员) 或 'walk'(跨平台遍历+监听)
    l1_backend: str = "auto"  # auto=Windows选usn,其他选walk

    # ---- L2 全文层 ----
    fulltext_exts: list[str] = field(default_factory=lambda: list(DEFAULT_FULLTEXT_EXTS))
    max_fulltext_mb: int = 50  # 超过此大小的文件不抽取正文

    # ---- L3 语义层(分叉点2:2.1+2.2)----
    # 2.1 用户指定的重点语义目录(只对这些做向量化);为空则回退 2.2
    semantic_dirs: list[str] = field(default_factory=list)
    # 2.2 按类型自动圈定(在 semantic_dirs 内、或全盘 fallback 时生效)
    semantic_exts: list[str] = field(default_factory=lambda: list(DEFAULT_SEMANTIC_EXTS))
    semantic_full_disk: bool = False  # 是否对全盘可读文档做语义(默认否,避免爆库)
    embed_model_path: str = str(MODELS_DIR / "bge-small-zh-v1.5")
    chunk_size: int = 512
    chunk_overlap: int = 64

    # ---- 服务 ----
    host: str = "127.0.0.1"
    port: int = 8899

    @classmethod
    def load(cls) -> "OmniConfig":
        cfg = cls()
        for name in ("config.yaml", "config.local.yaml"):
            p = ROOT / name
            if p.exists():
                data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
                for k, v in data.items():
                    if hasattr(cfg, k) and v is not None:
                        setattr(cfg, k, v)
        return cfg


def ensure_dirs() -> None:
    for d in (DATA_DIR, MODELS_DIR, LOGS_DIR):
        d.mkdir(parents=True, exist_ok=True)
