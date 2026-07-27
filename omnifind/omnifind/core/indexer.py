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
    skipped_no_extractor = 0
    skipped_too_large = 0
    skipped_extract_fail = 0
    t0 = time.time()
    # 进度报告:每处理 500 个文件打印一次
    REPORT_INTERVAL = 500
    last_report = 0
    for root in cfg.scan_roots:
        print(f"[L2] 开始扫描: {root}")
        for dirpath, dirnames, filenames in os.walk(root, topdown=True):
            dirnames[:] = [d for d in dirnames if d.lower() not in exclude]
            for name in filenames:
                ext = os.path.splitext(name)[1].lower()
                if ext not in exts or get_extractor(ext) is None:
                    skipped_no_extractor += 1
                    continue
                p = os.path.join(dirpath, name)
                try:
                    st = os.stat(p)
                except OSError:
                    continue
                if st.st_size > max_bytes:
                    skipped_too_large += 1
                    continue
                res = extract_text(Path(p))
                if not res.ok or not res.text.strip():
                    skipped_extract_fail += 1
                    continue
                l2.upsert_document(p, res.title or name, res.text, st.st_size, st.st_mtime, ext)
                done += 1
                # 进度报告
                if done - last_report >= REPORT_INTERVAL:
                    elapsed = time.time() - t0
                    speed = done / elapsed if elapsed > 0 else 0
                    print(f"[L2] 已索引 {done} 篇 ({speed:.0f}篇/s) 跳过[无提取器:{skipped_no_extractor}, 过大:{skipped_too_large}, 提取失败:{skipped_extract_fail}]")
                    last_report = done
                if limit and done >= limit:
                    print(f"[L2] 达到测试上限 {limit},停止")
                    print(f"[L2] 全文索引:{done} 篇,耗时 {time.time()-t0:.1f}s")
                    return done
    elapsed = time.time() - t0
    speed = done / elapsed if elapsed > 0 else 0
    print(f"[L2] 全文索引完成:{done} 篇,耗时 {elapsed:.1f}s ({speed:.0f}篇/s)")
    print(f"[L2] 统计:跳过[无提取器:{skipped_no_extractor}, 文件过大(>{cfg.max_fulltext_mb}MB):{skipped_too_large}, 提取失败/空内容:{skipped_extract_fail}]")
    return done
