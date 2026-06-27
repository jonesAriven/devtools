#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAG 数据灌入脚本
部署在 内网Debian (Tailscale 100.105.196.63)
从 openclaw-work-space git 仓库读取 .md 文件，分块后 embed + upsert 到 Qdrant

用法:
  python3 ingest.py --all              # 全量灌入所有 .md 文件
  python3 ingest.py --file path.md     # 灌入单个文件
  python3 ingest.py --changed          # 只灌入 git pull 后变更的文件
  python3 ingest.py --deleted          # 删除已删除文件对应的向量
  python3 ingest.py --stats            # 统计当前 Qdrant 数据
  python3 ingest.py --search "关键词"  # 测试检索

依赖: 纯 Python 标准库（urllib/json/os/hashlib/re）
部署: /home/root01/rag-tools/ingest.py
仓库: /home/root01/openclaw-work-space
"""
import os
import sys
import json
import zlib
import argparse
import urllib.request
import urllib.error
import re
from pathlib import Path
from datetime import datetime

# ========== 配置 ==========
EMBEDDING_URL = "http://localhost:8081"
QDRANT_URL = "http://localhost:6333"
COLLECTION = "memory"
REPO_PATH = "/home/root01/openclaw-work-space"
SUPPORTED_EXTS = {".md", ".markdown"}

# 分块参数
CHUNK_SIZE = 600       # 每块字符数（约 400 token）
CHUNK_OVERLAP = 100    # 块重叠字符数（约 80 token）
MIN_CHUNK_SIZE = 50    # 最小块大小（小于此不分块）


# ========== HTTP 工具 ==========
def http_request(url, payload=None, method="POST", timeout=30):
    """通用 HTTP 请求，默认 POST"""
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json"},
        method=method,
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode("utf-8")
        return json.loads(body) if body else {}


def http_post(url, payload, timeout=30):
    return http_request(url, payload, method="POST", timeout=timeout)


def http_put(url, payload, timeout=30):
    return http_request(url, payload, method="PUT", timeout=timeout)


def http_get(url, timeout=10):
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_delete(url, timeout=10):
    req = urllib.request.Request(url, method="DELETE")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


# ========== Embedding ==========
def embed_text(text):
    """调用 Embedding 服务，返回 512 维向量"""
    result = http_post(f"{EMBEDDING_URL}/embed", {"text": text})
    vector = result.get("vector", [])
    if not vector:
        raise ValueError(f"Embedding 返回空向量: {result}")
    return vector


def embed_batch(texts):
    """批量 embed（逐条调用，Embedding 服务不支持批量）"""
    return [embed_text(t) for t in texts]


# ========== 分块 ==========
def split_into_chunks(text, chunk_size=CHUNK_SIZE, overlap=CHUNK_OVERLAP):
    """按段落优先切分，块大小 ~600 字符，重叠 ~100 字符"""
    if len(text) <= chunk_size:
        return [text.strip()] if text.strip() else []

    # 先按段落分（双换行）
    paragraphs = re.split(r'\n\s*\n', text)
    paragraphs = [p.strip() for p in paragraphs if p.strip()]

    chunks = []
    current = ""
    for para in paragraphs:
        # 如果段落本身超长，硬切分
        if len(para) > chunk_size:
            if current:
                chunks.append(current)
                current = ""
            for i in range(0, len(para), chunk_size - overlap):
                chunk = para[i:i + chunk_size]
                if chunk:
                    chunks.append(chunk)
        # 累积到块大小
        elif len(current) + len(para) + 2 <= chunk_size:
            current = (current + "\n\n" + para) if current else para
        else:
            if current:
                chunks.append(current)
            current = para
    if current:
        chunks.append(current)

    # 过滤过小的块（合并到前一个）
    filtered = []
    for chunk in chunks:
        if len(chunk) < MIN_CHUNK_SIZE and filtered:
            filtered[-1] = filtered[-1] + "\n\n" + chunk
        else:
            filtered.append(chunk)

    return filtered if filtered else ([text[:chunk_size]] if text.strip() else [])


# ========== point_id 生成 ==========
def make_point_id(relative_path, chunk_index):
    """crc32(相对路径) * 1000 + 块索引，确保确定性"""
    path_hash = zlib.crc32(relative_path.encode("utf-8")) & 0xFFFFFFFF
    return path_hash * 1000 + chunk_index


# ========== 文件处理 ==========
def get_relative_path(file_path):
    """获取相对于仓库根目录的路径"""
    file_path = Path(file_path)
    try:
        rel = file_path.relative_to(REPO_PATH)
        return str(rel).replace("\\", "/")
    except ValueError:
        return str(file_path).replace("\\", "/")


def get_category(relative_path):
    """从目录结构推断 category"""
    parts = relative_path.split("/")
    if len(parts) > 1:
        if parts[0] in ("知识", "经验", "备忘录", "archive"):
            return parts[0]
        return parts[0]
    return "document"


def get_tags_from_filename(filename):
    """从文件名提取 tags"""
    name = Path(filename).stem
    # 去除日期后缀
    name = re.sub(r'_\d{8}$', '', name)
    # 按常见分隔符拆分
    tags = re.split(r'[_\-/\s]+', name)
    return [t for t in tags if t and len(t) > 1][:5]


def find_md_files():
    """遍历仓库所有 .md 文件"""
    repo = Path(REPO_PATH)
    if not repo.exists():
        print(f"ERROR: 仓库路径不存在: {REPO_PATH}", file=sys.stderr)
        sys.exit(1)
    files = []
    for ext in SUPPORTED_EXTS:
        files.extend(repo.rglob(f"*{ext}"))
    # 排除 .git 目录
    files = [f for f in files if ".git" not in f.parts]
    return sorted(files)


def read_file_content(file_path):
    """读取文件内容（utf-8）"""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            return f.read()
    except UnicodeDecodeError:
        with open(file_path, "r", encoding="gbk", errors="replace") as f:
            return f.read()


# ========== Qdrant 操作 ==========
def upsert_points(points):
    """upsert 点到 Qdrant（必须用 PUT 方法，POST 会报 missing field 'ids'）"""
    if not points:
        return
    payload = {"points": points}
    result = http_put(
        f"{QDRANT_URL}/collections/{COLLECTION}/points",
        payload,
        timeout=60,
    )
    return result


def delete_points(point_ids):
    """从 Qdrant 删除点"""
    if not point_ids:
        return
    # 用 filter 按 source 删除
    payload = {
        "filter": {
            "should": [
                {"key": "source", "match": {"value": sid}}
                for sid in point_ids
            ]
        }
    }
    result = http_post(
        f"{QDRANT_URL}/collections/{COLLECTION}/points/delete",
        payload,
        timeout=30,
    )
    return result


def get_collection_info():
    """获取 collection 信息"""
    return http_get(f"{QDRANT_URL}/collections/{COLLECTION}")


def count_points():
    """统计点数"""
    result = http_post(
        f"{QDRANT_URL}/collections/{COLLECTION}/points/count",
        {"exact": True},
    )
    return result.get("result", {}).get("count", 0)


# ========== 灌入逻辑 ==========
def ingest_file(file_path, date_str=None):
    """灌入单个文件，返回灌入的块数"""
    file_path = Path(file_path)  # 确保是 Path 对象
    if date_str is None:
        date_str = datetime.now().strftime("%Y-%m-%d")

    relative_path = get_relative_path(file_path)
    content = read_file_content(file_path)
    if not content.strip():
        return 0

    chunks = split_into_chunks(content)
    if not chunks:
        return 0

    category = get_category(relative_path)
    tags = get_tags_from_filename(file_path.name)

    # 批量 embed
    points = []
    for i, chunk in enumerate(chunks):
        try:
            vector = embed_text(chunk)
        except Exception as e:
            print(f"  WARN: embed 失败 (chunk {i}): {e}", file=sys.stderr)
            continue

        point = {
            "id": make_point_id(relative_path, i),
            "vector": vector,
            "payload": {
                "content": chunk,
                "source": relative_path,
                "category": category,
                "date": date_str,
                "tags": tags,
                "path": relative_path,
                "chunk_index": i,
                "total_chunks": len(chunks),
            },
        }
        points.append(point)

    if points:
        upsert_points(points)
    return len(points)


def ingest_all():
    """全量灌入"""
    files = find_md_files()
    print(f"全量灌入: {len(files)} 个文件")
    total_chunks = 0
    failed = 0
    for i, f in enumerate(files, 1):
        rel = get_relative_path(f)
        try:
            chunks = ingest_file(f)
            total_chunks += chunks
            print(f"  [{i}/{len(files)}] {rel} -> {chunks} 块")
        except Exception as e:
            failed += 1
            print(f"  [{i}/{len(files)}] {rel} -> FAIL: {e}", file=sys.stderr)
    print(f"\n完成: {len(files)} 文件, {total_chunks} 块, {failed} 失败")
    print(f"Qdrant 总点数: {count_points()}")


def ingest_changed():
    """增量灌入 git pull 后变更的文件"""
    import subprocess
    # 获取变更的 .md 文件
    result = subprocess.run(
        ["git", "diff", "HEAD@{1}", "--name-only", "--diff-filter=ACMR", "*.md"],
        cwd=REPO_PATH, capture_output=True, text=True, timeout=30,
    )
    changed = [l.strip() for l in result.stdout.splitlines() if l.strip()]

    if not changed:
        # 可能是首次 pull 或无变更，尝试 git log
        result = subprocess.run(
            ["git", "log", "--oneline", "-1"],
            cwd=REPO_PATH, capture_output=True, text=True, timeout=30,
        )
        print(f"无变更文件（{result.stdout.strip()}）")
        return

    print(f"增量灌入: {len(changed)} 个变更文件")
    total_chunks = 0
    for i, rel in enumerate(changed, 1):
        file_path = Path(REPO_PATH) / rel
        if not file_path.exists():
            print(f"  [{i}] {rel} -> 已删除，跳过（用 --deleted 清理）")
            continue
        if file_path.suffix not in SUPPORTED_EXTS:
            continue
        try:
            chunks = ingest_file(file_path)
            total_chunks += chunks
            print(f"  [{i}] {rel} -> {chunks} 块")
        except Exception as e:
            print(f"  [{i}] {rel} -> FAIL: {e}", file=sys.stderr)
    print(f"\n完成: {total_chunks} 块灌入")


def ingest_deleted():
    """删除已从仓库删除的文件对应的向量"""
    import subprocess
    result = subprocess.run(
        ["git", "diff", "HEAD@{1}", "--name-only", "--diff-filter=D", "*.md"],
        cwd=REPO_PATH, capture_output=True, text=True, timeout=30,
    )
    deleted = [l.strip() for l in result.stdout.splitlines() if l.strip()]

    if not deleted:
        print("无删除文件")
        return

    print(f"清理删除文件: {len(deleted)} 个")
    for rel in deleted:
        try:
            delete_points([rel])
            print(f"  删除: {rel}")
        except Exception as e:
            print(f"  FAIL: {rel} -> {e}", file=sys.stderr)
    print(f"\n完成: {len(deleted)} 文件清理")


def show_stats():
    """统计当前 Qdrant 数据"""
    info = get_collection_info()
    result = info.get("result", {})
    count = count_points()
    print(f"Collection: {COLLECTION}")
    print(f"  状态: {result.get('status')}")
    print(f"  点数: {count}")
    print(f"  向量维度: {result.get('config', {}).get('params', {}).get('vectors', {}).get('size')}")
    print(f"  距离: {result.get('config', {}).get('params', {}).get('vectors', {}).get('distance')}")
    print(f"  已索引向量: {result.get('indexed_vectors_count')}")


def test_search(query, top_k=5, threshold=0.65):
    """测试检索"""
    vector = embed_text(query)
    payload = {
        "vector": vector,
        "limit": top_k,
        "score_threshold": threshold,
        "with_payload": True,
        "with_vector": False,
    }
    result = http_post(
        f"{QDRANT_URL}/collections/{COLLECTION}/points/search",
        payload,
    )
    hits = result.get("result", [])
    print(f"🔍 查询: {query}")
    print(f"   命中: {len(hits)} 条")
    for i, hit in enumerate(hits, 1):
        score = hit.get("score", 0)
        p = hit.get("payload", {})
        print(f"  [{i}] score={score:.4f} | {p.get('source', '?')}")
        print(f"      {p.get('content', '')[:150]}")
        print()


def main():
    parser = argparse.ArgumentParser(description="RAG 数据灌入脚本")
    g = parser.add_mutually_exclusive_group(required=True)
    g.add_argument("--all", action="store_true", help="全量灌入")
    g.add_argument("--file", help="灌入单个文件")
    g.add_argument("--changed", action="store_true", help="增量灌入变更文件")
    g.add_argument("--deleted", action="store_true", help="清理删除文件")
    g.add_argument("--stats", action="store_true", help="统计当前数据")
    g.add_argument("--search", help="测试检索")
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--threshold", type=float, default=0.65)
    args = parser.parse_args()

    try:
        if args.all:
            ingest_all()
        elif args.file:
            chunks = ingest_file(args.file)
            print(f"灌入: {args.file} -> {chunks} 块")
        elif args.changed:
            ingest_changed()
        elif args.deleted:
            ingest_deleted()
        elif args.stats:
            show_stats()
        elif args.search:
            test_search(args.search, args.top_k, args.threshold)
    except urllib.error.URLError as e:
        print(f"ERROR: 网络错误 - {e}", file=sys.stderr)
        sys.exit(2)
    except Exception as e:
        print(f"ERROR: {type(e).__name__} - {e}", file=sys.stderr)
        sys.exit(3)


if __name__ == "__main__":
    main()
