"""
L2 全文层 —— SQLite FTS5 + jieba 中文分词。

自包含、零外部服务、离线可用。存储:
  files 表:文件元数据(路径/大小/mtime/是否已抽取)
  fts   表:FTS5 虚表,存正文分词索引,external content 关联 files
"""
from __future__ import annotations
import re
import sqlite3
from pathlib import Path
from dataclasses import dataclass

import jieba

from omnifind.core.config import DATA_DIR

# 与前端 highlightText 约定的高亮标记(Unicode 私有区)
_HL0 = "\ue000"
_HL1 = "\ue001"


def tokenize(text: str) -> str:
    """jieba 分词后用空格连接,喂给 FTS5(用 unicode61 按空格切)。"""
    return " ".join(jieba.cut_for_search(text))


def _escape_fts5_token(token: str) -> str:
    """转义 FTS5 MATCH 特殊字符,用双引号包裹 token 避免语法错误。"""
    if not token:
        return '""'
    escaped = token.replace('"', '""')
    return f'"{escaped}"'


def build_fts5_query(query: str) -> str:
    """构建安全的 FTS5 MATCH 查询字符串。

    策略: jieba 分词 -> 每个 token 用双引号包裹 -> 空格连接(AND 语义)。
    这样可以避免特殊字符(.@-:()等)导致的 FTS5 语法错误。
    """
    tokens = [t for t in jieba.cut_for_search(query) if t.strip()]
    if not tokens:
        return '""'
    return " ".join(_escape_fts5_token(t) for t in tokens)


@dataclass
class FtsHit:
    path: str
    title: str
    snippet: str
    score: float
    size: int = 0
    mtime: float = 0.0
    ext: str = ""


class FullTextIndex:
    def __init__(self, db_path: Path | None = None):
        self.db_path = db_path or (DATA_DIR / "fulltext.db")
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
            CREATE TABLE IF NOT EXISTS files (
                id       INTEGER PRIMARY KEY,
                path     TEXT UNIQUE NOT NULL,
                title    TEXT,
                size     INTEGER,
                mtime    REAL,
                ext      TEXT,
                indexed  INTEGER DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_files_mtime ON files(mtime);

            CREATE VIRTUAL TABLE IF NOT EXISTS fts USING fts5(
                title, body,
                tokenize = 'unicode61 remove_diacritics 2'
            );
            """
        )
        self.conn.commit()

    def upsert_document(self, path: str, title: str, body: str,
                        size: int, mtime: float, ext: str) -> None:
        """写入/更新一篇文档的全文索引。body 已是原文,内部做分词。"""
        with self._lock:
            cur = self.conn.cursor()
            cur.execute(
                "INSERT INTO files(path,title,size,mtime,ext,indexed) VALUES(?,?,?,?,?,1) "
                "ON CONFLICT(path) DO UPDATE SET title=?,size=?,mtime=?,ext=?,indexed=1",
                (path, title, size, mtime, ext, title, size, mtime, ext),
            )
            file_id = cur.execute("SELECT id FROM files WHERE path=?", (path,)).fetchone()[0]
            # FTS5 external-less:直接按 rowid=file_id 存分词后的内容
            cur.execute("DELETE FROM fts WHERE rowid=?", (file_id,))
            cur.execute(
                "INSERT INTO fts(rowid,title,body) VALUES(?,?,?)",
                (file_id, tokenize(title), tokenize(body)),
            )
            self.conn.commit()

    def search(self, query: str, limit: int = 30, ext_filter: str | None = None) -> list[FtsHit]:
        q = build_fts5_query(query)
        try:
            # 排序用 FTS5 内置 `rank` 列(默认即 bm25), 触发按相关度流式输出 + LIMIT 早停,
            # 避免 ORDER BY bm25(fts) 退化成对全部命中行逐条算分再排序, 高频词卡死。
            # 不用 SQL snippet() —— 它对超大 body 列逐行扫描极慢(单条可达秒级)。
            # 改为读取原始 body 后在 Python 侧生成 snippet(见 _make_snippet), 速度提升数百倍。
            sql = """
                SELECT f.path AS path, f.title AS title, f.size AS size,
                       f.mtime AS mtime, f.ext AS ext,
                       body AS body_text,
                       rank AS score
                FROM fts JOIN files f ON f.id = fts.rowid
                WHERE fts MATCH ?
            """
            params: list = [q]
            if ext_filter:
                sql += " AND LOWER(f.ext) = LOWER(?)"
                params.append(ext_filter)
            sql += " ORDER BY rank LIMIT ?"
            params.append(limit)
            with self._lock:
                rows = self.conn.execute(sql, params).fetchall()
            return [FtsHit(
                r["path"], r["title"] or "", self._make_snippet(r["body_text"] or "", query),
                r["score"], r["size"] or 0, r["mtime"] or 0.0, r["ext"] or ""
            ) for r in rows]
        except sqlite3.OperationalError as e:
            import logging
            logger = logging.getLogger(__name__)
            logger.warning("FTS5 搜索失败 query=%s error=%s, 降级为 LIKE 模糊搜索", query, e)
            # LIKE 通配符 % _ 必须转义,防搜 a_b 误中 axb
            like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            sql = ("SELECT path, title, size, mtime, ext FROM files "
                   "WHERE (title LIKE ? ESCAPE '\\' OR path LIKE ? ESCAPE '\\')")
            params: list = [like, like]
            if ext_filter:
                sql += " AND LOWER(ext) = LOWER(?)"
                params.append(ext_filter)
            sql += " ORDER BY mtime DESC LIMIT ?"
            params.append(limit)
            with self._lock:
                rows = self.conn.execute(sql, params).fetchall()
            return [FtsHit(
                r["path"], r["title"] or "", "", 0.0,
                r["size"] or 0, r["mtime"] or 0.0, r["ext"] or ""
            ) for r in rows]


    def _make_snippet(self, body_text: str, query: str, window: int = 120) -> str:
        """在 Python 侧生成 snippet(替代 FTS5 慢速 snippet())。

        - 以 query 分词后首个命中位置为中心, 截取 window 字符的上下文窗口;
        - 用 U+E000/U+E001 私有区标记包裹命中词, 与旧 FTS5 snippet 一致, 前端 highlightText 可识别;
        - 仅对返回的 limit 条结果生效, 不做全量遍历(DoS 防护)。
        """
        if not body_text:
            return ""
        tokens = [t for t in jieba.cut_for_search(query) if t.strip()]
        low = body_text.lower()
        best = -1
        for t in tokens:
            i = low.find(t.lower())
            if i >= 0 and (best == -1 or i < best):
                best = i
        if best < 0:
            snippet = body_text[:window]
        else:
            start = max(0, best - window // 2)
            end = min(len(body_text), best + window // 2)
            snippet = body_text[start:end]
            if start > 0:
                snippet = " … " + snippet
            if end < len(body_text):
                snippet = snippet + " … "
        for t in tokens:
            if not t:
                continue
            try:
                snippet = re.sub(re.escape(t), lambda m: _HL0 + m.group(0) + _HL1,
                                 snippet, flags=re.IGNORECASE)
            except re.error:
                pass
        # FTS 存的是 jieba 分词文本(空格分隔),直接展示会出现 "包含 检索 功能" 式碎裂。
        # 只合并 CJK 字符(含高亮私有区标记)之间的空格,英文/代码 token 间空格保留。
        _HL = r'\ue000-\ue001'
        snippet = re.sub(
            rf'(?<=[\u4e00-\u9fff{_HL}])\s+(?=[\u4e00-\u9fff{_HL}])', '', snippet)
        return snippet

    def count_match(self, query: str, ext_filter: str | None = None,
                    cap: int = 5000) -> tuple[int, bool]:
        """查询实际匹配数（用于结果层计数，区别于全量 count()）。

        返回 (count, capped): 超过 cap 时返回 cap 并令 capped=True。
        用 SELECT COUNT(*) FROM (子查询 LIMIT cap+1) 实现早停。
        """
        q = build_fts5_query(query)
        try:
            inner = "SELECT 1 FROM fts JOIN files f ON f.id = fts.rowid WHERE fts MATCH ?"
            params: list = [q]
            if ext_filter:
                inner += " AND LOWER(f.ext) = LOWER(?)"
                params.append(ext_filter)
            capped_sql = f"SELECT COUNT(*) FROM ({inner} LIMIT ?)"
            params.append(cap + 1)
            with self._lock:
                n = self.conn.execute(capped_sql, params).fetchone()[0]
            capped = n > cap
            return (cap if capped else n, capped)
        except sqlite3.OperationalError:
            like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            inner = ("SELECT 1 FROM files WHERE (title LIKE ? ESCAPE '\\' OR path LIKE ? ESCAPE '\\')")
            params: list = [like, like]
            if ext_filter:
                inner += " AND LOWER(ext) = LOWER(?)"
                params.append(ext_filter)
            capped_sql = f"SELECT COUNT(*) FROM ({inner} LIMIT ?)"
            params.append(cap + 1)
            with self._lock:
                n = self.conn.execute(capped_sql, params).fetchone()[0]
            capped = n > cap
            return (cap if capped else n, capped)

    def count_match_grouped(self, query: str, exts: list[str],
                            cap: int = 5000) -> dict[str, int]:
        """一次 FTS 查询同时给出总命中数与各扩展名分面计数(替代逐 ext 的 N 次扫描)。

        exts: 形如 ['', '.py', '.md']; 返回 {ext: count}, '' 为总数。
        总命中数超过 cap 时同批截断(口径与 count_match 一致)。
        """
        q = build_fts5_query(query)
        suffixes = [e for e in exts if e]
        sum_parts = ", ".join(
            f"SUM(CASE WHEN LOWER(ext) = '{e}' THEN 1 ELSE 0 END) AS s{i}"
            for i, e in enumerate(suffixes)
        )
        sql = (
            "WITH m AS (SELECT f.ext AS ext FROM fts JOIN files f ON f.id = fts.rowid "
            f"WHERE fts MATCH ? LIMIT ?) "
            f"SELECT COUNT(*) AS total{', ' + sum_parts if sum_parts else ''} FROM m"
        )
        params: list = [q, cap + 1]
        with self._lock:
            row = self.conn.execute(sql, params).fetchone()
        total = min(row["total"], cap)
        out = {"": total}
        for i in range(len(suffixes)):
            out[suffixes[i]] = min(row[f"s{i}"] or 0, cap)
        return out

    def clear(self) -> None:
        """清空全部文档(重建前调用)。走锁,禁止外部直接操作 conn。"""
        with self._lock:
            self.conn.execute("DELETE FROM files")
            self.conn.execute("DELETE FROM fts")
            self.conn.commit()

    def count(self) -> int:
        with self._lock:
            return self.conn.execute("SELECT COUNT(*) FROM files WHERE indexed=1").fetchone()[0]

    def close(self) -> None:
        self.conn.close()
