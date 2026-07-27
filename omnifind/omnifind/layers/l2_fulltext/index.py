"""
L2 全文层 —— SQLite FTS5 + jieba 中文分词。

自包含、零外部服务、离线可用。存储:
  files 表:文件元数据(路径/大小/mtime/是否已抽取)
  fts   表:FTS5 虚表,存正文分词索引,external content 关联 files
"""
from __future__ import annotations
import sqlite3
from pathlib import Path
from dataclasses import dataclass

import jieba

from omnifind.core.config import DATA_DIR


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
        self.conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
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
            sql = """
                SELECT f.path AS path, f.title AS title, f.size AS size,
                       f.mtime AS mtime, f.ext AS ext,
                       snippet(fts, 1, '[', ']', ' … ', 12) AS snippet,
                       bm25(fts) AS score
                FROM fts JOIN files f ON f.id = fts.rowid
                WHERE fts MATCH ?
            """
            params: list = [q]
            if ext_filter:
                sql += " AND LOWER(f.ext) = LOWER(?)"
                params.append(ext_filter)
            sql += " ORDER BY score LIMIT ?"
            params.append(limit)
            rows = self.conn.execute(sql, params).fetchall()
            return [FtsHit(
                r["path"], r["title"] or "", r["snippet"] or "", r["score"],
                r["size"] or 0, r["mtime"] or 0.0, r["ext"] or ""
            ) for r in rows]
        except sqlite3.OperationalError as e:
            import logging
            logger = logging.getLogger(__name__)
            logger.warning("FTS5 搜索失败 query=%s error=%s, 降级为 LIKE 模糊搜索", query, e)
            like = f"%{query}%"
            sql = "SELECT path, title, size, mtime, ext FROM files WHERE (title LIKE ? OR path LIKE ?)"
            params: list = [like, like]
            if ext_filter:
                sql += " AND LOWER(ext) = LOWER(?)"
                params.append(ext_filter)
            sql += " ORDER BY mtime DESC LIMIT ?"
            params.append(limit)
            rows = self.conn.execute(sql, params).fetchall()
            return [FtsHit(
                r["path"], r["title"] or "", "", 0.0,
                r["size"] or 0, r["mtime"] or 0.0, r["ext"] or ""
            ) for r in rows]

    def count(self) -> int:
        return self.conn.execute("SELECT COUNT(*) FROM files WHERE indexed=1").fetchone()[0]

    def close(self) -> None:
        self.conn.close()
