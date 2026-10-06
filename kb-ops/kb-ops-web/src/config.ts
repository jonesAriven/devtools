/**
 * 应用运行时配置 —— **唯一派生点**（Phase 13 配置化接入）
 *
 * 配置不再手写：交给 `@marschat/app-kit` 的纯函数 `resolveAppConfig` 派生，
 * 优先级 `显式选项 > public/app-config.json（apps-registry.yml 派生） > 内置默认`。
 *
 * ⚠️ 本文件**刻意不 import `@/router`**：它被 router / stores / api / views 广泛引用，
 *    一旦依赖 router 就会形成 `router → MainLayout → stores → api → request → config → router`
 *    的循环依赖（模块初始化顺序不确定，`permCode` 可能拿到 undefined）。
 *    需要 router 的装配单独放在 `src/marschat.ts`。
 */
import {
  resolveAppConfig,
  readRuntimeConfig,
  deriveTokenKeys,
  type ResolvedAppConfig,
  type RuntimeConfig,
} from '@marschat/app-kit'
import { createSsoClient, initTokenConfig, type SsoConfig } from '@marschat/auth-components'
// ⚠️ 必须**先 import 到本地作用域再 export**（不能只写 `export { x } from 'mod'`）：
//    后者不产生本地绑定，本文件下方的 `permOptions.getToken` 会拿到 undefined。
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  getIdToken,
  setIdToken,
  clearTokens,
  getTokenKind,
  setTokenKind,
  isOidcToken,
  decodeOidcClaims,
} from '@marschat/auth-components'

/** 本应用的接入选项（对齐 apps-registry.yml 的 client-id / context-path / api-base）。 */
export const APP_OPTIONS = {
  appId: 'marschat-kbops',
  contextPath: '/ops',
  apiBase: '/ops-api',
  authApiBase: '/ops/auth-api',
  /**
   * 令牌键前缀 —— **必须显式传**：kb-ops 历史键是 `kb_ops_*`，
   * 而由 appId 派生的默认前缀是 `kbops_*`；不传会让所有老用户「登录态消失」。
   */
  tokenKeyPrefix: 'kb_ops_',
} as const

/** 运行时配置（`public/app-config.json`，由 `devtools/scripts/gen-from-registry.py` 生成）。 */
export const RUNTIME: RuntimeConfig = readRuntimeConfig('/')

/** 派生结果 —— 排查「配置到底生效了没」时的唯一依据。 */
export const APP_CONFIG: ResolvedAppConfig = resolveAppConfig(
  APP_OPTIONS,
  RUNTIME,
  window.location.origin,
)

// 令牌存储键绑定（与 APP_OPTIONS.tokenKeyPrefix 同源，避免两处各写一份漂移）
initTokenConfig(deriveTokenKeys(APP_OPTIONS.appId, APP_OPTIONS.tokenKeyPrefix))

/** 部署前缀（子路径），如 `/ops`。 */
export const CONTEXT_PATH = APP_CONFIG.contextPath
/** 业务 API 基址。 */
export const API_BASE_URL = APP_CONFIG.apiBase
/** 认证 API 基址（登录 / 邮箱码 / 找回密码）。 */
export const AUTH_BASE_URL = APP_CONFIG.authApiBase

/**
 * 本应用的 OIDC 配置（登录页 / 回调页 / 登出共用）。
 * issuer / clientId / redirectUri 全部来自运行时配置，不硬编码域名。
 */
export const SSO_CONFIG: SsoConfig = {
  issuer: APP_CONFIG.issuer,
  clientId: APP_CONFIG.clientId,
  redirectUri: APP_CONFIG.redirectUri,
  scope: 'openid profile',
  loginUrl: APP_CONFIG.loginUrl,
  silentLogin: true,
}

/** 绑定好配置的 SSO 客户端（应用内单例）。 */
export const sso = createSsoClient(SSO_CONFIG)

/**
 * 构造权限点**全码** `<client_id>:<type>:<code>`。
 * 路由 `meta.perm` / `PermissionGate` 必须传全码（半码永不匹配，坑 #10）。
 */
export function permCode(type: 'menu' | 'api', code: string): string {
  return `${APP_CONFIG.clientId}:${type}:${code}`
}

/**
 * 当前页面的**路由内路径**（剥离部署前缀）。
 *
 * 🔴 `window.location.pathname` 含前缀（`/ops/...`），而 router 的 base 也是 `/ops`；
 * 原样传给 `renew` / 回调页的 `router.replace` 会二次拼接成 `/ops/ops/...` → 404
 * （2026-09-14 实测）。所有「拿当前地址去回跳」的地方一律经此函数。
 */
export function currentSpaPath(): string {
  const p = window.location.pathname
  const stripped = p.startsWith(CONTEXT_PATH) ? p.slice(CONTEXT_PATH.length) : p
  return (stripped || '/') + window.location.search
}

/**
 * 权限语境（菜单 / 路由守卫 / PermissionGate 三层同源共用一份）。
 * `getToken` 必须显式注入 —— 各应用 localStorage 键不同，不注入则 `/auth/permissions`
 * 不带 Bearer → 401 → 永远 `configured=false`，权限体系静默失效。
 */
export const permOptions = {
  issuer: APP_CONFIG.issuer,
  clientId: APP_CONFIG.clientId,
  getToken: () => getToken(),
  adminBypass: true,
}

// ── 令牌便捷出口 ──
// 唯一实现仍在 `@marschat/auth-components`，此处只是本应用的统一引用点；
// 不再各应用各抄一份 localStorage 读写（历史：kb-web / kb-ops / infra 三份 key 前缀各不相同）。
// 注意：这里 export 的是上方 import 进来的**本地绑定**，故本文件内部也能直接用它们。
export {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  getIdToken,
  setIdToken,
  clearTokens,
  getTokenKind,
  setTokenKind,
  isOidcToken,
  decodeOidcClaims,
}
