"""
索引器 —— 驱动全流程:遍历文件 -> 建 L1 文件名索引 -> 抽取正文建 L2 全文索引。
配置驱动:扫描范围/类型/排除全来自 OmniConfig。
"""
from __future__ import annotations
import os
import time
from pathlib import Path

from omnifind.core.config import OmniConfig
from omnifind.layers.l1_filename.index import FilenameIndex, make_backend
from omnifind.layers.l2_fulltext.index import FullTextIndex
from omnifind.extractors import load_all_extractors, extract_text, get_extractor


def build_filename_index(cfg: OmniConfig, l1: FilenameIndex) -> int:
    """L1:全盘文件名索引(平台自动选后端)。"""
    backend = make_backend(cfg, l1)
    t0 = time.time()
    n = backend.build()
    print(f"[L1] 文件名索引完成:{n} 条,耗时 {time.time()-t0:.1f}s,后端 {type(backend).__name__}")
    return n


def build_fulltext_index(cfg: OmniConfig, l2: FullTextIndex, limit: int | None = None) -> int:
    """L2:遍历可抽取类型,抽正文入全文索引。limit 用于测试限量。"""
    load_all_extractors()
    exts = set(e.lower() for e in cfg.fulltext_exts)
    exclude = set(d.lower() for d in cfg.exclude_dirs)
    max_bytes = cfg.max_fulltext_mb * 1024 * 1024
    done = 0
    t0 = time.time()
    for root in cfg.scan_roots:
        for dirpath, dirnames, filenames in os.walk(root, topdown=True):
            dirnames[:] = [d for d in dirnames if d.lower() not in exclude]
            for name in filenames:
                ext = os.path.splitext(name)[1].lower()
                if ext not in exts or get_extractor(ext) is None:
                    continue
                p = os.path.join(dirpath, name)
                try:
                    st = os.stat(p)
                except OSError:
                    continue
                if st.st_size > max_bytes:
                    continue
                res = extract_text(Path(p))
                if not res.ok or not res.text.strip():
                    continue
                l2.upsert_document(p, res.title or name, res.text, st.st_size, st.st_mtime, ext)
                done += 1
                if limit and done >= limit:
                    print(f"[L2] 达到测试上限 {limit},停止")
                    print(f"[L2] 全文索引:{done} 篇,耗时 {time.time()-t0:.1f}s")
                    return done
    print(f"[L2] 全文索引完成:{done} 篇,耗时 {time.time()-t0:.1f}s")
    return done
