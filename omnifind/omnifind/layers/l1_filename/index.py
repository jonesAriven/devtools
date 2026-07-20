"""
L1 文件名层 —— 后端契约。

两个后端:
  WalkBackend : 跨平台,os.scandir 遍历建索引 + (Linux inotify / 轮询)增量。本机可验证。
  UsnBackend  : Windows 专用,读 NTFS MFT + USN 变更日志,冷启动秒级全量。需管理员,
                在 Windows 机(192.168.31.77)上开发验证,此处仅占位接口。

文件名索引存 SQLite(路径/名字/大小/mtime/是否目录),支持子串/通配/正则秒搜。
"""
from __future__ import annotations
import os
import sqlite3
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path

from omnifind.core.config import DATA_DIR, OmniConfig


@dataclass
class NameHit:
    path: str
    name: str
    size: int
    mtime: float
    is_dir: bool


class FilenameIndex:
    """文件名索引存储(后端无关)。"""

    def __init__(self, db_path: Path | None = None):
        self.db_path = db_path or (DATA_DIR / "filename.db")
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self._init_schema()

    def _init_schema(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS entries (
                path    TEXT PRIMARY KEY,
                name    TEXT NOT NULL,
                size    INTEGER,
                mtime   REAL,
                is_dir  INTEGER DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_entries_name ON entries(name COLLATE NOCASE);
            """
        )
        self.conn.commit()

    def bulk_upsert(self, rows: list[tuple]) -> None:
        self.conn.executemany(
            "INSERT INTO entries(path,name,size,mtime,is_dir) VALUES(?,?,?,?,?) "
            "ON CONFLICT(path) DO UPDATE SET name=excluded.name,size=excluded.size,"
            "mtime=excluded.mtime,is_dir=excluded.is_dir",
            rows,
        )
        self.conn.commit()

    def remove(self, path: str) -> None:
        self.conn.execute("DELETE FROM entries WHERE path=? OR path LIKE ?", (path, path + os.sep + "%"))
        self.conn.commit()

    def search(self, query: str, limit: int = 50) -> list[NameHit]:
        # 默认子串匹配(不区分大小写);支持 * 通配转 LIKE
        if "*" in query:
            like = query.replace("*", "%")
        else:
            like = f"%{query}%"
        rows = self.conn.execute(
            "SELECT * FROM entries WHERE name LIKE ? ESCAPE '\\' "
            "ORDER BY is_dir DESC, mtime DESC LIMIT ?",
            (like, limit),
        ).fetchall()
        return [NameHit(r["path"], r["name"], r["size"], r["mtime"], bool(r["is_dir"])) for r in rows]

    def count(self) -> int:
        return self.conn.execute("SELECT COUNT(*) FROM entries").fetchone()[0]

    def close(self) -> None:
        self.conn.close()


class FilenameBackend(ABC):
    """文件名索引后端契约。"""

    def __init__(self, cfg: OmniConfig, index: FilenameIndex):
        self.cfg = cfg
        self.index = index

    @abstractmethod
    def build(self) -> int:
        """全量建索引,返回索引到的条目数。"""
        ...

    @abstractmethod
    def watch(self) -> None:
        """启动增量监听(阻塞或后台线程)。"""
        ...


class WalkBackend(FilenameBackend):
    """跨平台遍历后端。本机可验证。"""

    def build(self) -> int:
        exclude = set(d.lower() for d in self.cfg.exclude_dirs)
        batch: list[tuple] = []
        total = 0
        for root in self.cfg.scan_roots:
            for dirpath, dirnames, filenames in os.walk(root, topdown=True):
                # 原地过滤排除目录(topdown 才生效)
                dirnames[:] = [d for d in dirnames if d.lower() not in exclude]
                # 目录自身也入索引
                for name in dirnames:
                    p = os.path.join(dirpath, name)
                    try:
                        st = os.stat(p, follow_symlinks=False)
                        batch.append((p, name, 0, st.st_mtime, 1))
                    except OSError:
                        continue
                for name in filenames:
                    p = os.path.join(dirpath, name)
                    try:
                        st = os.stat(p, follow_symlinks=False)
                        batch.append((p, name, st.st_size, st.st_mtime, 0))
                    except OSError:
                        continue
                    if len(batch) >= 5000:
                        self.index.bulk_upsert(batch)
                        total += len(batch)
                        batch.clear()
        if batch:
            self.index.bulk_upsert(batch)
            total += len(batch)
        return total

    def watch(self) -> None:
        # 增量:阶段二用 watchdog(跨平台)接入;此处占位
        raise NotImplementedError("watch 将在阶段二用 watchdog 接入")


def make_backend(cfg: OmniConfig, index: FilenameIndex) -> FilenameBackend:
    """按配置/平台选后端。Windows+usn -> UsnBackend(待 Windows 机实现),否则 WalkBackend。"""
    from omnifind.core.config import is_windows
    backend = cfg.l1_backend
    if backend == "auto":
        backend = "usn" if is_windows() else "walk"
    if backend == "usn":
        try:
            from omnifind.layers.l1_filename.usn_backend import UsnBackend
            return UsnBackend(cfg, index)
        except Exception:
            # Windows 后端不可用时安全回退
            return WalkBackend(cfg, index)
    return WalkBackend(cfg, index)
