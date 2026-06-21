#!/usr/bin/env python3
"""
知识库微服务性能压测脚本
基于实际机器资源：2核 i5-7400 @ 3.0GHz, 3.3GB 可用内存

压测维度：
1. 登录接口 — 基准并发/峰值并发
2. 文档列表 — 读写比评估
3. 搜索接口 — MeiliSearch 查询性能
4. 文件上传 — IO密集型
5. 混合场景 — 模拟真实用户行为

评估标准（2核机器等比）：
- P95 < 500ms = 优秀
- P95 < 1000ms = 合格
- P95 < 2000ms = 可接受
- P95 > 2000ms = 需优化
- 错误率 < 1% = 合格
- 吞吐量 > 50 req/s = 合格（2核小机器）
"""

import requests
import time
import json
import statistics
import concurrent.futures
import threading
from collections import defaultdict
from datetime import datetime
import sys
import os

BASE_URL = "http://localhost:8090/kb/api"
TIMEOUT = 10
REPORT_FILE = os.path.join(os.path.dirname(__file__), "perf-test-report.json")

# 压测配置
CONFIGS = {
    "login": {
        "name": "登录接口",
        "method": "POST",
        "path": "/auth/login",
        "json": {"username": "admin", "password": "admin123"},
        "concurrency": [1, 5, 10, 20, 50],
        "requests_per_level": 50,
    },
    "doc_list": {
        "name": "文档列表",
        "method": "GET",
        "path": "/doc/list",
        "needs_auth": True,
        "concurrency": [1, 5, 10, 20, 50],
        "requests_per_level": 50,
    },
    "search": {
        "name": "搜索接口",
        "method": "GET",
        "path": "/search?q=test",
        "needs_auth": True,
        "concurrency": [1, 5, 10, 20, 50],
        "requests_per_level": 50,
    },
    "folder_tree": {
        "name": "文件夹树",
        "method": "GET",
        "path": "/folder/tree/1",
        "needs_auth": True,
        "concurrency": [1, 5, 10, 20],
        "requests_per_level": 40,
    },
}

# 混合场景权重
MIXED_SCENARIOS = [
    ("doc_list", 0.40),    # 40% 查看文档列表
    ("search", 0.30),      # 30% 搜索
    ("folder_tree", 0.15), # 15% 浏览文件夹
    ("login", 0.15),       # 15% 登录（新会话）
]


def get_token():
    """获取认证 token"""
    r = requests.post(f"{BASE_URL}/auth/login",
                      json={"username": "admin", "password": "admin123"},
                      timeout=TIMEOUT)
    return r.json()["data"]["accessToken"]


def make_request(config, token=None):
    """执行单个请求，返回 (耗时ms, 状态码, 是否成功)"""
    url = f"{BASE_URL}{config['path']}"
    headers = {}
    if config.get("needs_auth") and token:
        headers["Authorization"] = f"Bearer {token}"

    start = time.monotonic()
    try:
        if config["method"] == "GET":
            r = requests.get(url, headers=headers, timeout=TIMEOUT)
        else:
            r = requests.post(url, json=config.get("json"), headers=headers, timeout=TIMEOUT)

        elapsed = (time.monotonic() - start) * 1000
        success = r.status_code == 200
        return elapsed, r.status_code, success
    except Exception as e:
        elapsed = (time.monotonic() - start) * 1000
        return elapsed, 0, False


def run_concurrent_test(config, concurrency, total_requests, token=None):
    """并发压测"""
    latencies = []
    errors = 0
    completed = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = []
        for _ in range(total_requests):
            futures.append(pool.submit(make_request, config, token))

        for future in concurrent.futures.as_completed(futures):
            elapsed, status, success = future.result()
            latencies.append(elapsed)
            if not success:
                errors += 1
            completed += 1

    latencies.sort()
    return {
        "concurrency": concurrency,
        "total_requests": completed,
        "errors": errors,
        "error_rate": f"{errors/completed*100:.2f}%",
        "latency_avg_ms": round(statistics.mean(latencies), 1),
        "latency_p50_ms": round(latencies[len(latencies)//2], 1),
        "latency_p90_ms": round(latencies[int(len(latencies)*0.9)], 1),
        "latency_p95_ms": round(latencies[int(len(latencies)*0.95)], 1),
        "latency_max_ms": round(max(latencies), 1),
        "throughput_rps": round(completed / (sum(latencies) / 1000 / concurrency), 1),
    }


def run_mixed_scenario(token, concurrency, duration_sec=30):
    """混合场景压测"""
    latencies = []
    errors = 0
    stop_time = time.monotonic() + duration_sec
    request_count = 0

    def worker():
        nonlocal request_count
        local_latencies = []
        local_errors = 0
        while time.monotonic() < stop_time:
            # 按权重选择场景
            rand = threading.current_thread()._args[0] if hasattr(threading.current_thread(), '_args') else 0.5
            import random
            r = random.random()
            cumulative = 0
            for scenario_name, weight in MIXED_SCENARIOS:
                cumulative += weight
                if r <= cumulative:
                    config = CONFIGS[scenario_name]
                    break
            else:
                config = CONFIGS["doc_list"]

            elapsed, status, success = make_request(config, token)
            local_latencies.append(elapsed)
            if not success:
                local_errors += 1
            request_count += 1

        return local_latencies, local_errors

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker) for _ in range(concurrency)]
        for future in concurrent.futures.as_completed(futures):
            l, e = future.result()
            latencies.extend(l)
            errors += e

    if not latencies:
        return {"error": "no requests completed"}

    latencies.sort()
    total = len(latencies)
    actual_duration = duration_sec
    return {
        "concurrency": concurrency,
        "duration_sec": actual_duration,
        "total_requests": total,
        "errors": errors,
        "error_rate": f"{errors/total*100:.2f}%",
        "latency_avg_ms": round(statistics.mean(latencies), 1),
        "latency_p50_ms": round(latencies[total//2], 1),
        "latency_p90_ms": round(latencies[int(total*0.9)], 1),
        "latency_p95_ms": round(latencies[int(total*0.95)], 1),
        "latency_max_ms": round(max(latencies), 1),
        "throughput_rps": round(total / actual_duration, 1),
    }


def assess_performance(result):
    """根据2核机器标准评估性能"""
    p95 = result.get("latency_p95_ms", 9999)
    error_rate = float(result.get("error_rate", "100%").replace("%", ""))
    throughput = result.get("throughput_rps", 0)

    grade = ""
    if p95 < 500 and error_rate < 1 and throughput > 50:
        grade = "✅ 优秀"
    elif p95 < 1000 and error_rate < 1:
        grade = "✅ 合格"
    elif p95 < 2000 and error_rate < 5:
        grade = "⚠️ 可接受"
    else:
        grade = "❌ 需优化"

    return grade


def main():
    print("=" * 70)
    print("知识库微服务性能压测")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"目标: {BASE_URL}")
    print(f"机器: 2核 i5-7400 @ 3.0GHz, 3.3GB 可用内存")
    print("=" * 70)

    # 获取 token
    print("\n🔑 获取认证 Token...")
    token = get_token()
    print(f"   Token: {token[:20]}...")

    all_results = {}

    # 1. 单接口压测
    for key, config in CONFIGS.items():
        print(f"\n{'='*60}")
        print(f"📊 压测: {config['name']} ({config['method']} {config['path']})")
        print(f"{'='*60}")

        all_results[key] = []
        for concurrency in config["concurrency"]:
            total = config["requests_per_level"]
            print(f"\n  并发={concurrency}, 请求数={total} ...", end="", flush=True)

            result = run_concurrent_test(config, concurrency, total, token)
            grade = assess_performance(result)

            print(f" 完成 | P50={result['latency_p50_ms']}ms P95={result['latency_p95_ms']}ms "
                  f"TPS={result['throughput_rps']} 错误率={result['error_rate']} {grade}")

            all_results[key].append(result)

    # 2. 混合场景压测
    print(f"\n{'='*60}")
    print(f"📊 混合场景压测 (30秒, 模拟真实用户行为)")
    print(f"{'='*60}")

    all_results["mixed"] = []
    for concurrency in [10, 20, 50]:
        print(f"\n  并发={concurrency}, 持续30秒 ...", end="", flush=True)
        result = run_mixed_scenario(token, concurrency, duration_sec=30)
        grade = assess_performance(result)
        print(f" 完成 | P50={result['latency_p50_ms']}ms P95={result['latency_p95_ms']}ms "
              f"TPS={result['throughput_rps']} 错误率={result['error_rate']} {grade}")
        all_results["mixed"].append(result)

    # 3. 总结报告
    print(f"\n{'='*70}")
    print("📋 性能压测总结")
    print(f"{'='*70}")

    for key, results in all_results.items():
        name = CONFIGS.get(key, {}).get("name", "混合场景")
        print(f"\n  {name}:")
        for r in results:
            grade = assess_performance(r)
            print(f"    并发{r['concurrency']:>3} | P50={r['latency_p50_ms']:>7}ms | "
                  f"P95={r['latency_p95_ms']:>7}ms | TPS={r['throughput_rps']:>6} | "
                  f"错误={r['error_rate']:>6} | {grade}")

    # 4. 性能评估总结
    print(f"\n{'='*70}")
    print("📝 性能评估（基于2核3.3GB机器资源）")
    print(f"{'='*70}")

    # 找到最优并发
    best_concurrency = {}
    for key, results in all_results.items():
        name = CONFIGS.get(key, {}).get("name", "混合场景")
        best = None
        for r in results:
            grade = assess_performance(r)
            if "✅" in grade and (best is None or r["throughput_rps"] > best["throughput_rps"]):
                best = r
        if best:
            best_concurrency[name] = best
            print(f"  {name}: 最优并发={best['concurrency']} TPS={best['throughput_rps']} "
                  f"P95={best['latency_p95_ms']}ms")

    # 保存报告
    report = {
        "timestamp": datetime.now().isoformat(),
        "machine": {"cpu": "2x i5-7400 @ 3.0GHz", "memory": "3.3GB available"},
        "target": BASE_URL,
        "results": all_results,
    }
    with open(REPORT_FILE, "w") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"\n📁 报告已保存: {REPORT_FILE}")

    print(f"\n✅ 性能压测完成！")


if __name__ == "__main__":
    main()
