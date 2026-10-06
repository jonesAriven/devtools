#!/usr/bin/env bash
# =============================================================================
# portal 管理面权限 · 三条 P0 验收脚本（QA 交付）
#
# 用途：本机不具备条件（MySQL 不可达、auth-center 是容器内 DNS），
#      交由有环境的同事执行。只需改顶部三个变量。
#
# 依据：
#   - 验收基线 =当前 bff-whitelist.yml（15 条显式清单 + 边界③④）
#     ⚠️ 不要用 12:19 那版 9 条 —— 它自身有 3 个致命 bug，会让人把「全 401/404」误当正确结果
#   - 判读逻辑含 BE 工程师提醒的关键点：**401 ⇒ 反证 role 闸已放行**
#
# 用法：
#   chmod +x portal_admin_p0.sh
#   ./portal_admin_p0.sh                      # 跑全部
#   ./portal_admin_p0.sh --only case1 case3   # 只跑指定用例
# =============================================================================
set -uo pipefail

# ─────────────── 只需改这里 ───────────────
BASE="http://127.0.0.1:18087"        # portal-server（含 context-path /portal）
# 登录路径：AuthController @PostMapping("/login") + context-path
LOGIN_PASSWORD="${LOGIN_PASSWORD:-}"
[[ -z "$LOGIN_PASSWORD" ]] && { echo "请先 export LOGIN_PASSWORD=xxx"; exit 2; }
# 测试账号（role 必须是中心里的真实角色）
USER_ACCT="${USER_ACCT:-zhangsan}"        # role=user
ADMIN_SSO_ACCT="${ADMIN_SSO_ACCT:-}"       # 中心超管，走 SSO（需人工完成）
ADMIN_PWD_ACCT="${ADMIN_PWD_ACCT:-}"# 中心 admin，走账密
SUPERADMIN_ACCT="${SUPERADMIN_ACCT:-}"   # 中心超管（用例 4）

# ✅ 方案 A 已落地（team-lead 裁决 2026-10-06，BE 已实现并 VerifyBff 56/56 静态全绿）
#   实际三闸顺序（PortalAdminGateInterceptor.preHandle）：
#     ① 本地 role ∈ {admin, superadmin}   不过⇒ 403   （无需重授权，语义正确）
#     ② hasSsoSession(userId) 前置          无  ⇒ 401   （场景③·账密/邮箱码）
#     ③ hasPermission(bearer,["api:admin"],ANY) 否 ⇒ 401   （场景②·中心不可达/权限点被回收）
#     superadmin 豁免在 PortalPermissionChecker:92-96 内，本类不重复实现
#
# 🔴 场景②与③【都是 401，状态码无法区分】—— 只能靠日志：
#     场景③ WARN "[管理面闸门] 拒绝（场景③·无 SSO 会话，请用统一认证登录）"
#     场景② WARN "[管理面闸门] 拒绝（场景②·中心不可达或权限点 api:admin 已被回收）"
#   ⇒ 排障必须同时看服务端日志，不能只看 HTTP 码。
#
#   注：VerifyBff 的 6 条新契约是【静态断言】（反射读字段 + 读源码判顺序/状态码），
#       能防「删掉第②道/顺序改反/401 改 403」，但**不能替代真机三态**。
GATE_MODE="${GATE_MODE:-dual-gate-with-sso-check}"
# ───────────────────────────────────────────

ADMIN_API="${BASE}/portal/api/admin/users?client=marschat-portal"
COOKIE_JAR="$(mktemp -t portal_p0.XXXXXX)"
trap 'rm -f "$COOKIE_JAR"' EXIT

pass=0; fail=0; blocked=0
declare -a RESULTS=()

hr(){ printf '=%.0s' {1..78}; echo; }

record(){ # name expected actual note
  local name="$1" exp="$2" act="$3" note="${4:-}"
  local verdict
  if [[ "$act" == "$exp" ]]; then verdict="PASS"; pass=$((pass+1))
  elif [[ "$act" == "BLOCKED" ]]; then verdict="BLOCKED"; blocked=$((blocked+1))
  else verdict="FAIL"; fail=$((fail+1)); fi
  RESULTS+=("$(printf '%-8s %-46s 期望=%-4s 实际=%-6s %s' "$verdict" "$name" "$exp" "$act" "$note")")
  printf '  [%s] %s  期望=%s 实际=%s %s\n' "$verdict" "$name" "$exp" "$act" "$note"
}

# 取 token（账密登录）。portal 自己的 /portal/api/auth/login
login_pwd(){
  local acct="$1"
  curl -sS -m 20 -X POST "${BASE}/portal/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${acct}\",\"password\":\"${LOGIN_PASSWORD}\"}" \
    -c "$COOKIE_JAR" 2>/dev/null
}

# 从登录响应取 Authorization（portal 自签 HS256，浏览器侧放 Bearer）
extract_bearer(){
  python3 -c 'import sys,json
d=json.load(sys.stdin)
print(d.get("data",{}).get("token",""))' 2>/dev/null
}

probe(){ # bearer_url label
  curl -sS -m 20 -o ./_p0_body.tmp -w '%{http_code}' \
    -H "Authorization: Bearer $2" "$1" 2>/dev/null || echo "000"
}

hr; echo "portal 管理面权限 P0 验收"; hr
echo "目标: $ADMIN_API"
echo

# ─────────────────────────────────────────────────────────────
# 用例 1：role=user → 期望 403
#   🔴 这是唯一能暴露越权的路径。
#   ⚠️ 不要用「未登录」代替 —— JwtInterceptor 会正常 401，
#      那样会「通过」并掩盖越权缺陷。
# ─────────────────────────────────────────────────────────────
echo "[用例 1] role=user 访问管理面 —— 期望 403（唯一能暴露越权的路径）"
if [[ -z "$USER_ACCT" ]]; then
  record "case1 role=user → 403" "403" "BLOCKED" "未设 USER_ACCT"
else
  resp="$(login_pwd "$USER_ACCT" 2>/dev/null || true)"
  tok="$(printf '%s' "$resp" | extract_bearer)"
  if [[ -z "$tok" ]]; then
    # 登录失败通常就是「该账号在中心无记录」，这本身是有效的边界证据
    record "case1 role=user → 403" "403" "BLOCKED" "登录失败（账号在中心不存在?）: $(printf '%s' "$resp" | head -c 120)"
  else
    code="$(probe "$ADMIN_API" "$tok")"
    note=""
    [[ "$code" == "401" ]] && note="注意：401 而非 403 ⇒ role 闸已放行，需查该账号 role"
    [[ "$code" == "200" ]] && note="🔴 越权！普通用户竟能读管理面"
    record "case1 role=user → 403" "403" "$code" "$note"
  fi
fi
echo

# ─────────────────────────────────────────────────────────────
# 用例 2：role=admin（SSO） → 期望 200
#   🔴 这条最关键：它同时验证「resolver 身份透传」与「role 闸门」两件事。
#   ⚠️ 若它是 403/401 而用例 3 是 200，说明凭据链只覆盖了账密。
#   ⚠️ 若它 401：resolver 返回 null（无 refresh_token）⇒ 不是 role 问题。
# ─────────────────────────────────────────────────────────────
echo "[用例 2] role=admin（SSO 换票） → 期望 200（同时验证透传+ role 闸）"
if [[ -z "$ADMIN_SSO_ACCT" ]]; then
  record "case2 admin(SSO) → 200" "200" "BLOCKED" \
    "需人工完成 SSO 后取 token（SSO 有 state/redirect，脚本无法自动化）"
  echo "  ↳ 手工步骤："
  echo "     1) 浏览器登录 $BASE/portal/login"
  echo "     2) DevTools → Application → Local Storage → portal_token"
  echo "     3) 复制该值填入 \$ADMIN_SSO_TOKEN 后重跑"
else
  code="$(probe "$ADMIN_API" "${ADMIN_SSO_TOKEN:-}")"
  note=""
  [[ "$code" == "401" ]] && note="resolver 返回 null（无 refresh_token），非 role 问题"
  [[ "$code" == "403" ]] && note="role 闸拒绝 ⇒ 该账号 role 不是 admin/superadmin"
  record "case2 admin(SSO) → 200" "200" "$code" "$note"
fi
echo

# ─────────────────────────────────────────────────────────────
# 用例 3：role=admin（账密） → 期望 401
#   ✅ 这是**预期行为，不是缺陷**（已定性为「边界一致」）。
#   原理：账密/邮箱码走 portal-server BFF 换票，浏览器侧无 SAS refresh_token，
#        中心 /auth/login 与 /auth/mail-login 返回 legacy token，不入 SSO 会话池。
#   🔴 判读关键（BE 工程师提醒）：**401 恰好反证 role 闸已放行过了** ——
#      401 来自 resolveCenterToken 返回 null；403 才来自 PortalAdminGateInterceptor。
#      若这里看到 403 ⇒ role 闸没放行，与本用例的预期不同，需查 role 来源。
# ─────────────────────────────────────────────────────────────
case3_expect=401
case3_note="预期行为·边界一致，非缺陷"
if [[ "$GATE_MODE" == "dual-gate" ]]; then
  case3_expect=403
  case3_note="双闸下被中心闸拦（预期）"
elif [[ "$GATE_MODE" == "dual-gate-with-sso-check" ]]; then
  case3_expect=401
  case3_note="双闸但先查 hasSsoSession ⇒ 仍 401（保留重授权引导）"
fi
echo "[用例 3] role=admin（账密） → 期望 ${case3_expect}（${case3_note}）[GATE_MODE=${GATE_MODE}]"
if [[ -z "${ADMIN_PWD_ACCT:-}" ]]; then
  record "case3 admin(账密) → 401" "401" "BLOCKED" "未设 ADMIN_PWD_ACCT"
else
  resp="$(login_pwd "$ADMIN_PWD_ACCT" 2>/dev/null || true)"
  tok="$(printf '%s' "$resp" | extract_bearer)"
  if [[ -z "$tok" ]]; then
    record "case3 admin(账密) → ${case3_expect}" "$case3_expect" "BLOCKED" "登录失败: $(printf '%s' "$resp" | head -c 120)"
  else
    code="$(probe "$ADMIN_API" "$tok")"
    note="${case3_note}"
    if [[ "$code" == "401" ]]; then
      note="${case3_note} ⚠️401 与场景②同码，请查服务端日志区分：场景③='无SSO会话' / 场景②='中心不可达或权限点被回收'"
    fi
    if [[ "$case3_expect" == "401" && "$code" == "403" ]]; then
      note="⚠️ 得 403 而非 401 ⇒ GATE_MODE 设定与实现不符（若为 single-gate 说明 role 闸未放行）"
    elif [[ "$case3_expect" == "403" && "$code" == "401" ]]; then
      note="⚠️ 得 401 而非 403 ⇒ 若为 dual-gate 说明中心闸未生效"
    elif [[ "$code" == "200" ]]; then
      note="🔴 非预期：账密 admin 竟能读管理面"
    fi
    record "case3 admin(账密) → ${case3_expect}" "$case3_expect" "$code" "$note"
  fi
fi
echo

# ─────────────────────────────────────────────────────────────
# 用例 4：superadmin → 期望 200
#   🔴 双闸下唯一必须恒放行的路径。
#   PortalPermissionChecker:92-96 `if ("superadmin".equals(role)) return true;`
#   —— 若本条 403，说明该恒放行分支被破坏（这正是当初「怕丢 superadmin 兼容」的原因）。
# ─────────────────────────────────────────────────────────────
echo "[用例 4] superadmin → 期望 200（双闸下唯一必须恒放行）"
if [[ -z "${SUPERADMIN_ACCT:-}" ]]; then
  record "case4 superadmin → 200" "200" "BLOCKED" "未设 SUPERADMIN_ACCT"
else
  resp="$(login_pwd "$SUPERADMIN_ACCT" 2>/dev/null || true)"
  tok="$(printf '%s' "$resp" | extract_bearer)"
  if [[ -z "$tok" ]]; then
    record "case4 superadmin → 200" "200" "BLOCKED" "登录失败（需走SSO，见用例 2 手工步骤）"
  else
    code="$(probe "$ADMIN_API" "$tok")"
    note="PortalPermissionChecker 应恒放行"
    [[ "$code" != "200" ]] && note="🔴 superadmin 未放行 ⇒恒放行分支被破坏"
    record "case4 superadmin → 200" "200" "$code" "$note"
  fi
fi
echo

# ─────────────────────────────────────────────────────────────
# 附加：白名单外端点应 404（默认拒绝）
# ─────────────────────────────────────────────────────────────
echo "[附加] 白名单外写端点 → 期望 404（默认拒绝）"
if [[ -n "${ADMIN_PWD_ACCT:-}" ]]; then
  resp="$(login_pwd "$ADMIN_PWD_ACCT" 2>/dev/null || true)"
  tok="$(printf '%s' "$resp" | extract_bearer)"
  if [[ -n "$tok" ]]; then
    code="$(curl -sS -m 20 -o /dev/null -w '%{http_code}' -X POST \
      -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d '{}' "${BASE}/portal/api/admin/nonexistent-endpoint-probe" 2>/dev/null || echo 000)"
    record "附加 白名单外 → 404" "404" "$code" "验证默认拒绝"
  fi
fi
echo

hr
echo "汇总"
hr
for r in "${RESULTS[@]}"; do echo "  $r"; done
hr
echo "方案 A 期望值（GATE_MODE=${GATE_MODE}）："
echo "  role=user                 → 403   场景①·非管理员"
echo "  admin(账密/邮箱码)        → ${case3_expect}   场景③·无 SSO 会话（401 可自助恢复）"
echo "  admin(SSO) 有权限         → 200"
echo "  superadmin                → 200   恒放行（2026-09-13 事故点）"
echo "  中心不可达 + SSO 管理员→ 401   场景②（与③同码，靠日志区分）"
echo "  白名单外端点              → 404   默认拒绝"
hr
printf 'PASS=%d  FAIL=%d  BLOCKED=%d\n' "$pass" "$fail" "$blocked"
hr

# ── 场景②/③ 只能靠日志区分（状态码都是 401）────────────────────
if [[ "${LOG_GUIDE:-1}" == "1" ]]; then
  echo
  echo "─── 401 的场景判读（务必同时看 portal-server 日志）───"
  echo "  日志含「场景③·无 SSO 会话」   ⇒ 该用户无 SSO 会话（应改用统一认证登录）"
  echo "  日志含「场景②·中心不可达」     ⇒ 中心故障或 api:admin 权限点被回收"
  echo "  两者状态码都是 401，仅靠 HTTP 码无法区分"
  echo
fi

if [[ "$fail" -gt 0 ]]; then
  echo "有FAIL —— 请把上面的实际状态码回给 QA（software-qa-engineer）。"
  exit 1
fi
if [[ "$blocked" -gt 0 ]]; then
  echo "有 BLOCKED —— 需补齐环境/账号后重跑；**不要把 BLOCKED 当PASS**。"
  exit 2
fi
echo "三条 P0 全部符合预期。"
