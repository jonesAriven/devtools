/**
 * portal 的 SSO 接入（Phase 6 紧密型接入）
 *
 * ⚠️ portal 与 kb-web 那类应用**不同**：portal 是 **OIDC 机密客户端**
 * （`client_authentication_methods=client_secret_basic`），换票在服务端做 ——
 * 前端只把 `code` 交给 portal-server，由它带 client_secret 去换 token 并建立会话。
 *
 * 所以这里**不能**直接用组件的 `silentSignIn` / `handleCallback`（那会绕过 portal-server，
 * 变成"浏览器直换票"，与 portal 的会话模型冲突）。本文件的做法是：
 * - 复用组件的**会话探针**（`probeIdpSession`）：判断"浏览器是否已有 IdP 会话"是纯前端问题，
 *   与换票模式无关；
 * - 探到会话后，跳**portal 自己的服务端授权入口** `/portal/api/auth/sso/authorize`；
 * - 统一登出（SLO）用组件的 `ssoLogout` —— 它是标准 OIDC RP-Initiated Logout，两条路径通用。
 */
import {
  createSsoClient,
  type SsoConfig,
  type SloOptions,
  type SessionWatcher,
  type SessionWatcherOptions,
} from '@marschat/auth-components'

/** portal 登录页地址 —— 统一登出回跳地址（必须落在 auth-center 的 post_logout 白名单内） */
export const PORTAL_LOGIN_URL = `${window.location.origin}/portal/login`

/** portal-server 的 SSO 授权入口（服务端流） */
export const PORTAL_AUTHORIZE_PATH = '/portal/api/auth/sso/authorize'

export const SSO_CONFIG: SsoConfig = {
  issuer: 'https://auth.marschat.online',
  clientId: 'marschat-portal',
  redirectUri: `${window.location.origin}/portal/auth/callback`,
  scope: 'openid profile',
  loginUrl: PORTAL_LOGIN_URL,
  silentLogin: true,
}

/** 绑定好配置的客户端（SLO / 探针等通用能力走它） */
export const sso = createSsoClient(SSO_CONFIG)

/** 服务端授权入口 URL（`redirect` 交给 portal-server 处理） */
export function bffAuthorizeUrl(redirect?: string): string {
  const target = redirect || window.location.origin
  return `${PORTAL_AUTHORIZE_PATH}?redirect=${encodeURIComponent(target)}`
}

/**
 * 登录页静默免登（BFF 版）
 *
 * @returns `true` 表示已发起跳转（调用方不用再渲染登录框）；`false` 表示无 IdP 会话，正常显示登录框。
 *
 * ⚠️ 探针异常一律按"无会话"处理，认证中心抖动不能把登录页打成白屏。
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
 */
let sessionWatcher: SessionWatcher | null = null

/**
 * 启动**会话监视**（幂等）—— Phase 6「单点登出跨应用联动」。
 *
 * ⚠️ 与换票模式无关：探针只回答"浏览器在 auth-center 侧还有没有 IdP 会话"，
 * 因此 BFF 机密客户端（portal）与浏览器直换票应用可以复用同一实现。
 *
 * SAS 1.x 不实现 back-channel logout，别处登出后本应用本地 token 不会自动失效。
 * 监视器在「页面切回可见 / 获焦 / 定时（默认 60s）」时探一次 `/auth/session`，
 * **只有确认会话已消失**才清本地并跳登录页；探针异常一律保持现状（fail-safe）。
 */
export function startSessionWatcher(options?: SessionWatcherOptions): SessionWatcher {
  if (sessionWatcher) return sessionWatcher
  sessionWatcher = sso.watchSession(options)
  return sessionWatcher
}

/** 停止会话监视（登出前调用） */
export function stopSessionWatcher(): void {
  sessionWatcher?.stop()
  sessionWatcher = null
}

/** 统一登出（SLO）：先停监视 → 销毁 IdP 会话 + 清本地，然后回跳登录页 */
export function logout(options: SloOptions = {}): void {
  stopSessionWatcher()
  sso.logout(options)
}

// ⚠️ 这里刻意**不用** `export { probeIdpSession, ssoLogout, ... }` 直接再导出组件裸函数：
// 组件裸函数签名是 `(config, ...)`，而本仓调用点用的是绑定式 `(redirect/options)`，
// 直接再导出会把 redirect 字符串当成 config 传进去 → client_id=undefined → 功能静默失效。
// 统一包一层绑定配置（2026-09-11 复查修复）。

/** 会话探针：当前浏览器是否已有有效 IdP 会话 */
export const probeIdpSession = (opts?: { timeoutMs?: number }) => sso.probeSession(opts)

/** 仅构建登出 URL（需要自己控制跳转时机时用） */
export const buildSloUrl = (options?: SloOptions) => sso.buildLogoutUrl(options)

/** 仅清本地凭据，不碰 IdP 会话 */
export const clearLocalAuth = () => sso.clearLocalAuth()

/** 统一登出（SLO）——绑定配置版，等价于 `logout()`（同样会先停会话监视） */
export const ssoLogout = (options?: SloOptions) => {
  stopSessionWatcher()
  sso.logout(options)
}
