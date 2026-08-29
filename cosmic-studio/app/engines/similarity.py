"""相似度引擎：分词 + Jaccard 预过滤 + difflib 精算，阈值矩阵来自 Hermes 规范。

阈值矩阵（similarity-thresholds.md）：
  功能过程/子过程/数据属性 三维度同阈值：
  同需求 ≤65% / 同批次 ≤75% / 跨批次(归档) ≤85%
E 类固定格式豁免：两个 E 类子过程之间天然 90%+ 相似，不判违规（也不许改 E 类写法来降相似度）。
"""
import difflib
import re

SAME_REQ_THRESHOLD = 0.65
CROSS_REQ_THRESHOLD = 0.85
JACCARD_PREFILTER = 0.35


def tokenize(text: str) -> set:
    if not text:
        return set()
    tokens = set()
    for p in re.split(r"[、，。；：/\s]+", text):
        p = p.strip()
        if not p:
            continue
        if len(p) >= 2:
            for i in range(len(p) - 1):
                tokens.add(p[i:i + 2])
        tokens.add(p)
    return tokens


def sim_ratio(a: str, b: str) -> float:
    return difflib.SequenceMatcher(None, a or "", b or "").ratio()


def jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def pairwise_same_project(items: list, threshold: float = SAME_REQ_THRESHOLD,
                          exempt_pair=None):
    """items: [{key, text, ...}]；exempt_pair(a,b)=True 时跳过该对（E类豁免/CRUD兄弟豁免）。
    返回 [(a, b, ratio)] 超阈值对。"""
    hits = []
    tokens = [tokenize(it["text"]) for it in items]
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            a, b = items[i], items[j]
            if a.get("exempt") and b.get("exempt"):
                continue  # E 类固定格式豁免
            if exempt_pair and exempt_pair(a, b):
                continue
            if tokens[i] and tokens[j]:
                overlap = len(tokens[i] & tokens[j]) / min(len(tokens[i]), len(tokens[j]))
                if overlap < 0.30:
                    continue
            r = sim_ratio(a["text"], b["text"])
            if r > threshold:
                hits.append((a, b, r))
    return hits


def cross_archive(current_items: list, archive_items: list,
                  threshold: float = CROSS_REQ_THRESHOLD):
    """当前↔归档 FP 名比对，Jaccard 预过滤降计算量。
    current_items/archive_items: [{key, text, label}] → [(cur, arch, ratio)]"""
    arch_tokens = [tokenize(it["text"]) for it in archive_items]
    hits = []
    for cur in current_items:
        cur_tokens = tokenize(cur["text"])
        for j, arch in enumerate(archive_items):
            if cur_tokens and arch_tokens[j]:
                overlap = len(cur_tokens & arch_tokens[j]) / min(len(cur_tokens), arch_tokens[j] and len(arch_tokens[j]) or 1)
                if overlap < JACCARD_PREFILTER:
                    continue
            r = sim_ratio(cur["text"], arch["text"])
            if r > threshold:
                hits.append((cur, arch, r))
    return hits
