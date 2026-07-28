"""
OmniFind 配置系统 —— 配置驱动,所有索引范围/类型/路径不硬编码。

配置优先级:%ProgramData%\\omnifind\\config.yaml (系统级覆盖) >
          <项目根>/config.local.yaml (开发/用户本地) >
          <项目根>/config.yaml (打包默认) >
          代码内置默认值。
跨平台:自动探测操作系统,给出平台相关的默认盘符/目录 + 数据目录。
"""
from __future__ import annotations
import os
import sys
import platform
from pathlib import Path
from dataclasses import dataclass, field
from typing import Any

import yaml

# 项目根目录(omnifind/core/config.py -> 上两级)——仅用于查 config.yaml 默认文件,不再作为运行时数据目录
ROOT = Path(__file__).resolve().parent.parent.parent


def is_windows() -> bool:
    return platform.system() == "Windows"


def default_data_dir() -> Path:
    """
    默认运行时数据目录。
    - 打包后(frozen):随 exe 走 <exe同级>/data,实现离线自包含,不写死 %ProgramData%。
    - 开发/系统安装:Windows 走 %ProgramData%\\omnifind\\
      （SYSTEM 服务和普通用户 Web 都能读写的共享位置）,
      Linux/Mac 走 ~/.omnifind/。用户可通过 config.local.yaml 的 data_dir 覆盖。
    """
    if getattr(sys, "frozen", False):
        return Path(sys.executable).parent / "data"
    if is_windows():
        base = os.environ.get("PROGRAMDATA", r"C:\ProgramData")
        return Path(base) / "omnifind"
    return Path.home() / ".omnifind"


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
    # ---- 运行时数据目录(索引 DB / 语义库 / 日志 / 模型的父目录) ----
    data_dir: str = field(default_factory=lambda: str(default_data_dir()))

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
    chunk_size: int = 512
    chunk_overlap: int = 64

    # ---- 模型(相对 data_dir,方便打包时一键指向 %ProgramData%\omnifind\models\) ----
    # 若显式设置绝对路径则用绝对路径,否则拼 data_dir/models/bge-small-zh-v1.5
    embed_model_path: str = ""

    # ---- 服务 ----
    host: str = "127.0.0.1"
    port: int = 8899

    # ---- 派生属性(不进 yaml) ----
    @property
    def data_dir_path(self) -> Path:
        return Path(self.data_dir)

    @property
    def db_dir(self) -> Path:
        return self.data_dir_path / "data"

    @property
    def logs_dir(self) -> Path:
        return self.data_dir_path / "logs"

    @property
    def models_dir(self) -> Path:
        # 打包后:模型随 exe 打进 _MEIPASS/models,离线即用(不依赖 %ProgramData%)
        if getattr(sys, "frozen", False):
            return Path(getattr(sys, "_MEIPASS", "")) / "models"
        return self.data_dir_path / "models"

    @property
    def semantic_dir(self) -> Path:
        return self.db_dir / "semantic"

    @property
    def resolved_embed_model_path(self) -> Path:
        if self.embed_model_path:
            return Path(self.embed_model_path)
        return self.models_dir / "bge-small-zh-v1.5"

    @classmethod
    def load(cls) -> "OmniConfig":
        cfg = cls()
        # 优先级从低到高逐层覆盖
        candidate_paths = [
            ROOT / "config.yaml",          # 打包默认
            ROOT / "config.local.yaml",    # 开发/用户本地
        ]
        # %ProgramData%\omnifind\config.yaml 作为系统级覆盖(最高优先级),
        # 让 SYSTEM 服务和普通用户共享同一份配置
        prog_data_cfg = default_data_dir() / "config.yaml"
        if prog_data_cfg.exists():
            candidate_paths.append(prog_data_cfg)
        for p in candidate_paths:
            if p.exists():
                # 配置文件损坏时降级为默认配置,不拖垮服务启动
                try:
                    data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
                except yaml.YAMLError as e:
                    print(f"[config] 配置文件 {p} 解析失败({e}),该文件按默认处理")
                    continue
                if not isinstance(data, dict):
                    print(f"[config] 配置文件 {p} 顶层不是字典,该文件按默认处理")
                    continue
                for k, v in data.items():
                    if not hasattr(cfg, k) or v is None:
                        continue
                    # 派生 property(db_dir/logs_dir 等)不可写,跳过防 AttributeError
                    if isinstance(getattr(type(cfg), k, None), property):
                        print(f"[config] 跳过派生只读字段: {k}")
                        continue
                    # 空列表视为"使用默认值",不覆盖
                    if isinstance(v, list) and len(v) == 0:
                        continue
                    # bool 不是合法 int(True 会被 isinstance(bool, int) 放过)
                    cur = getattr(cfg, k)
                    if isinstance(cur, int) and not isinstance(cur, bool) and isinstance(v, bool):
                        print(f"[config] 字段 {k} 需要 int,忽略 bool 值 {v}")
                        continue
                    setattr(cfg, k, v)
        return cfg


def ensure_dirs(cfg: OmniConfig | None = None) -> None:
    """确保运行时数据目录存在。默认使用 OmniConfig.load() 后的路径。"""
    if cfg is None:
        cfg = OmniConfig.load()
    for d in (cfg.data_dir_path, cfg.db_dir, cfg.logs_dir, cfg.models_dir, cfg.semantic_dir):
        d.mkdir(parents=True, exist_ok=True)


# ---- 向后兼容(旧代码里 from omnifind.core.config import DATA_DIR 直接读常量) ----
# 尽量少改老代码。DATA_DIR 现在指向"当前 config 下的 db_dir"，一次解析。
_default_cfg = OmniConfig.load()
DATA_DIR = _default_cfg.db_dir
MODELS_DIR = _default_cfg.models_dir
LOGS_DIR = _default_cfg.logs_dir
