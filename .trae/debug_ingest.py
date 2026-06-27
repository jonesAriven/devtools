#!/usr/bin/env python3
"""调试 ingest.py 的 --file 流程"""
import sys
sys.path.insert(0, '/home/root01/rag-tools')
import ingest
import json
import urllib.request
import urllib.error

# 1. 测试 embed
print("=== 1. 测试 embed_text ===")
try:
    v = ingest.embed_text("测试文本")
    print(f"  dim={len(v)}, first3={v[:3]}")
except Exception as e:
    print(f"  FAIL: {e}")
    sys.exit(1)

# 2. 测试 make_point_id
print("\n=== 2. 测试 make_point_id ===")
rel = "小桉工作规则.md"
pid = ingest.make_point_id(rel, 0)
print(f"  relative_path={rel}, point_id={pid}, type={type(pid).__name__}")

# 3. 测试单个 point upsert
print("\n=== 3. 测试 upsert 单个 point ===")
point = {
    "id": pid,
    "vector": v,
    "payload": {
        "content": "测试内容",
        "source": rel,
        "category": "测试",
        "date": "2026-06-27",
        "tags": ["test"],
        "path": rel,
        "chunk_index": 0,
        "total_chunks": 1,
    },
}
try:
    result = ingest.upsert_points([point])
    print(f"  OK: {result}")
except Exception as e:
    print(f"  FAIL: {e}")
    # 抓取原始响应
    data = json.dumps({"points": [point]}).encode("utf-8")
    req = urllib.request.Request(
        f"{ingest.QDRANT_URL}/collections/{ingest.COLLECTION}/points",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        print(f"  raw resp: {resp.read().decode()}")
    except urllib.error.HTTPError as he:
        print(f"  HTTP {he.code}: {he.read().decode()}")

# 4. 测试 stats
print("\n=== 4. 测试 stats ===")
try:
    s = ingest.show_stats()
except Exception as e:
    print(f"  FAIL: {e}")

# 5. 测试 ingest_file
print("\n=== 5. 测试 ingest_file ===")
try:
    n = ingest.ingest_file("/home/root01/openclaw-work-space/小桉工作规则.md")
    print(f"  OK: 灌入 {n} 块")
except Exception as e:
    print(f"  FAIL: {e}")
    import traceback
    traceback.print_exc()
