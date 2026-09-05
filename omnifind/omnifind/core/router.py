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
import logging
import os

# 计数/遍历上限: 防止高频词或超长结果集导致服务端卡死(DoS 防护)。
# count_match 用 SELECT COUNT(*) FROM (子查询 LIMIT COUNT_CAP+1) 早停；
# 真实匹配数超过该值时返回 COUNT_CAP 并置对应 *_capped 标记。
COUNT_CAP = 5000

# 前端文件类型行的固定扩展名列表(与 index.html 的 extFilters 一一对应)。
# facet 计数按与 counts["total"] 完全相同的口径(各激活层 count_match 之和),
# 保证"按钮上的数字 == 点击该按钮后看到的 共 N 条"。
FACET_EXTS = [".py", ".md", ".txt", ".js", ".json", ".html", ".css", ".java"]


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
    # 每个固定扩展名在当前模式+查询下的命中总数(口径同 counts["total"]),
    # 供前端文件类型按钮展示数量; 正则模式等场景可能为 None(前端隐藏数字)。
    ext_facets: dict | None = None

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

    def _sort_hits(self, hits: list[UnifiedHit], sort: str, query: str = "") -> list[UnifiedHit]:
        """对搜索结果排序。"""
        if sort == "score":
            q = query.lower()
            
            def compute_score(h: UnifiedHit) -> float:
                """计算综合相关度分，越大越相关。"""
                if h.layer == "l1":
                    title = (h.title or "").lower()
                    # 完全匹配
                    if title == q:
                        return 100.0
                    # 前缀匹配
                    if title.startswith(q):
                        return 90.0
                    # 后缀匹配（扩展名前）
                    base = title.rsplit('.', 1)[0] if '.' in title else title
                    if base.endswith(q):
                        return 80.0
                    # 包含匹配，计算匹配位置权重
                    idx = title.find(q)
                    if idx >= 0:
                        # 越靠前权重越高
                        pos_weight = max(0, 1.0 - idx / max(len(title), 1))
                        return 60.0 + pos_weight * 20.0
                    # 兜底
                    return 40.0
                elif h.layer == "l2":
                    # bm25 是越小越相关（负数），取反后越大越相关
                    # 典型范围 -10 ~ 0，取反后 0 ~ 10
                    bm25 = h.score or 0
                    # 归一化到 0-50 分区间
                    normalized = max(0.0, min(50.0, -bm25 * 5.0))
                    return normalized
                elif h.layer == "l3":
                    # 语义相似度 0-1，乘以 70
                    return (h.score or 0) * 70.0
                return 0.0
            
            # 按综合分降序，同分按修改时间降序
            return sorted(hits, key=lambda h: (-compute_score(h), -(h.extra.get("mtime", 0) or 0)))
        elif sort == "time_desc":
            return sorted(hits, key=lambda h: -(h.extra.get("mtime", 0) or 0))
        elif sort == "time_asc":
            return sorted(hits, key=lambda h: (h.extra.get("mtime", 0) or 0))
        elif sort == "size_desc":
            return sorted(hits, key=lambda h: -(h.extra.get("size", 0) or 0))
        elif sort == "size_asc":
            return sorted(hits, key=lambda h: (h.extra.get("size", 0) or 0))
        return hits

    def search(self, query: str, limit: int = 30,
               mode: str | None = None,
               ext: str | None = None,
               sort: str | None = None) -> SearchResponse:
        """执行搜索。

        参数:
            query: 搜索关键词
            limit: 结果数量限制
            mode: 搜索模式（可选），优先使用传入的 mode；为 None 时从 query 中解析
            ext: 扩展名筛选（可选），优先使用传入的 ext；为 None 时从 query 中解析
            sort: 排序方式（可选），优先使用传入的 sort；为 None 时从 query 中解析
        """
        parsed = self.parse_query(query)
        parsed_mode = parsed["mode"]

        # 前缀优先(修复 P1: q 中的 filename:/content:/all:/re:/? 前缀覆盖 mode 参数,
        # 仅当 query 无显式前缀时才回退到传入的 mode)。
        if parsed_mode != "auto":
            final_mode = parsed_mode
        elif mode and mode != "auto":
            final_mode = mode
        else:
            final_mode = "auto"
        final_q = parsed["q"]
        # ext 和 sort：外部传了就用外部的，否则用解析的
        final_ext = ext if ext else parsed["ext_filter"]
        final_sort = sort if sort else parsed["sort"]

        resp = SearchResponse(query=query, mode=final_mode)
        if not final_q:
            return resp

        active = self._active_layers(final_mode)
        counts: dict = {}

        # ====================== 命中采集(仅当前模式激活的层) ======================
        # L1 文件名(含正则模式)
        if "l1" in active and self.l1:
            if final_mode == "filename_regex":
                try:
                    rx_hits = self.l1.search_regex(final_q, limit=COUNT_CAP)
                except ValueError as e:
                    # 修复 P1: 非法正则时 l1 计数应为 0, 不泄漏全量索引条数
                    counts["error"] = str(e)
                    counts["l1"] = 0
                else:
                    counts["l1"] = len(rx_hits)
                    if len(rx_hits) >= COUNT_CAP:
                        counts["l1_capped"] = True
                    for h in rx_hits[:limit]:
                        # ext_filter 在正则结果上再过滤一次
                        if final_ext:
                            file_ext = os.path.splitext(h.name)[1].lower()
                            if file_ext != final_ext.lower():
                                continue
                        ext_val = os.path.splitext(h.name)[1].lower()
                        resp.hits.append(UnifiedHit(
                            h.path, h.name, layer="l1",
                            extra={"size": h.size, "is_dir": h.is_dir, "mtime": h.mtime,
                                   "ext": ext_val, "regex": True}
                        ))
            else:
                for h in self.l1.search(final_q, limit=limit, ext_filter=final_ext):
                    ext_val = os.path.splitext(h.name)[1].lower()
                    resp.hits.append(UnifiedHit(
                        h.path, h.name, layer="l1",
                        extra={"size": h.size, "is_dir": h.is_dir, "mtime": h.mtime, "ext": ext_val}
                    ))

        # L2 全文
        if "l2" in active and self.l2:
            for h in self.l2.search(final_q, limit=limit, ext_filter=final_ext):
                resp.hits.append(UnifiedHit(
                    h.path, h.title, snippet=h.snippet,
                    layer="l2", score=h.score,
                    extra={"size": h.size, "mtime": h.mtime, "ext": h.ext}
                ))

        # L3 语义(单次搜索,结果同时供命中采集与计数,避免同查询向量化+检索两遍)
        # l3_raw 保留全量供分面对账; hits/counts 按需过 ext
        l3_raw: list = []
        l3_kept = 0
        if "l3" in active and self.l3:
            try:
                l3_raw = self.l3.search(final_q, limit=COUNT_CAP + 1)
                l3_hits = l3_raw
                if final_ext:
                    l3_hits = [h for h in l3_raw
                               if os.path.splitext(h.path)[1].lower() == final_ext.lower()]
                l3_kept = len(l3_hits)
                for h in l3_hits[:limit]:
                    resp.hits.append(UnifiedHit(
                        h.path, h.title, snippet=h.snippet,
                        layer="l3", score=h.score,
                        extra=getattr(h, 'extra', {})
                    ))
            except Exception as e:
                # 不再静默吞掉语义层异常:记录日志 + 置错误标记,让调用方/前端感知降级
                logging.getLogger(__name__).exception("L3 语义检索失败,已降级跳过")
                counts["l3_error"] = str(e)[:200]

        # ====================== counts: 始终计算所有可用层的真实计数 ======================
        # 修复 P0: 切换单层(mode=filename)时仍返回其它层的真实数, 供前端对比。
        # L1/L2 用 count_match_grouped 一次扫描同时产出总数与 9 个分面计数
        # (替代逐 ext 调 count_match 的 2x10 次全表扫描);正则模式计数已在上方设置。
        facet_counts: dict[str, int] = {}
        if final_mode != "filename_regex":
            g: dict[str, int] = {}
            for ext_key in [""] + FACET_EXTS:
                g[ext_key] = 0
            # 分面口径 = 当前模式激活层计数之和(与 total/"全部按钮=点击后条数"一致):
            # L1/L2 走 grouped 扫描; L3 用本次检索结果按后缀分桶(零额外向量检索)
            l1g = (self.l1.count_match_grouped(final_q, [""] + FACET_EXTS, cap=COUNT_CAP)
                   if ("l1" in active and self.l1) else {})
            l2g = (self.l2.count_match_grouped(final_q, [""] + FACET_EXTS, cap=COUNT_CAP)
                   if ("l2" in active and self.l2) else {})
            for src in (l1g, l2g):
                for k, v in src.items():
                    g[k] = g.get(k, 0) + v
            if "l3" in active and self.l3:
                for h in l3_raw:
                    e = os.path.splitext(h.path)[1].lower()
                    if e in g:
                        g[e] += 1
                    g[""] += 1
            facet_counts = g
            # counts["l1"/"l2"] = 该层在 final_ext 筛选下的命中数(始终全算供单层对比):
            #   无筛选 -> 全量; 筛选值在固定白名单内 -> 取该层分面; 自定义后缀 -> 单独精确计数
            for layer in ("l1", "l2"):
                idx = self.l1 if layer == "l1" else self.l2
                if not idx:
                    counts[layer] = 0
                elif not final_ext:
                    counts[layer] = (l1g if layer == "l1" else l2g).get("", 0)
                elif final_ext.lower() in g:
                    counts[layer] = (l1g if layer == "l1" else l2g).get(final_ext.lower(), 0)
                else:
                    n, capped = idx.count_match(final_q, final_ext, cap=COUNT_CAP)
                    counts[layer] = n
                    if capped:
                        counts[f"{layer}_capped"] = True
        else:
            counts["l1"] = 0 if "l1" not in counts else counts["l1"]
            if self.l2:
                n, capped = self.l2.count_match(final_q, final_ext, cap=COUNT_CAP)
                counts["l2"] = n
                if capped:
                    counts["l2_capped"] = True
            else:
                counts["l2"] = 0

        if self.l3:
            if "l3" in active:
                # 复用上方单次搜索结果(已过 ext),不再二次向量化+检索
                counts["l3"] = min(l3_kept, COUNT_CAP)
                if l3_kept > COUNT_CAP:
                    counts["l3_capped"] = True
            else:
                # 单层(filename/fulltext)模式下语义计数置 0:
                # 语义计数需向量化+向量检索,代价是 L1/L2 的百倍,不为展示计数付这笔钱
                counts["l3"] = 0
        else:
            counts["l3"] = 0

        if final_ext:
            counts["ext"] = final_ext

        # 语义层关闭信号(供前端明确提示, 而非空结果)
        if final_mode in ("semantic", "all") and self.l3 is None:
            counts["semantic_disabled"] = True

        # total: 当前模式真实命中总数 = 各激活层计数之和
        counts["total"] = sum(counts.get(layer, 0) for layer in active)

        # ext facet: 每个固定扩展名在"当前模式+当前查询"下的命中总数,
        # 口径与 counts["total"] 完全一致(已由 count_match_grouped 同批产出)。
        # 正则模式无法按 ext 高效计数, 跳过(前端检测缺失时不展示数字)。
        if final_mode != "filename_regex":
            resp.ext_facets = facet_counts

        resp.counts = counts
        resp.hits = self._sort_hits(resp.hits, final_sort, query=final_q)
        # 仅 auto 模式在 router 层截断(与既有行为一致; all/单层由 server 层兜底切片)
        if final_mode == "auto" and len(resp.hits) > limit:
            resp.hits = resp.hits[:limit]
        return resp

    def _active_layers(self, mode: str) -> set[str]:
        """返回当前模式下参与命中采集的层集合。"""
        has_l3 = self.l3 is not None
        if mode == "auto":
            return {"l1", "l2", "l3"} if has_l3 else {"l1", "l2"}
        if mode in ("filename", "filename_regex"):
            return {"l1"}
        if mode == "fulltext":
            return {"l2"}
        if mode == "semantic":
            return {"l3"}
        if mode == "all":
            return {"l1", "l2", "l3"} if has_l3 else {"l1", "l2"}
        return set()
