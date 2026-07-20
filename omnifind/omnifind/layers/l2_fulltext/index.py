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


@dataclass
class FtsHit:
    path: str
    title: str
    snippet: str
    score: float


class FullTextIndex:
    def __init__(self, db_path: Path | None = None):
        self.db_path = db_path or (DATA_DIR / "fulltext.db")
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(self.db_path))
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

    def search(self, query: str, limit: int = 30) -> list[FtsHit]:
        q = tokenize(query)
        # FTS5 MATCH,bm25 排序;snippet 取正文匹配片段
        rows = self.conn.execute(
            """
            SELECT f.path AS path, f.title AS title,
                   snippet(fts, 1, '[', ']', ' … ', 12) AS snippet,
                   bm25(fts) AS score
            FROM fts JOIN files f ON f.id = fts.rowid
            WHERE fts MATCH ?
            ORDER BY score
            LIMIT ?
            """,
            (q, limit),
        ).fetchall()
        return [FtsHit(r["path"], r["title"] or "", r["snippet"] or "", r["score"]) for r in rows]

    def count(self) -> int:
        return self.conn.execute("SELECT COUNT(*) FROM files WHERE indexed=1").fetchone()[0]

    def close(self) -> None:
        self.conn.close()
