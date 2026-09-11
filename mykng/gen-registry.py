#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""mykng 注册表生成器 / 漂移门禁
================================

单一事实来源：`mykng/module-registry.yml`
本脚本把该文件派生成三类产物，任何一处都不允许手工编辑。

用法:
    python3 mykng/gen-registry.py                  # 生成全部派生产物
    python3 mykng/gen-registry.py --check          # 只校验哈希（仅标准库，CI 可用）
    python3 mykng/gen-registry.py --check-full     # 重新生成并与磁盘逐字节比对（需 PyYAML）

派生产物:
    1. kb-gateway/src/main/resources/module-manifest.json
       网关运行时的「期望模块集」，用于替代原先硬编码的 KNOWN_MODULES
    2. docs/generated/模块契约文档.md
    3. docs/generated/架构图.mmd

门禁原理:
    manifest 内嵌 registry 的 sha256（sourceSha256）。kb-gateway 的
    ModuleManifestConsistencyTest 在 CI 中比对哈希 —— 改了 registry 却没跑
    生成器，哈希不匹配，构建失败。

退出码: 0 一致 / 1 存在漂移 / 2 用法或环境错误
"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

SCRIPT_DIR = Path(__file__).resolve().parent
REGISTRY_PATH = SCRIPT_DIR / "module-registry.yml"
MANIFEST_PATH = SCRIPT_DIR / "kb-gateway" / "src" / "main" / "resources" / "module-manifest.json"
GATEWAY_APP_YML = SCRIPT_DIR / "kb-gateway" / "src" / "main" / "resources" / "application.yml"
DOCS_DIR = SCRIPT_DIR / "docs" / "generated"
CLI_PATH = SCRIPT_DIR / "kb-cli.ps1"

MANIFEST_SCHEMA_VERSION = 1
# 网关自身也注册 Nacos，但前端不需要为它显隐菜单；仍纳入期望集，便于发现网关自身异常
CONTEXT_DEFAULT = "/kb"


# ---------------------------------------------------------------- 工具

def die(msg: str, code: int = 2):
    print(f"❌ {msg}")
    sys.exit(code)


def normalize_path(p: str) -> str:
    """把 ${KB_CONTEXT:/kb}/api/x/** 归一化成 /kb/api/x/**"""
    if not isinstance(p, str):
        return ""
    p = re.sub(r"\$\{KB_CONTEXT:[^}]*\}", CONTEXT_DEFAULT, p)
    return p.strip()


def registry_sha256() -> str:
    """注册表内容摘要。

    CRLF 归一到 LF 之后再算：本仓库同时存在 Windows 工作区与 Linux CI 两侧，
    若直接对原始字节取哈希，同一份文件会因换行符不同产出两个哈希，门禁就退化成
    "看你在哪台机器上跑"。.gitattributes 虽然声明了 `*.yml eol=lf`，但这里不依赖它
    ——不确定性必须在计算层消除，而不是靠约定。
    """
    raw = REGISTRY_PATH.read_bytes().replace(b"\r\n", b"\n")
    return hashlib.sha256(raw).hexdigest()


def write_lf(path: Path, text: str) -> None:
    """统一以 LF 写盘。

    生成物必须与 Linux 侧字节一致，否则每次在 Windows 上跑生成器都会把整个文件
    的换行符翻一遍，diff 被噪声淹没，真实改动看不出来。
    """
    path.write_text(text, encoding="utf-8", newline="\n")


def load_registry() -> dict:
    try:
        import yaml  # noqa: PLC0415
    except ImportError:
        die("生成/全量校验需要 PyYAML：pip install pyyaml（--check 不需要）")
    with open(REGISTRY_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


# ---------------------------------------------------------------- 派生：清单

def build_manifest(reg: dict, sha: str) -> dict:
    modules = reg.get("modules") or []
    ctx = (reg.get("global") or {}).get("context-path", "${KB_CONTEXT:/kb}")
    ctx = normalize_path(ctx) if str(ctx).startswith("${") else str(ctx)

    out = []
    for m in modules:
        routes = [normalize_path(r["path"]) for r in (m.get("routes") or []) if r.get("path")]
        out.append({
            "name": m["name"],
            "type": m.get("type", "service"),
            "nacosRegistered": bool(m.get("nacos-registered")),
            "containerPort": m.get("port"),
            "hostPort": m.get("host-port"),
            "healthPath": m.get("health-path"),
            "registryAlias": m.get("registry-alias"),
            "database": m.get("database"),
            "dependsOn": m.get("depends-on") or [],
            "infraDepends": m.get("infra-depends") or [],
            "routes": routes,
            "description": (m.get("description") or "").strip(),
        })
    out.sort(key=lambda x: x["name"])          # 稳定排序，保证可 diff

    return {
        "schemaVersion": MANIFEST_SCHEMA_VERSION,
        "source": "mykng/module-registry.yml",
        "sourceSha256": sha,
        "contextPath": ctx,
        "expectedModules": sorted(x["name"] for x in out if x["nacosRegistered"]),
        "modules": out,
    }


def render_manifest(manifest: dict) -> str:
    return json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=False) + "\n"


# ---------------------------------------------------------------- 派生：文档

def render_contract_md(reg: dict, sha: str) -> str:
    g = reg.get("global") or {}
    infra = reg.get("infrastructure") or []
    modules = reg.get("modules") or []
    plugins = reg.get("plugins") or []
    presets = reg.get("profile-presets") or {}

    L = []
    A = L.append
    A("# mykng 模块契约文档")
    A("")
    A("> 本文件由 `mykng/gen-registry.py` 从 `mykng/module-registry.yml` 自动生成，**请勿手工编辑**。")
    A(f"> 源文件 sha256：`{sha[:16]}…`（与 `module-manifest.json` 的 `sourceSha256` 一致）")
    A(f"> 规模：{len(modules)} 个模块 / {len(infra)} 项基础设施 / {len(plugins)} 个插件")
    A("")
    A("> 修改方式：改 `module-registry.yml` → 跑 `python3 mykng/gen-registry.py` → 提交。")
    A("> 只改前者不跑生成器，CI 的 `ModuleManifestConsistencyTest` 会失败。")
    A("")
    A("---")
    A("")

    # 1. 模块
    A("## 1. 模块总览")
    A("")
    A("| 模块 | 类型 | Nacos 注册 | 容器端口 | 宿主端口 | 数据库 | 描述 |")
    A("|------|------|:---:|:---:|:---:|------|------|")
    for m in modules:
        hp = m.get("host-port")
        A(f"| `{m['name']}` | {m.get('type', '-')} | "
          f"{'✅' if m.get('nacos-registered') else '➖'} | "
          f"{m.get('port', '-')} | {hp if hp is not None else '—'} | "
          f"`{m.get('database', '—')}` | {(m.get('description') or '').strip()} |")
    A("")
    expected = sorted(m["name"] for m in modules if m.get("nacos-registered"))
    A(f"**网关健康探针期望集**（`nacos-registered: true`，共 {len(expected)} 个）："
      + "、".join(f"`{n}`" for n in expected))
    A("")
    A("> 网关 `GET ${KB_CONTEXT}/api/system/modules` 会拿这个期望集与 Nacos 实际注册做比对，")
    A("> 按四态返回：`OK`（期望有+实例有）/ `DOWN`（期望有+曾有实例+现为 0）/")
    A("> `MISSING`（期望有+从未注册，疑似改名漂移）/ `UNEXPECTED`（注册了但不在期望集，野模块）。")
    A("")

    # 2. 路由
    A("## 2. 网关路由")
    A("")
    gw = next((m for m in modules if m["name"] == "kb-gateway"), None)
    if gw and gw.get("routes"):
        A("| 路径 | 目标模块 |")
        A("|------|----------|")
        for r in gw["routes"]:
            A(f"| `{normalize_path(r['path'])}` | `{r['target']}` |")
        A("")
    else:
        A("_registry 未声明网关路由（`modules[kb-gateway].routes`）_")
        A("")

    # 3. 依赖关系
    A("## 3. 模块依赖")
    A("")
    A("| 模块 | 依赖服务 | 依赖基础设施 |")
    A("|------|----------|--------------|")
    for m in modules:
        dep = "、".join(f"`{d}`" for d in (m.get("depends-on") or [])) or "—"
        inf = "、".join(f"`{d}`" for d in (m.get("infra-depends") or [])) or "—"
        A(f"| `{m['name']}` | {dep} | {inf} |")
    A("")

    # 4. 基础设施
    A("## 4. 基础设施")
    A("")
    A("| 名称 | 镜像 | 端口 | Profile | 内存上限 | 描述 |")
    A("|------|------|------|---------|----------|------|")
    for i in infra:
        A(f"| `{i['name']}` | `{i.get('image', '-')}` | {i.get('port', '-')} | "
          f"{i.get('profile', '-')} | {i.get('mem-limit', '-')} | {i.get('description', '')} |")
    A("")

    # 5. 插件
    A("## 5. 插件清单")
    A("")
    A(f"共 {len(plugins)} 个。`enabled-by-default` 决定 `plugins: all` 是否启用。")
    A("")
    A("| 插件 ID | 名称 | 优先级 | 所属模块 | 默认启用 | 可卸载 |")
    A("|---------|------|:---:|----------|:---:|:---:|")
    for p in plugins:
        A(f"| `{p['id']}` | {p.get('name', '-')} | {p.get('priority', '-')} | "
          f"`{p.get('module', '-')}` | {'✅' if p.get('enabled-by-default') else '➖'} | "
          f"{'✅' if p.get('removable') else '❌'} |")
    A("")

    # 6. Profile 预设
    A("## 6. Profile 预设")
    A("")
    for name, cfg in presets.items():
        mods = cfg.get("modules") or []
        infr = cfg.get("infra") or []
        pl = cfg.get("plugins")
        A(f"### {name}")
        A("")
        A(f"- 描述：{cfg.get('description', '-')}")
        A(f"- 模块：{'、'.join(f'`{m}`' for m in mods) or '—'}")
        A(f"- 基础设施：{'、'.join(f'`{i}`' for i in infr) or '—'}")
        A(f"- 插件：{'all（所有 enabled-by-default=true）' if pl == 'all' else ('、'.join(f'`{x}`' for x in (pl or [])) or '—')}")
        A("")

    # 7. 架构图
    A("## 7. 架构图")
    A("")
    A("```mermaid")
    A(render_mermaid(reg))
    A("```")
    A("")
    A("---")
    A("")
    A("*由 `mykng/gen-registry.py` 自动生成，请勿手工编辑。*")
    A("")
    return "\n".join(L)


def render_mermaid(reg: dict) -> str:
    """按注册表派生架构图（不再硬编码模块名与端口）。"""
    infra = reg.get("infrastructure") or []
    modules = reg.get("modules") or []
    by_name = {m["name"]: m for m in modules}

    gw = by_name.get("kb-gateway", {})
    gw_port = gw.get("host-port") or gw.get("port") or "-"
    frontends = [m for m in modules if m.get("type") == "frontend"]
    backends = [m for m in modules if m.get("nacos-registered") and m["name"] != "kb-gateway"]

    def node_id(name: str) -> str:
        return "".join(w.capitalize() for w in re.split(r"[-_]", name))

    L = []
    A = L.append
    A("graph TD")
    A("")
    A("    %% ===== 入口层 =====")
    for fe in frontends:
        hp = fe.get("host-port") or fe.get("port")
        A(f"    {node_id(fe['name'])}[{fe['name']}<br/>:{hp}]")
    A(f"    User[用户] -->|HTTPS| Gateway[kb-gateway<br/>:{gw_port}]")
    for fe in frontends:
        A(f"    Gateway -->|静态资源| {node_id(fe['name'])}")
    A("")

    A("    %% ===== 期望探针集（nacos-registered: true）=====")
    for m in backends:
        p = m.get("port", "-")
        A(f"    Gateway -->|lb://| {node_id(m['name'])}[{m['name']}<br/>:{p}]")
    A("")

    infra_nodes = []
    for i in infra:
        nid = node_id(i["name"])
        infra_nodes.append(nid)
        A(f"    {nid}[({i['name']}<br/>:{i.get('port', '-')})]")
    A("")

    for m in backends:
        for dep in (m.get("infra-depends") or []):
            target = node_id(dep)
            if target in infra_nodes:
                A(f"    {node_id(m['name'])} --> {target}")
    A("")
    A("    %% ===== 服务发现 =====")
    A("    Nacos[(Nacos<br/>:8848)]")
    for m in backends:
        A(f"    {node_id(m['name'])} -.注册.-> Nacos")
    A("    Gateway -.发现.-> Nacos")
    A("")
    A("    %% ===== 样式 =====")
    A("    style Gateway fill:#4f46e5,color:#fff,stroke:#4338ca")
    A("    style User fill:#64748b,color:#fff,stroke:#475569")
    A("    style Nacos fill:#646cff,color:#fff,stroke:#5355d8")
    for m in backends:
        A(f"    style {node_id(m['name'])} fill:#10b981,color:#fff,stroke:#059669")
    A("")
    return "\n".join(L)


# ---------------------------------------------------------------- 门禁：路由漂移

# 结构性路由：由网关自身约定产生，不属于任何业务模块，不纳入 registry 声明范围。
# 排除是显式的、有理由的 —— 不允许静默吞掉其它任何路径。
STRUCTURAL_ROUTE_PATTERNS = (
    (re.compile(r"^/kb/api/[^/]+/v3/api-docs(/|$)"), "各模块 Swagger api-docs 聚合路由（网关按模块约定生成）"),
    (re.compile(r"^/kb/api/[^/]+/swagger-ui(/|$)"), "各模块 Swagger UI 路由（网关按模块约定生成）"),
    (re.compile(r"^/kb/api/\*\*$"), "API 未匹配兜底（404，必须放在具体 API 路由之后、SPA 兜底之前，台账 L027）"),
    (re.compile(r"^/kb/s(/|$)"), "前端静态资源路由（kb-web 内部）"),
    (re.compile(r"^/kb/?$"), "前端 SPA 兜底路由（必须最后匹配）"),
    (re.compile(r"^/kb/\*\*$"), "前端 SPA 兜底路由（必须最后匹配）"),
)


def classify_route(path: str):
    """返回 (是否为结构性路由, 排除理由)。"""
    for pat, why in STRUCTURAL_ROUTE_PATTERNS:
        if pat.match(path):
            return True, why
    return False, ""


def check_route_drift(reg: dict) -> list:
    """registry 声明的业务路由 vs 网关 application.yml 实际谓词，双向比对。"""
    problems = []
    modules = reg.get("modules") or []

    if not GATEWAY_APP_YML.exists():
        return [f"找不到网关配置：{GATEWAY_APP_YML}"]

    yml_text = GATEWAY_APP_YML.read_text(encoding="utf-8")
    # uri: lb://<name> —— 只统计服务发现路由，http:// 的静态路由不参与
    yml_targets = set(re.findall(r"uri:\s*lb://([\w.\-]+)", yml_text))

    # 每条 `- Path=` 谓词可含逗号分隔的多个路径，必须整行取值再拆分
    yml_paths = set()
    for raw in re.findall(r"-\s*Path=([^\n]+)", yml_text):
        for p in raw.split(","):
            p = normalize_path(p.strip())
            if not p:
                continue
            structural, _ = classify_route(p)
            if not structural:
                yml_paths.add(p)

    reg_targets = set()
    reg_paths = set()
    for m in modules:
        for r in (m.get("routes") or []):
            reg_targets.add(r.get("target"))
            reg_paths.add(normalize_path(r.get("path")))

    for t in sorted(yml_targets - reg_targets):
        problems.append(f"网关有 lb://{t} 但 registry 未声明该路由目标")
    for t in sorted(reg_targets - yml_targets):
        problems.append(f"registry 声明了目标 {t} 但网关 application.yml 无对应 lb://{t}")
    for p in sorted(yml_paths - reg_paths):
        problems.append(f"网关有路由 {p} 但 registry 未声明")
    for p in sorted(reg_paths - yml_paths):
        problems.append(f"registry 声明了 {p} 但网关 application.yml 无此谓词")

    # 期望探针集必须全部有路由（除网关自身）
    for name in sorted(m["name"] for m in modules if m.get("nacos-registered")):
        if name == "kb-gateway":
            continue
        if name not in reg_targets:
            problems.append(f"模块 {name} 标记 nacos-registered 但没有任何网关路由")
    return problems


def check_cli_drift(reg: dict) -> list:
    """开发期 CLI 的服务清单 vs registry（台账 T3）。

    kb-cli.ps1 不在任何构建链路上：全仓 grep 能扫到，但不会被编译或 CI 门禁拦截，
    是「改了 registry 却漏了消费方」的典型盲区（此前 5 个模块名与端口表就整体漂移过一次）。
    这里把它的 $MicroServices 清单纳入同一道门禁，避免再次静默漂移。
    """
    problems = []
    if not CLI_PATH.exists():
        return problems  # 文件不存在则跳过，不阻断

    text = CLI_PATH.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"\$MicroServices\s*=\s*@\(([^)]*)\)", text)
    if not m:
        return ["kb-cli.ps1 中未找到 $MicroServices 定义（格式已变，门禁失效需同步）"]

    cli_names = set(re.findall(r"'([^']+)'", m.group(1)))
    # 运行期后端服务 = 仓内服务(type=service) + 独立仓库部署的服务(type=external，如 auth-center)
    reg_services = {mod["name"] for mod in (reg.get("modules") or [])
                    if mod.get("type") in ("service", "external")}

    for n in sorted(cli_names - reg_services):
        problems.append(f"kb-cli.ps1 列出服务 {n}，但 registry 无此 type=service/external 模块")
    for n in sorted(reg_services - cli_names):
        problems.append(f"registry 有服务 {n}，但 kb-cli.ps1 的 $MicroServices 未列出")
    return problems


# ---------------------------------------------------------------- 命令

def cmd_check_full(reg: dict, sha: str) -> int:
    """重新生成并与磁盘逐字节比对。"""
    targets = [
        (MANIFEST_PATH, render_manifest(build_manifest(reg, sha))),
        (DOCS_DIR / "模块契约文档.md", render_contract_md(reg, sha)),
        (DOCS_DIR / "架构图.mmd", render_mermaid(reg) + "\n"),
    ]
    bad = []
    for path, expected in targets:
        if not path.exists():
            bad.append(f"{path.relative_to(SCRIPT_DIR)} 不存在")
        elif path.read_text(encoding="utf-8") != expected:
            bad.append(f"{path.relative_to(SCRIPT_DIR)} 内容与 registry 不一致（需重新生成）")
    problems = check_route_drift(reg)
    for p in problems:
        bad.append(f"路由漂移：{p}")
    for p in check_cli_drift(reg):
        bad.append(f"CLI 漂移：{p}")

    if bad:
        print(f"❌ 检出 {len(bad)} 处漂移：")
        for b in bad:
            print(f"   - {b}")
        print("\n修复：python3 mykng/gen-registry.py")
        return 1
    print("✅ registry 与全部派生产物一致，路由无漂移")
    return 0


def cmd_check() -> int:
    """仅标准库：校验 manifest 的 sourceSha256 是否等于 registry 当前哈希。"""
    if not MANIFEST_PATH.exists():
        print(f"❌ 缺少派生产物：{MANIFEST_PATH.relative_to(SCRIPT_DIR)}")
        print("   修复：python3 mykng/gen-registry.py")
        return 1
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"❌ module-manifest.json 不是合法 JSON：{e}")
        return 1

    actual = registry_sha256()
    recorded = manifest.get("sourceSha256", "")
    if recorded != actual:
        print("❌ registry 与 module-manifest.json 不同步（哈希不匹配）")
        print(f"   registry   sha256 = {actual}")
        print(f"   manifest 记录为    = {recorded or '(缺失)'}")
        print("   说明你改了 module-registry.yml 但没跑生成器。")
        print("   修复：python3 mykng/gen-registry.py")
        return 1

    expected = manifest.get("expectedModules") or []
    if not expected:
        print("❌ module-manifest.json 的 expectedModules 为空")
        return 1
    print(f"✅ 哈希一致，期望探针集 {len(expected)} 个：{'、'.join(expected)}")
    return 0


def cmd_gen(reg: dict, sha: str) -> int:
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    DOCS_DIR.mkdir(parents=True, exist_ok=True)

    manifest_text = render_manifest(build_manifest(reg, sha))
    write_lf(MANIFEST_PATH, manifest_text)
    print(f"✅ {MANIFEST_PATH.relative_to(SCRIPT_DIR)}")

    write_lf(DOCS_DIR / "模块契约文档.md", render_contract_md(reg, sha))
    print(f"✅ {(DOCS_DIR / '模块契约文档.md').relative_to(SCRIPT_DIR)}")

    write_lf(DOCS_DIR / "架构图.mmd", render_mermaid(reg) + "\n")
    print(f"✅ {(DOCS_DIR / '架构图.mmd').relative_to(SCRIPT_DIR)}")

    problems = check_route_drift(reg)
    if problems:
        print(f"\n⚠️  路由漂移 {len(problems)} 处（不阻断生成，但请修正）：")
        for p in problems:
            print(f"   - {p}")
    else:
        print("✅ 路由无漂移")

    cli_problems = check_cli_drift(reg)
    if cli_problems:
        print(f"\n⚠️  CLI 漂移 {len(cli_problems)} 处（不阻断生成，但请修正）：")
        for p in cli_problems:
            print(f"   - {p}")
    else:
        print("✅ kb-cli.ps1 服务清单与 registry 一致")
    print(f"\n源文件 sha256 = {sha}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mykng 注册表生成器 / 漂移门禁")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--check", action="store_true",
                   help="只校验哈希同步（仅标准库，CI 可用）")
    g.add_argument("--check-full", action="store_true",
                   help="重新生成并与磁盘逐字节比对 + 路由漂移检查（需 PyYAML）")
    args = ap.parse_args()

    if not REGISTRY_PATH.exists():
        die(f"找不到注册表：{REGISTRY_PATH}")

    if args.check:
        return cmd_check()

    sha = registry_sha256()
    reg = load_registry()
    if args.check_full:
        return cmd_check_full(reg, sha)
    return cmd_gen(reg, sha)


if __name__ == "__main__":
    sys.exit(main())
