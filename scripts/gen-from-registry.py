# -*- coding: utf-8 -*-
"""
gen-from-registry.py —— Phase 3 配置化接入生成器（R7=Python）

从单一真源 apps-registry.yml 计算派生产物并输出到 **stdout**（写入由调用方重定向完成，
本脚本不做任何文件写操作）：

  # ① auth-center L1 种子（重定向到 <auth-center>/src/main/resources/clients.yml）
  python scripts/gen-from-registry.py clients

  # ② 某前端运行时配置（重定向到 <前端>/public/app-config.json）
  python scripts/gen-from-registry.py appconfig --client marschat-kbops

  # ③ 全量校验（不产出）
  python scripts/gen-from-registry.py check

⚠️ 派生产物首行含 AUTO-GENERATED 标记，禁止手改；改 apps-registry.yml 后重新生成。
"""
import argparse
import json
import os
import sys

import yaml

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REGISTRY = os.path.join(ROOT, "apps-registry.yml")

AUTO_TAG = "# AUTO-GENERATED from apps-registry.yml by scripts/gen-from-registry.py — DO NOT EDIT"

DEFAULTS = {
    "scopes": ["openid", "profile"],
    "grant-types": ["authorization_code", "refresh_token"],
    "access-token-ttl-minutes": 30,
    "refresh-token-ttl-days": 7,
    "reuse-refresh-tokens": False,
    "require-consent": False,
}


def load_registry():
    with open(REGISTRY, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    apps = data.get("apps") or []
    ids = [a["client-id"] for a in apps]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        raise SystemExit("client-id 重复: %s" % dup)
    for a in apps:
        if a.get("type") == "confidential" and not a.get("secret"):
            raise SystemExit("confidential 客户端缺 secret: %s" % a["client-id"])
        auth = a.get("auth") or {}
        if not auth.get("redirect-uris"):
            raise SystemExit("缺 redirect-uris: %s" % a["client-id"])
    return data


def merged_auth(data, a):
    auth = dict(DEFAULTS)
    auth.update(data.get("defaults", {}).get("auth") or {})
    auth.update(a.get("auth") or {})
    return auth


def emit_clients(data):
    print(AUTO_TAG)
    print("clients:")
    for a in data["apps"]:
        auth = merged_auth(data, a)
        ctype = a.get("type", "public")
        print("  - client-id: %s" % a["client-id"])
        print("    name: %s" % a.get("name", a["client-id"]))
        print("    type: %s" % ctype)
        if ctype == "confidential":
            print("    secret: %s" % a["secret"])
        print("    scopes: %s" % json.dumps(auth["scopes"]))
        print("    grant-types: %s" % json.dumps(auth["grant-types"]))
        print("    access-token-ttl-minutes: %s" % auth["access-token-ttl-minutes"])
        print("    refresh-token-ttl-days: %s" % auth["refresh-token-ttl-days"])
        print("    reuse-refresh-tokens: %s" % str(auth["reuse-refresh-tokens"]).lower())
        print("    require-consent: %s" % str(auth["require-consent"]).lower())
        print("    redirect-uris:")
        for u in auth["redirect-uris"]:
            print("      - %s" % u)
        print("    post-logout-redirect-uris:")
        for u in auth.get("post-logout-redirect-uris", []):
            print("      - %s" % u)


def emit_appconfig(data, client_id):
    app = next((a for a in data["apps"] if a["client-id"] == client_id), None)
    if not app:
        raise SystemExit("registry 中无此 client: %s" % client_id)
    fe = app.get("frontend") or {}
    cfg = {
        "clientId": app["client-id"],
        "issuer": "https://auth.marschat.online",
        "entry": fe.get("entry"),
        "contextPath": fe.get("context-path", "/"),
        "apiBase": fe.get("api-base"),
        "authApiBase": fe.get("auth-api-base"),
    }
    cfg = {k: v for k, v in cfg.items() if v is not None}
    print("// AUTO-GENERATED from apps-registry.yml — DO NOT EDIT")
    print(json.dumps(cfg, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("clients", help="输出 auth-center clients.yml（stdout）")
    p_app = sub.add_parser("appconfig", help="输出某前端 app-config.json（stdout）")
    p_app.add_argument("--client", required=True)
    sub.add_parser("check", help="仅校验 registry")
    args = parser.parse_args()

    data = load_registry()
    if args.cmd == "check":
        print("registry OK: %d apps" % len(data["apps"]))
        for a in data["apps"]:
            print("-", a["client-id"], "(%s)" % a.get("type", "public"))
    elif args.cmd == "clients":
        emit_clients(data)
    elif args.cmd == "appconfig":
        emit_appconfig(data, args.client)


if __name__ == "__main__":
    main()
