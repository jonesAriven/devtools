"""
查询路由器 —— 统一三层入口,按查询语法分发。

语法约定:
  filename:xxx  或  name:xxx    -> 仅 L1 文件名层
  content:xxx   或普通关键词      -> L2 全文层(默认)
  ?自然语言问句                  -> L3 语义层(前缀 ? 触发)
  all:xxx                        -> 三层聚合

返回统一结构 SearchResponse,前端渲染无需关心来自哪层。
"""
from __future__ import annotations
from dataclasses import dataclass, asdict, field
from typing import Any


@dataclass
class UnifiedHit:
    path: str
    title: str
    snippet: str = ""
    layer: str = ""          # l1 / l2 / l3
    score: float = 0.0
    extra: dict = field(default_factory=dict)


@dataclass
class SearchResponse:
    query: str
    mode: str
    hits: list[UnifiedHit] = field(default_factory=list)
    counts: dict = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        return d


class QueryRouter:
    def __init__(self, l1=None, l2=None, l3=None):
        self.l1 = l1  # FilenameIndex
        self.l2 = l2  # FullTextIndex
        self.l3 = l3  # SemanticIndex(阶段四接入)

    def parse_mode(self, query: str) -> tuple[str, str]:
        q = query.strip()
        low = q.lower()
        if q.startswith("?"):
            return "semantic", q[1:].strip()
        for pfx in ("filename:", "name:"):
            if low.startswith(pfx):
                return "filename", q[len(pfx):].strip()
        if low.startswith("content:"):
            return "fulltext", q[len("content:"):].strip()
        if low.startswith("all:"):
            return "all", q[len("all:"):].strip()
        return "fulltext", q  # 默认全文

    def search(self, query: str, limit: int = 30) -> SearchResponse:
        mode, q = self.parse_mode(query)
        resp = SearchResponse(query=query, mode=mode)
        if not q:
            return resp

        if mode in ("filename", "all") and self.l1:
            for h in self.l1.search(q, limit):
                resp.hits.append(UnifiedHit(h.path, h.name, layer="l1",
                                            extra={"size": h.size, "is_dir": h.is_dir}))
            resp.counts["l1"] = self.l1.count()

        if mode in ("fulltext", "all") and self.l2:
            for h in self.l2.search(q, limit):
                resp.hits.append(UnifiedHit(h.path, h.title, snippet=h.snippet,
                                            layer="l2", score=h.score))
            resp.counts["l2"] = self.l2.count()

        if mode in ("semantic", "all") and self.l3:
            for h in self.l3.search(q, limit):
                resp.hits.append(UnifiedHit(h.path, h.title, snippet=h.snippet,
                                            layer="l3", score=h.score))
            resp.counts["l3"] = self.l3.count()

        return resp
