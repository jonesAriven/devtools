"""
L3 语义层 —— 向量索引(LanceDB,嵌入式、离线、自包含)。

存 chunk 级向量:一篇文档切成多个 chunk,每个 chunk 一条向量记录。
检索:query 向量化 -> LanceDB 余弦近邻 -> 按文档聚合去重 -> 返回。
"""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from omnifind.core.config import DATA_DIR


@dataclass
class SemanticHit:
    path: str
    title: str
    snippet: str
    score: float


def chunk_text(text: str, size: int = 512, overlap: int = 64) -> list[str]:
    """按字符切块(中文按字符更稳),带重叠保上下文。"""
    text = text.strip()
    if not text:
        return []
    if len(text) <= size:
        return [text]
    # overlap >= size 时 step 退化为 1，10KB 文档会切出上万 chunk(内存/耗时爆炸)。
    # 视为配置错误，自动钳制到 size 的 1/4。
    if overlap >= size:
        overlap = size // 4
    chunks = []
    step = max(1, size - overlap)
    for i in range(0, len(text), step):
        chunk = text[i:i + size]
        if chunk.strip():
            chunks.append(chunk)
        if i + size >= len(text):
            break
    return chunks


class SemanticIndex:
    TABLE = "chunks"

    def __init__(self, embedder, db_path: Path | None = None, dim: int = 512):
        self.embedder = embedder
        self.db_path = str(db_path or (DATA_DIR / "lancedb"))
        self.dim = dim
        self._db = None
        self._table = None
        import threading
        self._lock = threading.Lock()

    def _connect(self):
        if self._db is not None:
            return
        import lancedb
        self._db = lancedb.connect(self.db_path)
        if self.TABLE in self._db.table_names():
            self._table = self._db.open_table(self.TABLE)

    def _ensure_table(self, sample_vec):
        if self._table is not None:
            return
        import pyarrow as pa
        schema = pa.schema([
            pa.field("path", pa.string()),
            pa.field("title", pa.string()),
            pa.field("chunk_id", pa.int32()),
            pa.field("text", pa.string()),
            pa.field("vector", pa.list_(pa.float32(), len(sample_vec))),
        ])
        self._table = self._db.create_table(self.TABLE, schema=schema, mode="overwrite")

    def add_document(self, path: str, title: str, text: str,
                     chunk_size: int = 512, overlap: int = 64) -> int:
        self._connect()
        chunks = chunk_text(text, chunk_size, overlap)
        if not chunks:
            return 0
        vecs = self.embedder.encode(chunks, is_query=False)
        self._ensure_table(vecs[0])
        rows = [
            {"path": path, "title": title, "chunk_id": i,
             "text": chunks[i], "vector": vecs[i].tolist()}
            for i in range(len(chunks))
        ]
        with self._lock:
            # rebuild/重复 add 时先删同 path 旧 chunk，避免重复膨胀与旧内容残留
            try:
                self._table.delete(f"path = '{path.replace(chr(39), chr(39) * 2)}'")
            except Exception:
                pass
            self._table.add(rows)
        return len(chunks)

    def search(self, query: str, limit: int = 30, min_score: float = 0.30) -> list[SemanticHit]:
        self._connect()
        if self._table is None:
            return []
        qv = self.embedder.encode_one(query, is_query=True)
        # 多取一些 chunk 再按文档聚合
        with self._lock:
            raw = (self._table.search(qv.tolist())
                   .metric("cosine").limit(limit * 3).to_list())
        best: dict[str, SemanticHit] = {}
        for r in raw:
            # lancedb 返回 _distance(cosine 距离),转相似度分
            score = 1.0 - float(r.get("_distance", 1.0))
            if score < min_score:
                continue
            path = r["path"]
            if path not in best or score > best[path].score:
                snip = r["text"][:160].replace("\n", " ")
                best[path] = SemanticHit(path, r.get("title", ""), snip, score)
        hits = sorted(best.values(), key=lambda h: h.score, reverse=True)
        return hits[:limit]

    def count(self) -> int:
        self._connect()
        if self._table is None:
            return 0
        return self._table.count_rows()

    def remove_document(self, path: str) -> None:
        """增量删除：移除某文件所有 chunk 向量（文件被删/不可读时调用）。"""
        self._connect()
        if self._table is None:
            return
        with self._lock:
            try:
                esc = path.replace("'", "''")
                self._table.delete(f"path = '{esc}'")
            except Exception:
                pass

    def drop(self) -> None:
        """清空整个语义索引表（重建前调用）。"""
        self._connect()
        if self._table is not None:
            self._db.drop_table(self.TABLE)
            self._table = None
