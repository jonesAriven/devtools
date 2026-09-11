/**
 * SSO（OIDC authorization_code + PKCE）—— **薄适配层**（Phase 6 组件收敛）
 *
 * 实现已全部收敛到公共组件 `@marschat/auth-components`；本文件只做两件事：
 * 1. 绑定 kb-web 自己的 OIDC 配置（client_id=marscherch-kbweb，回调 /kb/sso-callback）；
 * 2. 保持既有导出名与调用签名不变，上层（LoginView / SsoCallbackView / api/index.ts）零改动。
 *
 * ⚠️ 本文件**不再**自己实现 PKCE / 换票 / 续期逻辑。此前 kb-web、kb-ops-web、
 * infra-monitor-web 各抄一份 150+ 行，activecode 还有一份 223 行自研版 —— 改一处要改四处。
 *
 * 新增能力（组件提供，本文件透传）：
 * - `bootstrapLoginPage()` 静默免登：已有 IdP 会话则直接进，不再弹登录框
 * - `ssoLogout()` 统一登出（SLO）：销毁 IdP 会话，不只是清本地
 * - `renewByReauthorize()` 静默续期：public client 拿不到 refresh_token，改为静默重授权
 */
import { createSsoClient, type SsoConfig, type SloOptions } from '@marschat/auth-components'
import { CONTEXT_PATH, OIDC_CLIENT_ID, OIDC_ISSUER, OIDC_REDIRECT_URI } from '@/config'

/** kb-web 的 SSO 配置（唯一真源，供登录页/回调页/登出共用） */
export const SSO_CONFIG: SsoConfig = {
  issuer: OIDC_ISSUER,
  clientId: OIDC_CLIENT_ID,
  redirectUri: OIDC_REDIRECT_URI,
  scope: 'openid profile',
  /** 登录页地址 —— 既是统一登出的回跳地址，也是"没有 IdP 会话"时的落点 */
  loginUrl: `${window.location.origin}${CONTEXT_PATH}/login`,
  /** 进入登录页时自动探测 IdP 会话，有则免密进入 */
  silentLogin: true,
}

/** 绑定好配置的客户端，新代码建议直接用 `sso.xxx()` */
export const sso = createSsoClient(SSO_CONFIG)

// ===== 以下为兼容旧调用点的转发（签名与收敛前一致） =====

/** 发起 SSO 登录（跳授权端点，不返回） */
export const startSsoLogin = (redirect = '/dashboard') => sso.login(redirect)

/** 回调页处理：校验 state → 换票 → 落库 → 返回站内目标路径 */
export const handleSsoCallback = (query?: URLSearchParams) => sso.handleCallback(query)

/**
 * @deprecated 请直接用 `renewByReauthorize`。保留此名只为不破坏既有 import。
 * 行为已改为「静默重授权」（public client 没有 refresh_token 可刷）。
 */
export const refreshOidcToken = () => sso.renew()

// ===== 以下是**已绑定配置**的转发（调用方签名与收敛前一致：第一个参数是 redirect，不是 config） =====
//
// ⚠️ 血泪坑（2026-09-11 复查发现并修复）：
// 这里**不能**写成 `export { bootstrapLoginPage, renewByReauthorize, ... }` 直接再导出组件裸函数。
// 组件的裸函数签名是 `(config, redirect?, opts?)`，而本文件所有调用点用的都是绑定式 `(redirect)`：
//   - LoginView: `bootstrapLoginPage(currentRedirect())`
//   - request/api: `renewByReauthorize(`${pathname}${search}`)`
// 直接再导出会把 redirect 字符串当成 config 传进去 → `config.clientId` 为 undefined →
// 授权 URL 变成 `client_id=undefined`、探针打到本站 `/auth/session`（相对路径）→
// **静默免登与静默续期静默失效，且不报错**（最难查的一类 bug）。
// 统一改为「包一层、绑定配置」，签名与调用点严格对齐。

/** 会话探针：当前浏览器是否已有有效 IdP 会话 */
export const probeIdpSession = (opts?: { timeoutMs?: number }) => sso.probeSession(opts)

/** 静默免登：有会话直接跳授权（不返回），无会话返回 false */
export const silentSignIn = (redirect?: string) => sso.silentSignIn(redirect)

/** 登录页入口编排：有会话免登（不返回），无会话返回 false（正常显示登录框） */
export const bootstrapLoginPage = (redirect?: string, opts?: { force?: boolean }) =>
  sso.bootstrapLoginPage(redirect, opts)

/** 静默续期：token 过期/401 时重跑授权，IdP 会话在则无感（不返回） */
export const renewByReauthorize = (redirect?: string) => sso.renew(redirect)

/** 仅构建登出 URL（需要自己控制跳转时机时用） */
export const buildSloUrl = (options?: SloOptions) => sso.buildLogoutUrl(options)

/** 统一登出（SLO）：销毁 IdP 会话 + 清本地，然后回跳登录页 */
export const ssoLogout = (options?: SloOptions) => sso.logout(options)

/** 仅清本地凭据，不碰 IdP 会话 */
export const clearLocalAuth = () => sso.clearLocalAuth()

/** 解析 token claims（不验签，仅取用户信息） */
export const decodeOidcClaims = (token?: string) => sso.decodeClaims(token)

export type { SsoConfig, SloOptions }
