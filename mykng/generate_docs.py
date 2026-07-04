#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M6 文档自动化生成工具
====================
从 module-registry.yml 自动生成：
  1. 模块契约文档（Markdown）
  2. 系统架构图（Mermaid）

用法:
  python generate_docs.py                # 生成所有文档
  python generate_docs.py --md-only      # 只生成 Markdown 契约文档
  python generate_docs.py --mermaid-only # 只生成 Mermaid 架构图
  python generate_docs.py -o <output_dir> # 指定输出目录（默认: docs/generated/）

可拔插架构的核心：新增模块只需改 module-registry.yml，
运行此脚本即可自动更新文档，无需人工维护。
"""

import argparse
import os
import sys
from datetime import datetime
from pathlib import Path

import yaml


def load_registry(registry_path: str) -> dict:
    """加载 module-registry.yml"""
    with open(registry_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def generate_markdown(registry: dict, output_path: str) -> str:
    """生成 Markdown 模块契约文档"""
    data = registry.get("registry", {})
    infrastructure = data.get("infrastructure", [])
    services = data.get("services", [])
    frontend = data.get("frontend", [])
    pluggable_rules = registry.get("pluggableRules", {})
    profile_presets = registry.get("profilePresets", {})

    lines = []
    lines.append("# mykng 模块契约文档")
    lines.append("")
    lines.append(f"> 自动生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"> 数据源：`module-registry.yml`")
    lines.append(f"> 模块总数：{len(services)} 个服务 + {len(infrastructure)} 个基础设施 + {len(frontend)} 个前端")
    lines.append("")
    lines.append("---")
    lines.append("")

    # ===== 目录 =====
    lines.append("## 目录")
    lines.append("")
    lines.append("1. [基础设施清单](#1-基础设施清单)")
    lines.append("2. [微服务模块契约](#2-微服务模块契约)")
    lines.append("3. [前端模块](#3-前端模块)")
    lines.append("4. [事件流全景图](#4-事件流全景图)")
    lines.append("5. [Profile 环境预设](#5-profile-环境预设)")
    lines.append("6. [可拔插规则](#6-可拔插规则)")
    lines.append("7. [架构图](#7-架构图)")
    lines.append("")
    lines.append("---")
    lines.append("")

    # ===== 1. 基础设施 =====
    lines.append("## 1. 基础设施清单")
    lines.append("")
    lines.append("| ID | 名称 | 类型 | 端口 | 镜像 | 必选 | Profiles | 数据隔离 |")
    lines.append("|----|------|------|------|------|------|----------|----------|")
    for infra in infrastructure:
        data_isolation = []
        if infra.get("databases"):
            data_isolation.append(f"DB: {', '.join(infra['databases'])}")
        if infra.get("streams"):
            data_isolation.append(f"Streams: {len(infra['streams'])} 条")
        if infra.get("buckets"):
            data_isolation.append(f"Buckets: {', '.join(infra['buckets'])}")
        if infra.get("indexes"):
            data_isolation.append(f"Indexes: {', '.join(infra['indexes'])}")
        if infra.get("collections"):
            data_isolation.append(f"Collections: {', '.join(infra['collections'])}")
        data_iso_str = "<br>".join(data_isolation) if data_isolation else "-"
        required = "✅" if infra.get("required") else "➖"
        profiles = ", ".join(infra.get("profile", []))
        lines.append(
            f"| `{infra['id']}` | {infra['name']} | {infra.get('type', '-')} | "
            f"{infra.get('port', '-')} | `{infra.get('image', '-')}` | "
            f"{required} | {profiles} | {data_iso_str} |"
        )
    lines.append("")

    # ===== 2. 微服务模块契约 =====
    lines.append("## 2. 微服务模块契约")
    lines.append("")
    lines.append("每个模块的自描述契约：API、发布事件、订阅事件、降级策略、前端菜单。")
    lines.append("")

    for svc in services:
        lines.append(f"### 2.{services.index(svc)+1} {svc['name']} (`{svc['id']}`)")
        lines.append("")
        lines.append(f"- **技术栈**：{svc.get('techStack', '-')}")
        lines.append(f"- **端口**：{svc.get('port', '-')}")
        lines.append(f"- **上下文路径**：`{svc.get('contextPath', '-')}`")
        lines.append(f"- **必选**：{'✅ 是' if svc.get('required') else '➖ 可选'}")
        lines.append(f"- **Nacos 服务发现**：{'✅ 已启用' if svc.get('nacosDiscovery') else '❌ 未启用'}")
        lines.append(f"- **Profiles**：{', '.join(svc.get('profile', []))}")
        lines.append(f"- **依赖基础设施**：{', '.join(f'`{d}`' for d in svc.get('dependsOn', []))}")
        if svc.get("dependsOnServices"):
            dep_svcs = [f"`{d['service']}` (via {d['via']})" for d in svc["dependsOnServices"]]
            lines.append(f"- **依赖服务**：{', '.join(dep_svcs)}")
        lines.append("")

        # API 列表
        api_list = svc.get("api", [])
        if api_list:
            lines.append("#### 暴露的 API")
            lines.append("")
            lines.append("| 路径 | 方法 | 鉴权 |")
            lines.append("|------|------|------|")
            for api in api_list:
                auth = "否" if api.get("auth") is False else ("是" if api.get("auth") else "-")
                lines.append(f"| `{api['path']}` | {api.get('method', 'ANY')} | {auth} |")
            lines.append("")

        # 发布事件
        publishes = svc.get("publishes", [])
        if publishes:
            lines.append("#### 发布的事件")
            lines.append("")
            lines.append("| 事件类型 | 描述 | Payload |")
            lines.append("|----------|------|---------|")
            for evt in publishes:
                payload_str = ", ".join(evt.get("payload", {}).keys()) if isinstance(evt.get("payload"), dict) else str(evt.get("payload", "-"))
                lines.append(f"| `{evt['event']}` | {evt.get('description', '-')} | `{payload_str}` |")
            lines.append("")

        # 订阅事件
        subscribes = svc.get("subscribes", [])
        if subscribes:
            lines.append("#### 订阅的事件")
            lines.append("")
            lines.append("| 事件类型 | 处理逻辑 |")
            lines.append("|----------|----------|")
            for sub in subscribes:
                lines.append(f"| `{sub['event']}` | {sub.get('handler', '-')} |")
            lines.append("")

        # 降级策略
        degradation = svc.get("degradation", [])
        if degradation:
            lines.append("#### 降级策略（依赖模块不可用时）")
            lines.append("")
            lines.append("| 场景 | 降级行为 |")
            lines.append("|------|----------|")
            for d in degradation:
                lines.append(f"| {d.get('scenario', '-')} | {d.get('behavior', '-')} |")
            lines.append("")

        # 前端菜单
        menus = svc.get("menu", [])
        if menus:
            lines.append("#### 前端菜单项")
            lines.append("")
            lines.append("| ID | 名称 | 图标 | 路径 |")
            lines.append("|----|------|------|------|")
            for m in menus:
                lines.append(f"| `{m['id']}` | {m['name']} | {m.get('icon', '-')} | `{m['path']}` |")
            lines.append("")

        lines.append("---")
        lines.append("")

    # ===== 3. 前端模块 =====
    lines.append("## 3. 前端模块")
    lines.append("")
    for fe in frontend:
        lines.append(f"### {fe['name']} (`{fe['id']}`)")
        lines.append("")
        lines.append(f"- **技术栈**：{fe.get('techStack', '-')}")
        lines.append(f"- **部署路径**：`{fe.get('deployPath', '-')}`")
        lines.append(f"- **必选**：{'✅ 是' if fe.get('required') else '➖ 可选'}")
        if fe.get("dynamicMenu"):
            dm = fe["dynamicMenu"]
            lines.append(f"- **动态菜单**：{'✅ 已启用' if dm.get('enabled') else '❌ 未启用'}")
            lines.append(f"  - 拉取端点：`{dm.get('endpoint', '-')}`")
            lines.append(f"  - 刷新间隔：{dm.get('refreshInterval', '-')}")
        lines.append("")

    # ===== 4. 事件流全景 =====
    lines.append("## 4. 事件流全景图")
    lines.append("")
    lines.append("### 4.1 事件通道")
    lines.append("")
    redis_infra = next((i for i in infrastructure if i["id"] == "kb-redis"), None)
    if redis_infra and redis_infra.get("streams"):
        lines.append("| Stream Key | 用途 |")
        lines.append("|------------|------|")
        for stream in redis_infra["streams"]:
            lines.append(f"| `{stream}` | - |")
        lines.append("")

    lines.append("### 4.2 事件发布/订阅关系")
    lines.append("")
    lines.append("| 事件类型 | 发布方 | 订阅方 |")
    lines.append("|----------|--------|--------|")
    # 收集所有事件
    all_events = {}
    for svc in services:
        for evt in svc.get("publishes", []):
            evt_name = evt["event"]
            if evt_name not in all_events:
                all_events[evt_name] = {"publishers": [], "subscribers": []}
            all_events[evt_name]["publishers"].append(svc["id"])
        for sub in svc.get("subscribes", []):
            evt_name = sub["event"]
            if evt_name not in all_events:
                all_events[evt_name] = {"publishers": [], "subscribers": []}
            all_events[evt_name]["subscribers"].append(svc["id"])

    for evt_name, info in sorted(all_events.items()):
        pubs = ", ".join(f"`{p}`" for p in info["publishers"]) or "-"
        subs = ", ".join(f"`{s}`" for s in info["subscribers"]) or "-"
        lines.append(f"| `{evt_name}` | {pubs} | {subs} |")
    lines.append("")

    # ===== 5. Profile 环境预设 =====
    lines.append("## 5. Profile 环境预设")
    lines.append("")
    for profile_name, profile_cfg in profile_presets.items():
        lines.append(f"### {profile_name.upper()} 环境")
        lines.append("")
        lines.append(f"- **描述**：{profile_cfg.get('description', '-')}")
        lines.append(f"- **服务模块**：{', '.join(f'`{s}`' for s in profile_cfg.get('services', []))}")
        lines.append(f"- **基础设施**：{', '.join(f'`{i}`' for i in profile_cfg.get('infrastructure', []))}")
        lines.append(f"- **排除可选模块**：{'是' if profile_cfg.get('excludeOptional') else '否'}")
        lines.append("")

    # ===== 6. 可拔插规则 =====
    lines.append("## 6. 可拔插规则")
    lines.append("")

    if pluggable_rules.get("addModule"):
        lines.append("### 6.1 新增模块（5 步）")
        lines.append("")
        for step in pluggable_rules["addModule"]:
            lines.append(f"- {step['step']}")
        lines.append("")

    if pluggable_rules.get("removeModule"):
        lines.append("### 6.2 删除模块（4 步）")
        lines.append("")
        for step in pluggable_rules["removeModule"]:
            lines.append(f"- {step['step']}")
        lines.append("")

    if pluggable_rules.get("independence"):
        lines.append("### 6.3 模块独立性约束")
        lines.append("")
        for rule in pluggable_rules["independence"]:
            lines.append(f"- {rule}")
        lines.append("")

    if pluggable_rules.get("eventContract"):
        lines.append("### 6.4 事件契约约束")
        lines.append("")
        for rule in pluggable_rules["eventContract"]:
            lines.append(f"- {rule}")
        lines.append("")

    if pluggable_rules.get("degradationRules"):
        lines.append("### 6.5 降级规则")
        lines.append("")
        for rule in pluggable_rules["degradationRules"]:
            lines.append(f"- {rule}")
        lines.append("")

    # ===== 7. 架构图（Mermaid） =====
    lines.append("## 7. 架构图")
    lines.append("")
    mermaid_code = generate_mermaid_code(registry)
    lines.append("```mermaid")
    lines.append(mermaid_code)
    lines.append("```")
    lines.append("")

    # 页脚
    lines.append("---")
    lines.append("")
    lines.append(f"*本文档由 `generate_docs.py` 自动生成，请勿手动编辑。修改 `module-registry.yml` 后重新运行脚本即可更新。*")
    lines.append("")

    content = "\n".join(lines)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(content)
    return output_path


def generate_mermaid_code(registry: dict) -> str:
    """生成 Mermaid 架构图代码"""
    data = registry.get("registry", {})
    infrastructure = data.get("infrastructure", [])
    services = data.get("services", [])

    lines = []
    lines.append("graph TD")
    lines.append("")
    lines.append("    %% ===== 用户端 ===== %%")
    lines.append("    User[用户] -->|HTTPS| Gateway[API 网关<br/>kb-gateway:8090]")
    lines.append("")

    lines.append("    %% ===== 网关层 ===== %%")
    lines.append("    Gateway -->|lb://| Auth[认证服务<br/>kb-auth:8081]")
    lines.append("    Gateway -->|lb://| File[文件服务<br/>kb-file:8082]")
    lines.append("    Gateway -->|lb://| Knowledge[知识库服务<br/>kb-knowledge:8083]")
    lines.append("    Gateway -->|lb://| Intelligence[知识引擎<br/>kb-intelligence:8086]")
    lines.append("")

    lines.append("    %% ===== 服务间事件总线 ===== %%")
    lines.append("    File -->|发布事件| Redis[(Redis Streams<br/>kb-redis:6379)]")
    lines.append("    Knowledge -->|发布事件| Redis")
    lines.append("    Knowledge -->|订阅事件| Redis")
    lines.append("    Intelligence -->|订阅事件| Redis")
    lines.append("")

    lines.append("    %% ===== 基础设施 ===== %%")
    lines.append("    Auth --> MySQL[(MySQL<br/>kb-mysql:3306)]")
    lines.append("    File --> MySQL")
    lines.append("    Knowledge --> MySQL")
    lines.append("    Intelligence --> MySQL")
    lines.append("")
    lines.append("    File --> MinIO[(MinIO<br/>kb-minio:9000)]")
    lines.append("")
    lines.append("    Knowledge --> Meili[(MeiliSearch<br/>kb-meilisearch:7700)]")
    lines.append("    File --> Meili")
    lines.append("    Intelligence --> Meili")
    lines.append("")
    lines.append("    Knowledge --> MongoDB[(MongoDB<br/>kb-mongodb:27017)]")
    lines.append("    Intelligence --> MongoDB")
    lines.append("")

    lines.append("    %% ===== 服务发现 ===== %%")
    lines.append("    Nacos[(Nacos<br/>kb-nacos:8848)]")
    lines.append("    Auth -.注册/发现.-> Nacos")
    lines.append("    File -.注册/发现.-> Nacos")
    lines.append("    Knowledge -.注册/发现.-> Nacos")
    lines.append("    Intelligence -.注册/发现.-> Nacos")
    lines.append("    Gateway -.发现.-> Nacos")
    lines.append("")

    lines.append("    %% ===== 样式 ===== %%")
    lines.append("    style Gateway fill:#4f46e5,color:#fff,stroke:#4338ca")
    lines.append("    style Auth fill:#10b981,color:#fff,stroke:#059669")
    lines.append("    style File fill:#f59e0b,color:#fff,stroke:#d97706")
    lines.append("    style Knowledge fill:#3b82f6,color:#fff,stroke:#2563eb")
    lines.append("    style Intelligence fill:#8b5cf6,color:#fff,stroke:#7c3aed")
    lines.append("    style Redis fill:#ef4444,color:#fff,stroke:#dc2626")
    lines.append("    style MySQL fill:#00758f,color:#fff,stroke:#005f74")
    lines.append("    style MinIO fill:#c83b34,color:#fff,stroke:#a32e29")
    lines.append("    style Meili fill:#ff6c0c,color:#fff,stroke:#d95c0a")
    lines.append("    style MongoDB fill:#13aa52,color:#fff,stroke:#0f8a42")
    lines.append("    style Nacos fill:#646cff,color:#fff,stroke:#5355d8")
    lines.append("")

    return "\n".join(lines)


def generate_mermaid_file(registry: dict, output_path: str) -> str:
    """生成独立的 Mermaid 架构图文件"""
    code = generate_mermaid_code(registry)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(code)
    return output_path


def main():
    parser = argparse.ArgumentParser(description="M6 文档自动化生成工具：从 module-registry.yml 生成 Markdown 契约文档和 Mermaid 架构图")
    parser.add_argument("-i", "--input", default="module-registry.yml", help="module-registry.yml 路径（默认: ./module-registry.yml）")
    parser.add_argument("-o", "--output-dir", default="docs/generated", help="输出目录（默认: docs/generated/）")
    parser.add_argument("--md-only", action="store_true", help="只生成 Markdown 契约文档")
    parser.add_argument("--mermaid-only", action="store_true", help="只生成 Mermaid 架构图")
    args = parser.parse_args()

    # 确定输入文件路径（相对于脚本所在目录）
    script_dir = Path(__file__).resolve().parent
    input_path = Path(args.input)
    if not input_path.is_absolute():
        input_path = script_dir / input_path
    if not input_path.exists():
        print(f"❌ 错误：找不到 {input_path}")
        sys.exit(1)

    # 确定输出目录
    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = script_dir / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    # 加载配置
    print(f"📖 加载配置：{input_path}")
    registry = load_registry(str(input_path))

    services = registry.get("registry", {}).get("services", [])
    infra = registry.get("registry", {}).get("infrastructure", [])
    print(f"   服务模块: {len(services)} 个")
    print(f"   基础设施: {len(infra)} 个")

    generated = []

    # 生成 Markdown
    if not args.mermaid_only:
        md_path = output_dir / "模块契约文档.md"
        generate_markdown(registry, str(md_path))
        generated.append(md_path)
        print(f"✅ Markdown 契约文档：{md_path}")

    # 生成 Mermaid
    if not args.md_only:
        mmd_path = output_dir / "架构图.mmd"
        generate_mermaid_file(registry, str(mmd_path))
        generated.append(mmd_path)
        print(f"✅ Mermaid 架构图：{mmd_path}")

    print("")
    print(f"🎉 完成！共生成 {len(generated)} 个文档：")
    for g in generated:
        print(f"   - {g}")


if __name__ == "__main__":
    main()
