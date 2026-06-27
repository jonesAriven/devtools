#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Trae AI RAG 检索脚本
复用龙虾 OpenClaw 体系的 RAG 服务（Qdrant + bge-small-zh-v1.5）
部署在 内网Debian Tailscale 100.105.196.63

用法:
  python rag_search.py "查询内容"
  python rag_search.py "查询内容" --top-k 3 --threshold 0.7
"""
import sys
import json
import argparse
import urllib.request
import urllib.error

EMBEDDING_URL = "http://100.105.196.63:8081"
QDRANT_URL = "http://100.105.196.63:6333"
COLLECTION = "memory"


def http_post(url, payload, timeout=10):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_get(url, timeout=10):
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def embed(text):
    result = http_post(f"{EMBEDDING_URL}/embed", {"text": text})
    return result.get("vector", [])


def search(vector, top_k=5, score_threshold=0.65):
    payload = {
        "vector": vector,
        "limit": top_k,
        "score_threshold": score_threshold,
        "with_payload": True,
        "with_vector": False,
    }
    result = http_post(
        f"{QDRANT_URL}/collections/{COLLECTION}/points/search",
        payload,
    )
    return result.get("result", [])


def format_results(query, hits):
    lines = []
    lines.append(f"🔍 RAG 检索: {query}")
    lines.append(f"   命中: {len(hits)} 条")
    lines.append("")
    for i, hit in enumerate(hits, 1):
        score = hit.get("score", 0)
        payload = hit.get("payload", {})
        content = payload.get("content", "")[:300]
        source = payload.get("source", "unknown")
        date = payload.get("date", "")
        tags = payload.get("tags", [])
        category = payload.get("category", "")
        path = payload.get("path", "")
        lines.append(f"[{i}] score={score:.4f} | {category} | {source} | {date}")
        if tags:
            lines.append(f"    tags: {', '.join(tags)}")
        if path:
            lines.append(f"    path: {path}")
        lines.append(f"    content: {content}")
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="RAG 检索脚本")
    parser.add_argument("query", help="查询内容")
    parser.add_argument("--top-k", type=int, default=5, help="返回条数 (默认 5)")
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.65,
        help="相似度阈值 (默认 0.65)",
    )
    parser.add_argument("--json", action="store_true", help="输出原始 JSON")
    args = parser.parse_args()

    try:
        vector = embed(args.query)
        if not vector:
            print("ERROR: Embedding 返回空向量", file=sys.stderr)
            sys.exit(1)

        hits = search(vector, args.top_k, args.threshold)

        if args.json:
            print(json.dumps({"query": args.query, "hits": hits}, ensure_ascii=False, indent=2))
        elif not hits:
            print(f"NO_RELEVANT_MEMORY (query={args.query!r}, threshold={args.threshold})")
        else:
            print(format_results(args.query, hits))

    except urllib.error.URLError as e:
        print(f"ERROR: 网络错误 - {e}", file=sys.stderr)
        sys.exit(2)
    except Exception as e:
        print(f"ERROR: {type(e).__name__} - {e}", file=sys.stderr)
        sys.exit(3)


if __name__ == "__main__":
    main()
