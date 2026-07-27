"""
查询路由器 —— 统一三层入口,按查询语法分发。

语法约定:
  filename:xxx  或  name:xxx    -> 仅 L1 文件名层
  content:xxx   或普通关键词      -> L2 全文层(默认)
  ?自然语言问句                  -> L3 语义层(前缀 ? 触发)
  all:xxx                        -> 三层聚合
  ext:.py                        -> 按扩展名筛选(可与其他前缀组合)
  sort:time                      -> 按时间排序(可选: time / size / score)
  sort:time_desc                 -> 时间倒序(默认)
  sort:size_asc                  -> 大小升序

返回统一结构 SearchResponse,前端渲染无需关心来自哪层。
"""
from __future__ import annotations
from dataclasses import dataclass, asdict, field
from typing import Any
import os


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

    def parse_query(self, query: str) -> dict[str, Any]:
        """解析查询语法，提取各种筛选条件和实际查询词。

        返回:
            {
                'mode': str,        # 搜索模式
                'q': str,           # 实际搜索词
                'ext_filter': str|None,  # 扩展名筛选，如 '.py'
                'sort': str,        # 排序方式: score / time_desc / time_asc / size_desc / size_asc
            }
        """
        q = query.strip()
        low = q.lower()
        result = {
            "mode": "auto",
            "q": q,
            "ext_filter": None,
            "sort": "score",
        }

        # 提取 ext: 筛选
        import re
        ext_match = re.search(r'\bext:(\S+)', q, re.IGNORECASE)
        if ext_match:
            ext_val = ext_match.group(1).lower()
            if not ext_val.startswith('.'):
                ext_val = '.' + ext_val
            result["ext_filter"] = ext_val
            q = (q[:ext_match.start()] + q[ext_match.end():]).strip()
            low = q.lower()

        # 提取 sort: 排序
        sort_match = re.search(r'\bsort:(\S+)', q, re.IGNORECASE)
        if sort_match:
            sort_val = sort_match.group(1).lower()
            sort_map = {
                "time": "time_desc",
                "time_desc": "time_desc",
                "time_asc": "time_asc",
                "date": "time_desc",
                "size": "size_desc",
                "size_desc": "size_desc",
                "size_asc": "size_asc",
                "score": "score",
                "relevance": "score",
                "相关度": "score",
                "时间": "time_desc",
                "大小": "size_desc",
            }
            result["sort"] = sort_map.get(sort_val, "score")
            q = (q[:sort_match.start()] + q[sort_match.end():]).strip()
            low = q.lower()

        # 解析模式
        if q.startswith("?"):
            result["mode"] = "semantic"
            result["q"] = q[1:].strip()
            return result

        for pfx in ("re:", "filename-re:", "name-re:"):
            if low.startswith(pfx):
                result["mode"] = "filename_regex"
                result["q"] = q[len(pfx):].strip()
                return result

        for pfx in ("filename:", "name:"):
            if low.startswith(pfx):
                result["mode"] = "filename"
                result["q"] = q[len(pfx):].strip()
                return result

        if low.startswith("content:"):
            result["mode"] = "fulltext"
            result["q"] = q[len("content:"):].strip()
            return result

        if low.startswith("all:"):
            result["mode"] = "all"
            result["q"] = q[len("all:"):].strip()
            return result

        result["q"] = q
        return result

    def parse_mode(self, query: str) -> tuple[str, str]:
        """旧接口兼容：返回 (mode, q)。"""
        parsed = self.parse_query(query)
        return parsed["mode"], parsed["q"]

    def _sort_hits(self, hits: list[UnifiedHit], sort: str) -> list[UnifiedHit]:
        """对搜索结果排序。"""
        if sort == "score":
            # 相关度排序: L2/L3 按 score(越小越好)，L1 按 mtime
            def score_key(h: UnifiedHit):
                if h.layer == "l1":
                    return (0, -(h.extra.get("mtime", 0) or 0))
                else:
                    return (1, h.score or 0)
            return sorted(hits, key=score_key)
        elif sort == "time_desc":
            return sorted(hits, key=lambda h: -(h.extra.get("mtime", 0) or 0))
        elif sort == "time_asc":
            return sorted(hits, key=lambda h: (h.extra.get("mtime", 0) or 0))
        elif sort == "size_desc":
            return sorted(hits, key=lambda h: -(h.extra.get("size", 0) or 0))
        elif sort == "size_asc":
            return sorted(hits, key=lambda h: (h.extra.get("size", 0) or 0))
        return hits

    def search(self, query: str, limit: int = 30) -> SearchResponse:
        parsed = self.parse_query(query)
        mode = parsed["mode"]
        q = parsed["q"]
        ext_filter = parsed["ext_filter"]
        sort = parsed["sort"]

        resp = SearchResponse(query=query, mode=mode)
        if not q:
            return resp

        # ---- auto 智能模式: 先 L2 全文, 无结果 fallback L1 文件名 ----
        if mode == "auto":
            # 先尝试 L2 全文搜索
            if self.l2:
                for h in self.l2.search(q, limit=limit, ext_filter=ext_filter):
                    resp.hits.append(UnifiedHit(
                        h.path, h.title, snippet=h.snippet,
                        layer="l2", score=h.score,
                        extra={"size": h.size, "mtime": h.mtime, "ext": h.ext}
                    ))
                resp.counts["l2"] = self.l2.count()
            # 如果 L2 没命中, 自动 fallback 到 L1 文件名搜索
            if not resp.hits and self.l1:
                for h in self.l1.search(q, limit=limit, ext_filter=ext_filter):
                    ext = os.path.splitext(h.name)[1].lower()
                    resp.hits.append(UnifiedHit(
                        h.path, h.name, layer="l1",
                        extra={"size": h.size, "is_dir": h.is_dir, "mtime": h.mtime, "ext": ext}
                    ))
                resp.counts["l1"] = self.l1.count()
                if resp.hits:
                    resp.mode = "auto_fallback_l1"
            if ext_filter:
                resp.counts["ext"] = ext_filter
            resp.hits = self._sort_hits(resp.hits, sort)
            return resp

        if mode == "filename_regex" and self.l1:
            try:
                for h in self.l1.search_regex(q, limit):
                    # ext_filter 在正则搜索中再过滤一次
                    if ext_filter:
                        file_ext = os.path.splitext(h.name)[1].lower()
                        if file_ext != ext_filter.lower():
                            continue
                    ext = os.path.splitext(h.name)[1].lower()
                    resp.hits.append(UnifiedHit(
                        h.path, h.name, layer="l1",
                        extra={"size": h.size, "is_dir": h.is_dir, "mtime": h.mtime, "ext": ext, "regex": True}
                    ))
            except ValueError as e:
                resp.counts["error"] = str(e)
            resp.counts["l1"] = self.l1.count()
            if ext_filter:
                resp.counts["ext"] = ext_filter
            resp.hits = self._sort_hits(resp.hits, sort)
            return resp

        if mode in ("filename", "all") and self.l1:
            for h in self.l1.search(q, limit, ext_filter=ext_filter):
                ext = os.path.splitext(h.name)[1].lower()
                resp.hits.append(UnifiedHit(
                    h.path, h.name, layer="l1",
                    extra={"size": h.size, "is_dir": h.is_dir, "mtime": h.mtime, "ext": ext}
                ))
            resp.counts["l1"] = self.l1.count()

        if mode in ("fulltext", "all") and self.l2:
            for h in self.l2.search(q, limit=limit, ext_filter=ext_filter):
                resp.hits.append(UnifiedHit(
                    h.path, h.title, snippet=h.snippet,
                    layer="l2", score=h.score,
                    extra={"size": h.size, "mtime": h.mtime, "ext": h.ext}
                ))
            resp.counts["l2"] = self.l2.count()

        if mode in ("semantic", "all") and self.l3:
            for h in self.l3.search(q, limit):
                resp.hits.append(UnifiedHit(
                    h.path, h.title, snippet=h.snippet,
                    layer="l3", score=h.score,
                    extra=getattr(h, 'extra', {})
                ))
            resp.counts["l3"] = self.l3.count()

        if ext_filter:
            resp.counts["ext"] = ext_filter

        resp.hits = self._sort_hits(resp.hits, sort)
        return resp
