"""
OmniFind CLI 入口。

用法:
  python -m omnifind index          # 建 L1+L2 索引(按 config)
  python -m omnifind index --l2-limit 50   # 全文层限量,测试用
  python -m omnifind search "关键词"  # 命令行搜索
  python -m omnifind serve           # 启动 Web 服务
  python -m omnifind status          # 查看索引状态
"""
from __future__ import annotations
import argparse
import sys

from omnifind.core.config import OmniConfig, ensure_dirs
from omnifind.layers.l1_filename.index import FilenameIndex
from omnifind.layers.l2_fulltext.index import FullTextIndex
from omnifind.core.router import QueryRouter
from omnifind.core.indexer import build_filename_index, build_fulltext_index


def cmd_index(args):
    ensure_dirs()
    cfg = OmniConfig.load()
    l1 = FilenameIndex()
    l2 = FullTextIndex()
    only = [f for f in (args.l1_only, args.l2_only, args.l3_only) if f]
    do_l1 = args.l1_only or not only
    do_l2 = args.l2_only or not only
    do_l3 = args.l3_only or not only
    if do_l1:
        build_filename_index(cfg, l1)
    if do_l2:
        build_fulltext_index(cfg, l2, limit=args.l2_limit)
    if do_l3:
        try:
            from omnifind.layers.l3_semantic.builder import build_semantic_index, make_embedder
            from omnifind.layers.l3_semantic.index import SemanticIndex
            from pathlib import Path as _P
            model_path = _P(cfg.resolved_embed_model_path)
            if not model_path.joinpath("model.onnx").exists():
                print(f"[L3] 跳过: 模型文件不存在 ({model_path / 'model.onnx'})")
                print("[L3] 请先运行: python scripts/fetch_model.py")
            else:
                emb = make_embedder(cfg)
                sem = SemanticIndex(emb, dim=emb.dim)
                build_semantic_index(cfg, sem, limit=args.l3_limit)
        except ImportError as e:
            print(f"[L3] 跳过: 依赖缺失 ({e})")
            print("[L3] 安装依赖: pip install onnxruntime tokenizers lancedb numpy")
        except Exception as e:
            print(f"[L3] 跳过: 初始化失败 ({e})")
    l1.close(); l2.close()


def _make_router(cfg):
    l1 = FilenameIndex(); l2 = FullTextIndex()
    l3 = None
    try:
        from omnifind.layers.l3_semantic.builder import make_embedder
        from omnifind.layers.l3_semantic.index import SemanticIndex
        if cfg.resolved_embed_model_path.joinpath("model.onnx").exists():
            emb = make_embedder(cfg)
            l3 = SemanticIndex(emb, dim=emb.dim)
    except Exception as e:  # noqa: BLE001
        print(f"[L3] 语义层不可用(跳过):{e}")
    return QueryRouter(l1=l1, l2=l2, l3=l3), l1, l2


def cmd_search(args):
    cfg = OmniConfig.load()
    router, l1, l2 = _make_router(cfg)
    resp = router.search(args.query, limit=args.limit)
    print(f"模式:{resp.mode} · 命中 {len(resp.hits)}")
    for h in resp.hits:
        line = f"[{h.layer}] {h.title or h.path}"
        print(line)
        print(f"      {h.path}")
        if h.snippet:
            print(f"      {h.snippet}")
    l1.close(); l2.close()


def cmd_serve(args):
    from omnifind.web.server import main as serve_main
    serve_main()


def cmd_status(args):
    l1 = FilenameIndex(); l2 = FullTextIndex()
    print(f"文件名索引:{l1.count()} 条")
    print(f"全文索引  :{l2.count()} 篇")
    l1.close(); l2.close()


def main(argv=None):
    p = argparse.ArgumentParser(prog="omnifind", description="本地全能搜索(文件名+全文+语义)")
    sub = p.add_subparsers(dest="cmd", required=True)

    pi = sub.add_parser("index", help="建立索引")
    pi.add_argument("--l1-only", action="store_true")
    pi.add_argument("--l2-only", action="store_true")
    pi.add_argument("--l3-only", action="store_true")
    pi.add_argument("--l2-limit", type=int, default=None, help="全文层限量(测试)")
    pi.add_argument("--l3-limit", type=int, default=None, help="语义层限量(测试)")
    pi.set_defaults(func=cmd_index)

    ps = sub.add_parser("search", help="搜索")
    ps.add_argument("query")
    ps.add_argument("--limit", type=int, default=30)
    ps.set_defaults(func=cmd_search)

    pse = sub.add_parser("serve", help="启动 Web 服务")
    pse.set_defaults(func=cmd_serve)

    pst = sub.add_parser("status", help="索引状态")
    pst.set_defaults(func=cmd_status)

    args = p.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
