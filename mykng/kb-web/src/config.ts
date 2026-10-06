/**
 * 应用运行时配置 —— **唯一派生点**（Phase 13 配置化接入）
 *
 * 配置不再手写：交给 `@marschat/app-kit` 的纯函数 `resolveAppConfig` 派生，
 * 优先级 `显式选项 > public/app-config.json（apps-registry.yml 派生） > 内置默认`。
 *
 * 本文件同时是本应用「**令牌读写 + SSO 客户端**」的统一出口：
 * 令牌存储键在本文件绑定一次（`initTokenConfig`），其余模块一律从这里转出的函数取，
 * 杜绝「各应用各抄一份 key 前缀」的历史漂移。
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
  removeRefreshToken,
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
  appId: 'marschat-kbweb',
  contextPath: '/kb',
  apiBase: '/kb/api',
  /**
   * 令牌键前缀 —— **必须显式传**：kb-web 历史键是 `kb_*`，
   * 而由 appId 派生的默认前缀是 `kbweb_*`（`marschat-kbweb` 去掉前缀后净化）；
   * 不传会让所有老用户「登录态消失」。
   */
  tokenKeyPrefix: 'kb_',
  /**
   * 工作台落地路径 —— **显式声明，为固化意图，非修复当前故障**。
   *
   * 该字段有真实消费方：路由守卫 `onDeny`（`router.replace(config.homePath)`）
   * 与登录成功落点（`afterLoginRedirect || homePath`）。
   *
   * ⚠️ 为何显式写（而非依赖 `DEFAULTS.homePath = '/dashboard'`）：
   *   1. **当前行为一致** —— 显式值与默认值相同，且 `createWebHistory('/kb')` 会自动拼base
   *      （vue-router：`createBaseLocation() + base + to`），故 `/dashboard` 正确落到 `/kb/dashboard`。
   *      本行**不是**在修 bug，删掉它当前也不会坏。
   *   2. **防未来静默漂移** —— `AppOptionsInput.homePath` 的类型是 `string`（非字面量联合），
   *      而 `DEFAULTS.homePath` 是 `readonly "/dashboard"`。若公共包将来改默认值而本应用
   *      未同步，`vue-tsc` **不会报任何错**（传任意 `string` 都合法），
   *      缺省会静默跳到 kb-web 路由表里不存在的路径（渲染 404 空页）。
   *      显式声明把这个「静默失败」变成「显式一致」：改路由时能立刻发现两处不一致。
   *
   * 本值须与 `src/router/index.ts:42` 的 `path: 'dashboard'` 保持一致。
   */
  homePath: '/dashboard',
  /**
   * 认证 API 基址 —— 显式声明，为固化意图。
   * 缺省派生为 `${apiBase}/auth` = `/kb/api/auth`，与 `LoginView.vue:27` 硬编码同值，
   * 当前行为一致；显式声明后，日后改 `apiBase` 不会静默改掉认证基址。
   */
  authApiBase: '/kb/api/auth',
  /**
   * 会话模式固定为 `oidc`：kb-web 的登录入口是 auth-center 统一认证（OIDC public client + PKCE），
   * 账码登录拿到的也是 auth-center 签发的中心令牌（见 `stores/user.ts` 的 `setTokenKind('legacy')`
   * 仅为标记「本应用自签、探针不适用」）。`main.ts` 另按 `isOidcToken()` 做二次分流，
   * 账码会话不启动会话监视器（2026-09-15 实测事故的修复语义，见 main.ts 注释）。
   */
  sessionMode: 'oidc',
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

/** 部署前缀（子路径），如 `/kb`。 */
export const CONTEXT_PATH = APP_CONFIG.contextPath
/** 业务 API 基址。 */
export const API_BASE_URL = APP_CONFIG.apiBase

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
 * 会话监视器句柄（应用内**单例**）。
 *
 * 为什么要单例：监视器可能在多处被启动（应用启动、路由守卫），重复创建会挂上多份
 * 定时器与事件监听 → 探针请求翻倍、登出逻辑重复触发。统一在这里收口。
 *
 * ⚠️ 迁移说明：装配层（`createMarschatApp`）也内置了监视器，但其判据是**应用级**
 * `sessionMode`；kb-web 是双模应用（账密 legacy / SSO oidc），必须按**逐令牌**
 * `isOidcToken()` 分流，故 `marschat.ts` 传了 `watchSession: false`，
 * 监视器统一由此处按需启动（见 `main.ts` 的 2026-09-15 回归修复注释）。
 */
let sessionWatcher: ReturnType<typeof sso.watchSession> | null = null

/**
 * 启动**会话监视**（幂等）—— Phase 6「单点登出跨应用联动」。
 *
 * 监视线程会在「页面切回可见 / 窗口获焦 / 定时（默认 60s）」时探一次
 * auth-center `/auth/session`；**只有确认会话已消失**才清本地并跳登录页，
 * 探针异常一律保持现状（fail-safe，绝不把在线用户误踢出去）。
 */
export function startSessionWatcher(): void {
  if (sessionWatcher) return
  sessionWatcher = sso.watchSession({
    intervalMs: APP_CONFIG.sessionProbeIntervalMs,
    getToken: () => getToken(),
    clearLocalAuth: () => {
      removeToken()
      removeRefreshToken()
    },
    // 身份一致性守卫：共享浏览器换人登录时，本地旧 token 与 IdP 会话身份不符 → 静默重换票
    getLocalIdentity: () => decodeOidcClaims(getToken() || '').sub ?? null,
    onIdentityMismatch: () => void sso.renew(),
  })
}

/** 停止会话监视（登出前调用，避免登出跳转途中被二次判定）。 */
export function stopSessionWatcher(): void {
  sessionWatcher?.stop()
  sessionWatcher = null
}

/**
 * 统一登出（SLO）：先停监视 → 销毁 IdP 会话 + 清本地 → 回跳登录页。
 * 内部已 `clearLocalAuth()`，调用方无需再 `clearTokens()`，也不要再 `router.push`。
 */
export function ssoLogout(options?: Parameters<typeof sso.logout>[0]): void {
  stopSessionWatcher()
  sso.logout(options)
}

/**
 * 构造权限点**全码** `<client_id>:<type>:<code>`。
 * 路由 `meta.perm` / `PermissionGate` 必须传全码（半码永不匹配，坑 #10）。
 */
export function permCode(type: 'menu' | 'api', code: string): string {
  return `${APP_CONFIG.clientId}:${type}:${code}`
}

/**
 * 权限语境（菜单 / 路由守卫 / PermissionGate 三层同源共用一份）。
 *
 * `getToken` 必须显式注入 —— kb-web 的令牌键是 `kb_*`，不注入则 `/auth/permissions`
 * 不带 Bearer → 401 → 永远 `configured=false`，权限体系静默失效。
 *
 * 注：本应用**不启用** `adminBypass`（默认 true）。kb-web 的 `configured=false`
 * 兜底已在 `MainLayout.vue` 用 `isAdmin` 显式收敛「用户管理」菜单可见性，
 * 语义与 `infra` / `kb-ops` 一致（平台超管恒放行 + 应用侧入口收敛）。
 */
export const permOptions = {
  issuer: APP_CONFIG.issuer,
  clientId: APP_CONFIG.clientId,
  getToken: () => getToken(),
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
  removeRefreshToken,
  getIdToken,
  setIdToken,
  clearTokens,
  getTokenKind,
  setTokenKind,
  isOidcToken,
  decodeOidcClaims,
}
