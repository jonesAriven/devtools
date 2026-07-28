"""
L3 语义增量监听 —— 零依赖轮询式，离线友好。

监听 semantic_dirs，按文件 mtime 增量维护向量库：
  - 新增/修改文件 -> 重新抽正文 + 向量化（覆盖旧 chunk）
  - 删除文件     -> 删除其向量
日常文件变化只需增量更新单文件，无需全量重建语义索引。

设计要点：
  - 首次扫描(prime)只记录 mtime、不重新向量化（构建阶段已完成），避免与首次构建重复劳动。
  - 之后每次扫描仅对 mtime 变化的文件做增量更新，弱机日常开销极小。
"""
from __future__ import annotations
import logging
import os
import threading
import time
from pathlib import Path

logger = logging.getLogger("omnifind.l3.watch")


class SemanticWatcher:
    def __init__(self, cfg, sem, interval: float = 30.0):
        self.cfg = cfg
        self.sem = sem
        self.interval = interval
        self._stop = False
        self._thread = None
        self._seen: dict[str, float] = {}   # path -> mtime（已同步状态）

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()
        logger.info("[L3 watch] 启动，监听 %s，间隔 %.0fs", self.cfg.semantic_dirs, self.interval)

    def stop(self) -> None:
        self._stop = True

    def _scan_once(self, prime: bool) -> None:
        from omnifind.extractors import load_all_extractors, extract_text, get_extractor
        from omnifind.layers.l3_semantic.builder import resolve_semantic_roots

        load_all_extractors()
        exts = {e.lower() for e in self.cfg.semantic_exts}
        exclude = {d.lower() for d in self.cfg.exclude_dirs}
        chunk_size = self.cfg.chunk_size
        chunk_overlap = self.cfg.chunk_overlap
        roots = resolve_semantic_roots(self.cfg)

        current: dict[str, float] = {}
        for root in roots:
            try:
                for dirpath, dirnames, filenames in os.walk(root, topdown=True):
                    dirnames[:] = [d for d in dirnames if d.lower() not in exclude]
                    for name in filenames:
                        ext = os.path.splitext(name)[1].lower()
                        if ext not in exts or get_extractor(ext) is None:
                            continue
                        p = os.path.join(dirpath, name)
                        try:
                            mtime = os.path.getmtime(p)
                        except OSError:
                            continue
                        current[p] = mtime
                        # 首次扫描仅记录状态，不重新向量化（构建阶段已做）
                        if prime:
                            continue
                        if self._seen.get(p) != mtime:
                            self._update_file(p, name, chunk_size, chunk_overlap)
            except OSError as e:
                logger.warning("[L3 watch] 扫描根 %s 失败(跳过): %s", root, e)

        # 删除已不存在文件的向量（仅非首次扫描执行）
        if not prime:
            for p in list(self._seen.keys()):
                if p not in current:
                    self.sem.remove_document(p)
                    del self._seen[p]
        # 刷新 seen 基线
        self._seen = dict(current)

    def _update_file(self, path: str, name: str, chunk_size: int, overlap: int) -> None:
        try:
            res = extract_text(Path(path))
        except Exception:
            res = None
        if res is None or not res.ok or not res.text.strip():
            # 文件不可读/无文本 -> 清掉其旧向量
            self.sem.remove_document(path)
        else:
            # add_document 内部会先删旧 chunk 再插入，天然幂等
            self.sem.add_document(path, res.title or name, res.text, chunk_size, overlap)
        try:
            self._seen[path] = os.path.getmtime(path)
        except OSError:
            self._seen.pop(path, None)

    def _loop(self) -> None:
        primed = False
        while not self._stop:
            try:
                if not primed:
                    self._scan_once(prime=True)
                    primed = True
                else:
                    self._scan_once(prime=False)
            except Exception:
                logger.exception("[L3 watch] 扫描异常")
            # 可中断的休眠（按秒拆分，stop() 能较快生效）
            for _ in range(max(1, int(self.interval))):
                if self._stop:
                    break
                time.sleep(1)
