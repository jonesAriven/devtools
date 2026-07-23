"""
OmniFind 索引服务入口 —— 以 SYSTEM 权限持续运行的后台进程。

流程：
  1. 若 filename.db 为空或 --full 强制全量重建 → 跑一次 build（USN 全量）
  2. 进入 watch 循环，持续增量监听 USN journal

被计划任务/服务托管时通过接收 CTRL+BREAK/SIGTERM 优雅退出。

用法：
  python -m omnifind.service.indexer_service                 # 服务模式
  python -m omnifind.service.indexer_service --full          # 强制全量重建后再 watch
  python -m omnifind.service.indexer_service --once          # 只跑一次 build 就退出
"""
from __future__ import annotations
import argparse
import logging
import signal
import sys
from pathlib import Path

from omnifind.core.config import OmniConfig, ensure_dirs
from omnifind.layers.l1_filename.index import FilenameIndex, make_backend
from omnifind.core.indexer import build_filename_index


def _setup_logging(cfg: OmniConfig) -> None:
    cfg.logs_dir.mkdir(parents=True, exist_ok=True)
    log_file = cfg.logs_dir / "indexer-service.log"
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=[
            logging.FileHandler(log_file, encoding="utf-8"),
            logging.StreamHandler(sys.stdout),
        ],
    )


_stop = False


def _signal_handler(signum, frame):
    global _stop
    _stop = True
    logging.info("收到信号 %s，准备优雅退出…", signum)


def main(argv=None):
    ap = argparse.ArgumentParser(prog="omnifind-indexer")
    ap.add_argument("--full", action="store_true", help="启动前强制全量重建 L1 索引")
    ap.add_argument("--once", action="store_true", help="只跑一次 build 就退出，不进 watch")
    args = ap.parse_args(argv)

    cfg = OmniConfig.load()
    ensure_dirs(cfg)
    _setup_logging(cfg)

    logging.info("OmniFind Indexer Service 启动")
    logging.info("data_dir = %s", cfg.data_dir_path)
    logging.info("scan_roots = %s", cfg.scan_roots)
    logging.info("l1_backend = %s", cfg.l1_backend)

    signal.signal(signal.SIGINT, _signal_handler)
    signal.signal(signal.SIGTERM, _signal_handler)
    if sys.platform == "win32":
        signal.signal(signal.SIGBREAK, _signal_handler)  # type: ignore[attr-defined]

    l1 = FilenameIndex()
    try:
        need_full = args.full or (l1.count() == 0)
        if need_full:
            logging.info("开始 L1 全量索引 …")
            build_filename_index(cfg, l1)
        else:
            logging.info("L1 已有 %d 条记录，跳过全量，直接进 watch", l1.count())

        if args.once:
            logging.info("--once 模式，构建完成后退出")
            return

        backend = make_backend(cfg, l1)
        logging.info("进入 watch 循环（后端: %s）…", type(backend).__name__)
        # 简单包一层，让全局 _stop 生效：如果 watch 内部支持中断就交给它，
        # 否则依赖信号在 SIGTERM 时终止进程
        try:
            backend.watch()
        except KeyboardInterrupt:
            logging.info("watch 被中断，退出")
        except NotImplementedError:
            logging.warning("当前后端未实现 watch，改为定时全量重建（每 30 分钟）")
            import time
            while not _stop:
                time.sleep(1800)
                if _stop:
                    break
                logging.info("30 分钟定时全量重建 …")
                build_filename_index(cfg, l1)
    finally:
        l1.close()
        logging.info("Indexer Service 退出")


if __name__ == "__main__":
    main()
