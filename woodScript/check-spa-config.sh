#!/bin/bash
# ============================================================
# check-spa-config.sh - SPA 前端配置一致性门禁（P3）
# ============================================================
# 目的: CI 构建前断言「vite base ↔ 前端 env ↔ deploy 脚本 nginx 参数」
#       三方对齐，杜绝 kb-ops-web 登录 405 这类配置漂移
#       （真实案例: AUTH_BASE_URL 代码默认值 /ops/auth-api 与 nginx
#         location /ops-api/ 不一致，构建期无校验，直到线上浏览器
#         实测才暴露——本脚本让这类 bug 在 CI 就 fail）
# 用法: bash woodScript/check-spa-config.sh [app...]
#       app ∈ {kb-ops-web, infra-monitor-web, portal-web}，缺省全查
# 运行位置: 仓库根（CI build 容器 / 本地均可，纯 bash+grep 无依赖）
# 期望值修改规则: 改 base/api 必须同步改
#       ① vite.config.ts（base / ctx fallback）
#       ② .env.production（VITE_CONTEXT_PATH / API / AUTH）
#       ③ cd/deploy-*.sh 的 render_spa_nginx 参数
#       ④ 本脚本的期望表
# 退出码: 0=全部一致  1=存在漂移（CI 直接 fail）
# ============================================================
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # 仓库根

FAIL=0
ok()  { echo "  ✅ $1 = $2"; }
bad() { echo "  ❌ $1: 期望 [$2] 实际 [$3]"; FAIL=1; }
eq() {
  if [ "$2" = "$3" ]; then ok "$1" "$2"; else bad "$1" "$2" "$3"; fi
}

# 从 env 文件读 KEY=VALUE（容错末尾空白/CR）
env_val() { grep -E "^${2}=" "$1" 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r'; }
# 从 vite.config.ts 提取 CONTEXT_PATH 的代码内 fallback 默认值
vite_fallback() {
  grep -oE "VITE_CONTEXT_PATH[[:space:]]*\|\|[[:space:]]*'[^']+'" "$1" 2>/dev/null \
    | head -1 | grep -oE "'[^']+'" | tr -d "'"
}
# 从 deploy 脚本 render_spa_nginx 行提取 base($4)/api($6)
deploy_base() { grep -h 'render_spa_nginx ' "$1" 2>/dev/null | head -1 | awk -F'"' '{print $4}'; }
deploy_api()  { grep -h 'render_spa_nginx ' "$1" 2>/dev/null | head -1 | awk -F'"' '{print $6}'; }

# ---------- kb-ops-web (/ops + /ops-api) ----------
check_kb_ops_web() {
  echo "── kb-ops-web ─────────────────────────────"
  local ENV="kb-ops/kb-ops-web/.env.production"
  local VITE="kb-ops/kb-ops-web/vite.config.ts"
  local DEPLOY="woodScript/cd/deploy-kb-ops-web.sh"
  local CTX="/ops" API="/ops-api"
  if [ ! -f "$ENV" ]; then bad "env 文件存在" "$ENV" "缺失"; return; fi
  eq "VITE_CONTEXT_PATH"      "$CTX" "$(env_val "$ENV" VITE_CONTEXT_PATH)"
  eq "VITE_API_BASE_URL"      "$API" "$(env_val "$ENV" VITE_API_BASE_URL)"
  eq "VITE_AUTH_BASE_URL"     "$API" "$(env_val "$ENV" VITE_AUTH_BASE_URL)"
  eq "vite ctx 代码fallback"   "$CTX" "$(vite_fallback "$VITE")"
  eq "deploy nginx base"      "$CTX" "$(deploy_base "$DEPLOY")"
  eq "deploy nginx api"       "$API" "$(deploy_api "$DEPLOY")"
}

# ---------- infra-monitor-web (/infra + /infra/api) ----------
check_infra_monitor_web() {
  echo "── infra-monitor-web ──────────────────────"
  local ENV="infra-monitor/infra-monitor-web/.env.production"
  local VITE="infra-monitor/infra-monitor-web/vite.config.ts"
  local DEPLOY="woodScript/cd/deploy-infra-monitor-web.sh"
  local CTX="/infra" API="/infra/api"
  if [ ! -f "$ENV" ]; then bad "env 文件存在" "$ENV" "缺失"; return; fi
  eq "VITE_CONTEXT_PATH"      "$CTX" "$(env_val "$ENV" VITE_CONTEXT_PATH)"
  eq "VITE_API_BASE_URL"      "$API" "$(env_val "$ENV" VITE_API_BASE_URL)"
  eq "VITE_AUTH_BASE_URL"     "$API" "$(env_val "$ENV" VITE_AUTH_BASE_URL)"
  eq "vite ctx 代码fallback"   "$CTX" "$(vite_fallback "$VITE")"
  eq "deploy nginx base"      "$CTX" "$(deploy_base "$DEPLOY")"
  eq "deploy nginx api"       "$API" "$(deploy_api "$DEPLOY")"
}

# ---------- portal-web (/portal + /portal/api, 无 env 文件) ----------
check_portal_web() {
  echo "── portal-web ─────────────────────────────"
  local VITE="portal/vite.config.ts"
  local DEPLOY="woodScript/cd/deploy-portal-web.sh"
  local CTX="/portal" API="/portal/api"
  if [ -f "portal/.env.production" ]; then
    echo "  ⚠️ portal 出现 .env.production — 请把 CONTEXT_PATH/API 校验补进本脚本"
  fi
  # vite base 硬编码（base: '/portal/'）
  eq "vite base(硬编码)" "$CTX/" \
    "$(grep -E '^[[:space:]]*base:' "$VITE" 2>/dev/null | head -1 | grep -oE "'/[^']*'" | tr -d "'")"
  eq "deploy nginx base" "$CTX" "$(deploy_base "$DEPLOY")"
  eq "deploy nginx api"  "$API" "$(deploy_api "$DEPLOY")"
}

# ---------- 主入口 ----------
APPS="${*:-kb-ops-web infra-monitor-web portal-web}"
for a in $APPS; do
  case "$a" in
    kb-ops-web)        check_kb_ops_web ;;
    infra-monitor-web) check_infra_monitor_web ;;
    portal-web)        check_portal_web ;;
    *) echo "❌ 未知 app: $a (可选: kb-ops-web infra-monitor-web portal-web)"; FAIL=1 ;;
  esac
done
echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "✅ SPA 配置一致性校验通过 (vite ↔ env ↔ deploy nginx)"
else
  echo "❌ SPA 配置存在漂移，构建中止 — 按上方 ❌ 项修复后重试"
  echo "   (改 base/api 需同步: vite.config.ts + .env.production + deploy 脚本 + 本脚本期望表)"
fi
exit "$FAIL"
