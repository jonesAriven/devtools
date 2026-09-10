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

注意：本生成器读取 module-registry.yml 的「真实 schema」（顶层
infrastructure / modules / plugins / profile-presets）。旧 schema 的
apis / menus / publishes / subscribes / degradation 等字段在 Phase 0.5
重构后已不存在，对应章节在数据缺失时优雅跳过，不会输出空标题。
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


def _section(registry: dict, key: str):
    """优先取顶层 key，兼容旧 schema 中 registry: 嵌套结构作为 fallback。

    profile-presets 是 dict，其余（infrastructure/modules/plugins）是 list。
    """
    val = registry.get(key)
    if isinstance(val, (list, dict)) and val:
        return val
    default = {} if key == "profile-presets" else []
    return registry.get("registry", {}).get(key, default)


def _port(v) -> str:
    """格式化端口：None / 'null' 显示为长破折号。"""
    if v is None or v == "null":
        return "—"
    return str(v)


def _profiles(v) -> str:
    """profile 可能是标量或列表，统一成字符串。"""
    if v is None:
        return "-"
    if isinstance(v, list):
        return ", ".join(str(x) for x in v) if v else "-"
    return str(v)


def generate_markdown(registry: dict, output_path: str) -> str:
    """生成 Markdown 模块契约文档"""
    infrastructure = _section(registry, "infrastructure")
    modules = _section(registry, "modules")
    plugins = _section(registry, "plugins")
    profile_presets = _section(registry, "profile-presets")

    lines = []
    lines.append("# mykng 模块契约文档")
    lines.append("")
    lines.append(f"> 自动生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"> 数据源：`module-registry.yml`")
    lines.append(f"> 模块总数：{len(modules)} 个模块 / {len(infrastructure)} 项基础设施 / {len(plugins)} 个插件")
    lines.append("")
    lines.append("---")
    lines.append("")

    # ===== 目录 =====
    lines.append("## 目录")
    lines.append("")
    lines.append("1. [模块总览](#1-模块总览)")
    lines.append("2. [网关路由](#2-网关路由)")
    lines.append("3. [模块依赖](#3-模块依赖)")
    lines.append("4. [基础设施](#4-基础设施)")
    lines.append("5. [插件清单](#5-插件清单)")
    lines.append("6. [Profile 环境预设](#6-profile-环境预设)")
    lines.append("7. [架构图](#7-架构图)")
    lines.append("")
    lines.append("---")
    lines.append("")

    # ===== 1. 模块总览 =====
    lines.append("## 1. 模块总览")
    lines.append("")
    lines.append("| 模块 | 类型 | Nacos 注册 | 容器端口 | 宿主端口 | 数据库 | 描述 |")
    lines.append("|------|------|:---:|:---:|:---:|------|------|")
    for m in modules:
        nacos = "✅" if m.get("nacos-registered") else "➖"
        db = m.get("database", "—")
        lines.append(
            f"| `{m['name']}` | {m.get('type', '-')} | {nacos} | "
            f"{_port(m.get('port'))} | {_port(m.get('host-port'))} | "
            f"`{db}` | {m.get('description', '-')} |"
        )
    lines.append("")

    # ===== 2. 网关路由 =====
    lines.append("## 2. 网关路由")
    lines.append("")
    gw = next((m for m in modules if m.get("routes")), None)
    if gw:
        lines.append("| 路径 | 目标模块 |")
        lines.append("|------|----------|")
        for r in gw["routes"]:
            lines.append(f"| `{r.get('path', '-')}` | `{r.get('target', '-')}` |")
    else:
        lines.append("（未配置网关路由）")
    lines.append("")

    # ===== 3. 模块依赖 =====
    lines.append("## 3. 模块依赖")
    lines.append("")
    lines.append("| 模块 | 依赖服务 | 依赖基础设施 |")
    lines.append("|------|----------|--------------|")
    for m in modules:
        deps = m.get("depends-on") or []
        infra_deps = m.get("infra-depends") or []
        dep_s = ", ".join(f"`{d}`" for d in deps) or "—"
        infra_s = ", ".join(f"`{d}`" for d in infra_deps) or "—"
        lines.append(f"| `{m['name']}` | {dep_s} | {infra_s} |")
    lines.append("")

    # ===== 4. 基础设施 =====
    lines.append("## 4. 基础设施")
    lines.append("")
    lines.append("| 名称 | 镜像 | 端口 | Profile | 内存上限 | 描述 |")
    lines.append("|------|------|------|---------|----------|------|")
    for i in infrastructure:
        port = i.get("port") or (i.get("ports") and i["ports"][0]) or "—"
        lines.append(
            f"| `{i['name']}` | `{i.get('image', '-')}` | {_port(port)} | "
            f"{_profiles(i.get('profile'))} | {i.get('mem-limit', '-')} | "
            f"{i.get('description', '-')} |"
        )
    lines.append("")

    # ===== 5. 插件清单 =====
    lines.append("## 5. 插件清单")
    lines.append("")
    if plugins:
        lines.append(f"共 {len(plugins)} 个。")
        lines.append("")
        lines.append("| 插件 ID | 名称 | 优先级 | 所属模块 | 默认启用 | 可卸载 |")
        lines.append("|---------|------|:---:|----------|:---:|:---:|")
        for p in plugins:
            en = "✅" if p.get("enabled-by-default") else "➖"
            rem = "✅" if p.get("removable") else "❌"
            lines.append(
                f"| `{p.get('id', '-')}` | {p.get('name', '-')} | {p.get('priority', '-')} | "
                f"`{p.get('module', '-')}` | {en} | {rem} |"
            )
    else:
        lines.append("（未配置插件）")
    lines.append("")

    # ===== 6. Profile 环境预设 =====
    lines.append("## 6. Profile 环境预设")
    lines.append("")
    if profile_presets:
        for name, cfg in profile_presets.items():
            lines.append(f"### {name}")
            lines.append("")
            lines.append(f"- **描述**：{cfg.get('description', '-')}")
            mods = cfg.get("modules", []) or []
            lines.append(f"- **模块**：{', '.join(f'`{x}`' for x in mods) or '-'}")
            inf = cfg.get("infra", cfg.get("infrastructure", [])) or []
            lines.append(f"- **基础设施**：{', '.join(f'`{x}`' for x in inf) or '-'}")
            pl = cfg.get("plugins", [])
            pls = "all" if pl == "all" else (', '.join(f'`{x}`' for x in pl) or "-")
            lines.append(f"- **插件**：{pls}")
            lines.append("")
    else:
        lines.append("（未配置 Profile 预设）")
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
    with open(output_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    return output_path


def _node_id(s: str) -> str:
    """Mermaid 节点 ID 只允许字母数字，去掉连字符。"""
    return (s or "node").replace("-", "").replace(" ", "")


def _mport(v) -> str:
    if v is None or v == "null":
        return "-"
    return str(v)


def generate_mermaid_code(registry: dict) -> str:
    """生成 Mermaid 架构图代码（数据驱动，读取真实 schema）"""
    infrastructure = _section(registry, "infrastructure")
    modules = _section(registry, "modules")
    gw = next((m for m in modules if m.get("routes")), None)
    gw_name = gw["name"] if gw else "kb-gateway"

    def gw_port(m):
        hp = m.get("host-port")
        if hp not in (None, "null"):
            return _mport(hp)
        return _mport(m.get("port"))

    lines = ["graph TD", ""]
    lines.append("    %% ===== 入口层 =====")
    lines.append(
        f"    User[用户] -->|HTTPS| {_node_id(gw_name)}[{gw_name}<br/>:"
        f"{gw_port(gw) if gw else '8090'}]"
    )
    web = next((m for m in modules if m.get("type") == "frontend"), None)
    if web:
        lines.append(
            f"    {_node_id(gw_name)} -->|静态资源| {_node_id(web['name']) }"
            f"[{web['name']}<br/>:{gw_port(web)}]"
        )
    lines.append("")

    lines.append("    %% ===== 网关路由（lb://）=====")
    if gw:
        seen = set()
        for r in gw["routes"]:
            t = r.get("target")
            if not t or t in seen:
                continue
            seen.add(t)
            tm = next((m for m in modules if m.get("name") == t), None)
            tport = gw_port(tm) if tm else "-"
            lines.append(f"    {_node_id(gw_name)} -->|lb://| {_node_id(t)}[{t}<br/>:{tport}]")
    lines.append("")

    lines.append("    %% ===== 基础设施 =====")
    infra_ids = {}
    for i in infrastructure:
        iid = i["name"]
        nid = _node_id(iid)
        iport = i.get("port") or (i.get("ports") and i["ports"][0]) or "-"
        lines.append(f"    {nid}[({iid}<br/>:{_mport(iport)})]")
        infra_ids[iid] = nid
    lines.append("")

    lines.append("    %% ===== 模块依赖基础设施 =====")
    for m in modules:
        if m.get("type") == "frontend":
            continue
        mid = _node_id(m["name"])
        for d in (m.get("infra-depends") or []):
            if d in infra_ids:
                lines.append(f"    {mid} --> {infra_ids[d]}")
    lines.append("")

    lines.append("    %% ===== 服务发现 =====")
    lines.append("    Nacos[(Nacos<br/>:8848)]")
    for m in modules:
        if m.get("nacos-registered"):
            lines.append(f"    {_node_id(m['name'])} -.注册.-> Nacos")
    lines.append("")

    lines.append("    %% ===== 样式 =====")
    type_color = {"service": "#10b981", "frontend": "#64748b", "external": "#10b981"}
    lines.append(f"    style {_node_id(gw_name)} fill:#4f46e5,color:#fff,stroke:#4338ca")
    for m in modules:
        if m.get("name") == gw_name:
            continue
        c = type_color.get(m.get("type"), "#10b981")
        lines.append(f"    style {_node_id(m['name'])} fill:{c},color:#fff,stroke:{c}")
    lines.append("")

    return "\n".join(lines)


def generate_mermaid_file(registry: dict, output_path: str) -> str:
    """生成独立的 Mermaid 架构图文件"""
    code = generate_mermaid_code(registry)
    with open(output_path, "w", encoding="utf-8", newline="\n") as f:
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

    modules = _section(registry, "modules")
    infra = _section(registry, "infrastructure")
    print(f"   模块: {len(modules)} 个")
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
