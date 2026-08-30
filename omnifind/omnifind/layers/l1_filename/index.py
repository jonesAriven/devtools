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
        # 开启 WAL 模式提高并发读性能;busy_timeout 防多进程/重建并发写冲突
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA synchronous=NORMAL")
        self.conn.execute("PRAGMA busy_timeout=5000")
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
            # 子树 LIKE 需转义,防路径含 %/_ 时连带误删
            sub = path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + os.sep + "%"
            self.conn.execute("DELETE FROM entries WHERE path=? OR path LIKE ? ESCAPE '\\'", (path, sub))
            self.conn.commit()

    @staticmethod
    def _escape_like(s: str) -> str:
        """转义 LIKE 特殊字符(配合 ESCAPE '\\'),防搜 a_b 误中 axb。"""
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    @staticmethod
    def _build_like(query: str) -> str:
        """构造 LIKE 模式: * 通配转 %, 字面 % _ 转义(防 a_b 误中 axb)。"""
        if "*" in query:
            return "%".join(FilenameIndex._escape_like(seg) for seg in query.split("*"))
        return f"%{FilenameIndex._escape_like(query)}%"

    def search(self, query: str, limit: int = 50, ext_filter: str | None = None) -> list[NameHit]:
        # 默认子串匹配(不区分大小写);支持 * 通配转 LIKE;字面 % _ 必须转义
        like = self._build_like(query)
        sql = "SELECT * FROM entries WHERE name LIKE ? ESCAPE '\\'"
        params: list = [like]
        if ext_filter:
            # 取最后一个点后的后缀做精确匹配(修正 archive.tar.gz 被当成 .tar.gz 的问题)
            sql += " AND LOWER(SUBSTR(name, LENGTH(name) - LENGTH(?) + 1)) = LOWER(?)"
            params.extend([ext_filter, ext_filter])
        sql += " ORDER BY is_dir DESC, mtime DESC LIMIT ?"
        params.append(limit)
        with self._lock:
            rows = self.conn.execute(sql, params).fetchall()
        return [NameHit(r["path"], r["name"], r["size"], r["mtime"], bool(r["is_dir"])) for r in rows]

    def count_match(self, query: str, ext_filter: str | None = None,
                    cap: int = 5000) -> tuple[int, bool]:
        """查询实际匹配数（用于结果层计数，区别于全量 count()）。

        返回 (count, capped):
          - count: 真实匹配数，但超过 cap 时返回 cap 并令 capped=True（表示真实数 >= cap）。
          - 用 SELECT COUNT(*) FROM (子查询 LIMIT cap+1) 实现早停，避免对超大结果集全表扫描。
        """
        like = self._build_like(query)
        sql = "SELECT 1 FROM entries WHERE name LIKE ? ESCAPE '\\'"
        params: list = [like]
        if ext_filter:
            sql += " AND LOWER(SUBSTR(name, LENGTH(name) - LENGTH(?) + 1)) = LOWER(?)"
            params.extend([ext_filter, ext_filter])
        # 早停计数：子查询 LIMIT cap+1，超过 cap 即截断，防止超大结果集全扫
        capped_sql = f"SELECT COUNT(*) FROM ({sql} LIMIT ?)"
        params.append(cap + 1)
        with self._lock:
            n = self.conn.execute(capped_sql, params).fetchone()[0]
        capped = n > cap
        return (cap if capped else n, capped)

    def count_match_grouped(self, query: str, exts: list[str],
                            cap: int = 5000) -> dict[str, int]:
        """一次扫描同时给出总命中数与各后缀分面计数(替代逐 ext 调 count_match 的 N 次全扫)。

        exts: 形如 ['', '.py', '.md'] 的后缀列表; 返回 {ext: count}, '' 为总数。
        总命中数超过 cap 时各计数同批截断到 cap(口径与 count_match 一致)。
        分面前缀匹配用 name LIKE '%.ext'(等价后缀匹配), ext 来自固定白名单无通配符。
        """
        like = self._build_like(query)
        suffixes = [e for e in exts if e]
        sum_parts = ", ".join(
            f"SUM(CASE WHEN name LIKE ? ESCAPE '\\' THEN 1 ELSE 0 END) AS s{i}"
            for i in range(len(suffixes))
        )
        sql = (
            "WITH m AS (SELECT name FROM entries WHERE name LIKE ? ESCAPE '\\' LIMIT ?) "
            f"SELECT COUNT(*) AS total{', ' + sum_parts if sum_parts else ''} FROM m"
        )
        params: list = [like, cap + 1] + [("%" + e.lower()) for e in suffixes]
        with self._lock:
            row = self.conn.execute(sql, params).fetchone()
        total = min(row["total"], cap)
        out = {"": total}
        for i, e in enumerate(suffixes):
            out[e] = min(row[f"s{i}"] or 0, cap)
        return out

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

    def clear(self) -> None:
        """清空全表(重建前调用)。走锁,禁止外部直接操作 conn。"""
        with self._lock:
            self.conn.execute("DELETE FROM entries")
            self.conn.commit()

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
            # 按根目录隔离异常:单个盘/根不可访问(权限不足、脱机、云盘异常等)
            # 不应连累其他盘,否则会丢失已索引的数据(尤其 rebuild 先 DELETE 再 build 的场景)。
            try:
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
            except OSError as e:
                print(f"[L1] 扫描根 {root} 失败(权限/不可访问),已跳过: {type(e).__name__}: {e}")
                continue
        if batch:
            self.index.bulk_upsert(batch)
            total += len(batch)
        return total

    def watch(self) -> None:
        # 增量:阶段二用 watchdog(跨平台)接入;此处占位
        raise NotImplementedError("watch 将在阶段二用 watchdog 接入")


def make_backend(cfg: OmniConfig, index: FilenameIndex) -> FilenameBackend:
    """按配置/平台选后端。

    auto 模式下 Windows 优先 USN，但 USN 需要 SYSTEM 权限(SeManageVolumePrivilege)，
    普通管理员也不可用。因此 auto 模式做轻量可用性探测(开卷+查 journal)，
    探测失败安全回退 walk，避免"实例化成功但 build 静默返回 0 条"的假成功。
    """
    from omnifind.core.config import is_windows
    backend = cfg.l1_backend
    if backend == "auto":
        backend = "usn" if is_windows() else "walk"
    if backend == "usn":
        try:
            from omnifind.layers.l1_filename.usn_backend import UsnBackend, probe_usn_available
            # auto 模式先探测权限，不可用则回退 walk；显式 usn 则原样返回(build 时报错)
            if cfg.l1_backend == "auto" and not probe_usn_available(cfg):
                print("[L1] USN 权限不足(需 SYSTEM)，auto 模式回退 walk 后端")
                return WalkBackend(cfg, index)
            return UsnBackend(cfg, index)
        except ImportError:
            # Windows 后端不可用时安全回退
            return WalkBackend(cfg, index)
    return WalkBackend(cfg, index)
