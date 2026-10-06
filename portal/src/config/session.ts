/**
 * portal 的会话能力（BFF 机密客户端形态）—— 由 `utils/sso.ts` 迁入
 *
 * ⚠️ portal 与 kb-web 那类应用**根本不同**：portal 是 **OIDC 机密客户端**
 *（`client_authentication_methods=client_secret_basic`），换票在服务端做 ——
 * 前端只把 `code` 交给 portal-server，由它带 client_secret 去换 token 并建立会话。
 *
 * 所以这里**不能**用组件的 `silentSignIn` / `handleCallback` / `renew`（那会绕过 portal-server，
 * 变成「浏览器直换票」，与 portal 的会话模型冲突）。本模块的��法：
 * - 复用组件的**会话探针**（`probeSession`）：判断「浏览器是否已有 IdP 会话」是纯前端问题，
 *   与换票模式无关；
 * - 探到会话后，跳**portal 自己的服务端授权入口** `/portal/api/auth/sso/authorize`；
 * - 统一登出（SLO）用组件的 `sso.logout()` —— 标准 OIDC RP-Initiated Logout，两条路径通用。
 *
 * 迁移说明：Phase 13 删除了 `utils/sso.ts`（168 行转发壳），能力平移至此。
 * 与 `config/runtime.ts` 分开是因为本模块需要 `sso` 客户端与续期单飞状态，
 * 而 `config/runtime.ts` 必须保持零依赖（它被 `marschat.ts` 引用）。
 */
import { createSsoClient, type SsoConfig, type SloOptions, type SessionWatcher, type SessionWatcherOptions } from '@marschat/auth-components'
import { CONTEXT_PATH, OIDC_ISSUER, OIDC_CLIENT_ID, OIDC_REDIRECT_URI, API_BASE_URL } from '@/config/runtime'

/** portal 登录页地址 —— 统一登出回跳地址（必须落在 auth-center 的 post_logout 白名单内） */
export const PORTAL_LOGIN_URL = `${window.location.origin}${CONTEXT_PATH}/login`

/** portal-server 的 SSO 授权入口（服务端流） */
export const PORTAL_AUTHORIZE_PATH = `${API_BASE_URL}/auth/sso/authorize`

export const SSO_CONFIG: SsoConfig = {
  issuer: OIDC_ISSUER,
  clientId: OIDC_CLIENT_ID,
  redirectUri: OIDC_REDIRECT_URI,
  scope: 'openid profile',
  loginUrl: PORTAL_LOGIN_URL,
  silentLogin: true,
}

/** 绑定好配置的客户端（SLO / 探针等通用能力走它） */
export const sso = createSsoClient(SSO_CONFIG)

/**
 * 权限点全码 `<client_id>:<type>:<code>`。
 *
 * ⚠️ 权限点 code 约定（依据 auth-components hasPermission 实现实测）：
 * 含 `:` 的 code 会被**原样使用**，不含才自动补 `<client_id>:` 前缀；
 * 而 `useMenus` 判定的码是 `<client_id>:menu:<key>`。
 * 所以路由 `meta.perm` / `PermissionGate` 的 `perm` **必须传全码**，禁止手写半码。
 */
export function permCode(type: 'menu' | 'api', code: string): string {
  return `${SSO_CONFIG.clientId}:${type}:${code}`
}

/**
 * 权限语境（菜单 / 路由守卫 / PermissionGate **三层同源共用一份**）。
 *
 * 🔴 `issuer` 必须是 **portal-server 同源代理**（`/portal/api`）而不是 auth-center：
 * portal 是机密客户端，浏览器里只有 portal-server 自签的 `portal_token`（hutool HS256），
 * **不是 auth-center 签发的 token**。直连 auth-center 必然 401 → 永远 `configured=false`
 * → 权限体系静默全放行（菜单全出来、按钮全可点，且不报任何错）。
 * portal-server 用该用户自己的 auth-center 身份转发到 auth-center。
 */
export const permOptions = {
  issuer: API_BASE_URL,
  clientId: SSO_CONFIG.clientId,
}

/** 服务端授权入口 URL（`redirect` 交给 portal-server 处理）。
 *  站内相对路径（如 `/`、`/users`）在此补全为绝对 URL —— 后端 normalizeOrigin
 *  按 origin 校验白名单，相对路径会被拒「不允许的回调地址」（2026-09-12 实测）。 */
export function bffAuthorizeUrl(redirect?: string): string {
  let target = redirect || window.location.origin
  if (target.startsWith('/')) {
    target = window.location.origin + target
  }
  return `${PORTAL_AUTHORIZE_PATH}?redirect=${encodeURIComponent(target)}`
}

/**
 * 登录页静默免登（BFF 版）
 *
 * @returns `true` 表示已发起跳转（调用方不用再渲染登录框）；`false` 表示无IdP 会话，正常显示登录框。
 *
 * ⚠️ 探针异常一律按「无会话」处理，认证中心抖动不能把登录页打成白屏。
 */
export async function bootstrapLoginPage(redirect?: string): Promise<boolean> {
  if (SSO_CONFIG.silentLogin === false) return false
  const probe = await sso.probeSession()
  if (!probe.authenticated) return false
  window.location.href = bffAuthorizeUrl(redirect)
  return true
}

/**
 * 会话监视器句柄（应用内**单例**）—— 避免重复创建挂上多份定时器/监听器。
 *
 * ⚠️ Phase 13：装配层传了 `watchSession: false`，监视器由`main.ts` 按
 *    `isOidcToken()` 分流后**在此启动** —— 判据与应用特有语义（`getLocalIdentity` /
 *    `onIdentityMismatch`）都必须在应用侧，装配层硬编码项对 BFF 机密客户端不适用。
 */
let sessionWatcher: SessionWatcher | null = null

/** 启动**会话监视**（幂等）—— Phase 6「单点登出跨应用联动」。 */
export function startSessionWatcher(options?: SessionWatcherOptions): SessionWatcher {
  if (sessionWatcher) return sessionWatcher
  sessionWatcher = sso.watchSession(options)
  return sessionWatcher
}

/** 停止会话监视（登出前调用，避免登出跳转途中被二次判定） */
export function stopSessionWatcher(): void {
  sessionWatcher?.stop()
  sessionWatcher = null
}

/** 统一登出（SLO）：先停监视 → 销毁 IdP 会话 + 清本地，然后回跳登录页 */
export function logout(options: SloOptions = {}): void {
  stopSessionWatcher()
  sso.logout(options)
}

// ==========================================================================
// D-1（2026-09-18）：401 续期语义按会话渠道分流 —— 续期单飞 + 统一续期动作
//
// 背景：portal 存在**两条**会导航到 IdP 重授权的路径 ——
//   A. 身份一致性守卫（main.ts onIdentityMismatch）
//   B. 401 续期（api/request.ts 错误分支）
// 二者共用本模块的 `renewOidcSession()` 与**同一个** `reauthInFlight` 单飞标志，
// 从而保证同一文档生命周期内**至多一次续期导航**（互斥、幂等、不叠加跳转）。
// ==========================================================================

/** 续期单飞标志（模块级）——「身份一致性守卫」与「401 续期」**共用**。 */
let reauthInFlight = false

/** 是否已有续期在途（供调用方在导航前短路，避免重复跳转） */
export const isReauthInFlight = () => reauthInFlight

/**
 * 统一的 OIDC 续期动作（静默重授权）。两条续期入口都必须走它。
 *
 * ⚠️ portal 是 **BFF 服务端流**（机密客户端），redirect 的语义与 infra-monitor / kb-ops
 *    那类「浏览器直换票」**不同** —— 2026-09-18 复核 SsoController + SsoCallbackView 确认：
 *    - `?redirect=` 交给 portal-server，`SsoController#normalizeOrigin` 只取**裸origin** 做
 *      白名单校验，**路径部分一律丢弃**（带路径会拼出 /portal/portal/auth/callback 被 SAS 拒）；
 *    - `SsoCallbackView` 固定 `router.replace('/')`，**不读** redirect。
 *    ⇒ ① 续期后**一律回首页**，无法保留当前页（infra/kb-ops 能保留路径，portal 不能）；
 *      ② 传 origin 即可，精心构造 SPA 路径在 portal 上是死代码。
 *      **不要**照搬 infra/kb-ops 的 `currentSpaPath()` 口径。
 *
 * @param redirect 回跳地址（只有 origin 生效，路径会被后端丢弃；缺省即当前 origin）
 * @returns true = 已发起导航（调用方不要再做别的跳转）；
 *          false = 未导航（IdP 会话已不在 / 探针异常）——调用方自行降级
 */
export async function renewOidcSession(redirect?: string): Promise<boolean> {
  if (reauthInFlight) return true          // 已有续期在途：视为「已处理」
  reauthInFlight = true
  try {
    const navigated = await bootstrapLoginPage(redirect)
    if (!navigated) reauthInFlight = false                  // ★ 未导航 → 必须复位（否则之后永久静默失效）
    return navigated
  } catch (_e) {
    reauthInFlight = false                                  // ★ 探针异常 → 复位，交调用方降级
    return false
  }
}
