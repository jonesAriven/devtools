"""
L3 语义索引构建器 —— 实现分叉点2的范围圈定(2.1 + 2.2)。

范围决策(避免全盘向量化爆库):
  2.1 若 cfg.semantic_dirs 非空 -> 只扫这些目录(用户指定重点目录)。
      否则回退到 cfg.scan_roots(但仍受 2.2 类型过滤)。
  2.2 只对 cfg.semantic_exts(可读文档类型)做向量化,自动跳过系统/二进制。
  安全阀:cfg.semantic_full_disk=False 且未指定 semantic_dirs 时,
          若扫描根是全盘,发出警告并要求用户显式圈定(防误爆)。
"""
from __future__ import annotations
import os
from pathlib import Path

from omnifind.core.config import OmniConfig
from omnifind.layers.l3_semantic.embedder import OnnxEmbedder
from omnifind.layers.l3_semantic.index import SemanticIndex
from omnifind.extractors import load_all_extractors, extract_text, get_extractor


def resolve_semantic_roots(cfg: OmniConfig) -> list[str]:
    """按 2.1 决定扫描根。"""
    if cfg.semantic_dirs:
        return cfg.semantic_dirs  # 2.1 用户指定优先
    return cfg.scan_roots          # 回退全局扫描根(仍受 2.2 类型过滤)


def build_semantic_index(cfg: OmniConfig, sem: SemanticIndex,
                         limit: int | None = None) -> int:
    """遍历圈定范围,抽正文 -> 分块 -> 嵌入 -> 入向量库。

    安全阀(自包含,不只依赖 web 层):未指定 semantic_dirs 且 semantic_full_disk=False
    时直接跳过,绝不偷偷向量化全盘(避免爆库)。
    """
    load_all_extractors()
    # 安全阀:只在明确圈定目录或显式要求全盘时才向量化
    if not cfg.semantic_dirs and not cfg.semantic_full_disk:
        print("[L3] 未配置 semantic_dirs 且 semantic_full_disk=False,跳过语义构建(避免全盘向量化)")
        return 0
    roots = resolve_semantic_roots(cfg)
    exts = set(e.lower() for e in cfg.semantic_exts)   # 2.2 类型过滤
    exclude = set(d.lower() for d in cfg.exclude_dirs)
    # 体积闸门:复用 max_fulltext_mb,超大全文档跳过(防向量化卡死/爆库)
    max_bytes = (cfg.max_fulltext_mb * 1024 * 1024) if cfg.max_fulltext_mb > 0 else 0

    total_chunks = 0
    docs = 0
    for root in roots:
        for dirpath, dirnames, filenames in os.walk(root, topdown=True):
            dirnames[:] = [d for d in dirnames if d.lower() not in exclude]
            for name in filenames:
                ext = os.path.splitext(name)[1].lower()
                if ext not in exts or get_extractor(ext) is None:
                    continue
                p = os.path.join(dirpath, name)
                # 体积闸门:超 max_fulltext_mb 的文件跳过
                if max_bytes:
                    try:
                        if os.path.getsize(p) > max_bytes:
                            continue
                    except OSError:
                        continue
                res = extract_text(Path(p))
                if not res.ok or not res.text.strip():
                    continue
                n = sem.add_document(p, res.title or name, res.text,
                                     cfg.chunk_size, cfg.chunk_overlap)
                total_chunks += n
                docs += 1
                if limit and docs >= limit:
                    print(f"[L3] 达到测试上限 {limit} 篇")
                    print(f"[L3] 语义索引:{docs} 篇 / {total_chunks} chunks")
                    return total_chunks
    print(f"[L3] 语义索引完成:{docs} 篇 / {total_chunks} chunks,范围 {roots}")
    return total_chunks


def make_embedder(cfg: OmniConfig) -> OnnxEmbedder:
    # 空 embed_model_path 时必须回退默认模型目录,否则 L3 永远初始化失败
    return OnnxEmbedder(cfg.resolved_embed_model_path, max_length=cfg.chunk_size)
