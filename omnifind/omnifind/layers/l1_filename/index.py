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
        import threading
        self._lock = threading.Lock()
        self.conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        # 开启 WAL 模式提高并发读性能
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA synchronous=NORMAL")
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
        with self._lock:
            self.conn.executemany(
                "INSERT INTO entries(path,name,size,mtime,is_dir) VALUES(?,?,?,?,?) "
                "ON CONFLICT(path) DO UPDATE SET name=excluded.name,size=excluded.size,"
                "mtime=excluded.mtime,is_dir=excluded.is_dir",
                rows,
            )
            self.conn.commit()

    def remove(self, path: str) -> None:
        with self._lock:
            self.conn.execute("DELETE FROM entries WHERE path=? OR path LIKE ?", (path, path + os.sep + "%"))
            self.conn.commit()

    def search(self, query: str, limit: int = 50, ext_filter: str | None = None) -> list[NameHit]:
        # 默认子串匹配(不区分大小写);支持 * 通配转 LIKE
        if "*" in query:
            like = query.replace("*", "%")
        else:
            like = f"%{query}%"
        sql = "SELECT * FROM entries WHERE name LIKE ? ESCAPE '\\'"
        params: list = [like]
        if ext_filter:
            sql += " AND LOWER(SUBSTR(name, INSTR(name, '.'))) = LOWER(?)"
            params.append(ext_filter)
        sql += " ORDER BY is_dir DESC, mtime DESC LIMIT ?"
        params.append(limit)
        with self._lock:
            rows = self.conn.execute(sql, params).fetchall()
        return [NameHit(r["path"], r["name"], r["size"], r["mtime"], bool(r["is_dir"])) for r in rows]

    @staticmethod
    def _literal_prefix(pattern: str) -> str:
        """从正则里提取开头的连续字面字符,用于 LIKE 粗筛缩小候选集。
        遇到第一个正则元字符就停。无字面前缀返回空串(退化为全扫)。"""
        meta = set(".^$*+?{}[]()|\\")
        buf = []
        for ch in pattern:
            if ch in meta:
                break
            buf.append(ch)
        return "".join(buf)

    def search_regex(self, pattern: str, limit: int = 50,
                     ignore_case: bool = True, scan_cap: int = 200000) -> list[NameHit]:
        """文件名正则搜索:LIKE 粗筛候选集 + re 精筛。

        - 有字面前缀:用 name LIKE 'prefix%' 缩小候选,再 re.search 精筛(快)。
        - 无字面前缀:流式扫描,最多检查 scan_cap 条,防全表灾难。
        """
        import re
        flags = re.IGNORECASE if ignore_case else 0
        try:
            rx = re.compile(pattern, flags)
        except re.error as e:
            raise ValueError(f"非法正则: {e}") from e

        prefix = self._literal_prefix(pattern)
        # 顶层含 | 交替时,字面前缀粗筛会漏掉其他分支 -> 强制流式扫描保正确
        has_alt = "|" in pattern
        hits: list[NameHit] = []
        
        with self._lock:
            if not has_alt and pattern.startswith("^") and self._literal_prefix(pattern[1:]):
                pfx = self._literal_prefix(pattern[1:])
                cur = self.conn.execute(
                    "SELECT path,name,size,mtime,is_dir FROM entries "
                    "WHERE name LIKE ? ESCAPE '\\' ORDER BY is_dir DESC, mtime DESC",
                    (pfx.replace("%", "\\%").replace("_", "\\_") + "%",),
                )
            elif prefix and not has_alt:
                cur = self.conn.execute(
                    "SELECT path,name,size,mtime,is_dir FROM entries "
                    "WHERE name LIKE ? ESCAPE '\\' ORDER BY is_dir DESC, mtime DESC",
                    ("%" + prefix.replace("%", "\\%").replace("_", "\\_") + "%",),
                )
            else:
                # 无字面可用,流式全扫(限 scan_cap 条)
                cur = self.conn.execute(
                    "SELECT path,name,size,mtime,is_dir FROM entries "
                    "ORDER BY is_dir DESC, mtime DESC LIMIT ?", (scan_cap,)
                )

            for r in cur:
                if rx.search(r["name"]):
                    hits.append(NameHit(r["path"], r["name"], r["size"], r["mtime"], bool(r["is_dir"])))
                    if len(hits) >= limit:
                        break
        return hits

    def count(self) -> int:
        with self._lock:
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
