/**
 * 运行时配置 + 接入选项（T7 组件收敛，2026-09-15；Phase 13 接入 2026-10-06）
 *
 * 优先级：`public/app-config.json`（由 `scripts/gen-from-registry.py` 从 apps-registry.yml 派生）
 * > 编译期 `import.meta.env` > 内置默认。
 *
 * 为什么要有它：此前 portal 的部署 base（`/portal`）、中心 issuer、clientId 散落在
 * `main.ts` / `router/index.ts` / `utils/sso.ts` / `utils/permissions.ts` / 两个视图里
 * **各写一份字面量** —— 改一个环境的入口要改 6 处并重新构建，与「接入即配置」相悖。
 *
 * ⚠️ 模块求值时用**同步 XHR** 保证配置在消费方常量初始化前就绪
 *    （浏览器对主线程同步 XHR 有弃用告警，bootstrap 化为后续项）。
 *
 * ⚠️ 本文件**刻意不 import `@/router`、不 import `@/stores`**：它被 `marschat.ts` /
 *    `router` / 各视图 / store 广泛引用，一旦依赖它们就会形成循环依赖。
 *    需要 store的装配（`clearExtraAuth`）放在 `src/marschat.ts`。
 */
import { readRuntimeConfig, type RuntimeConfig } from '@marschat/app-kit'

// ── 运行时配置（保留既有实现，仅把读法交给 app-kit 以统一横幅剥离）──
const runtime = readRuntimeConfig(import.meta.env.BASE_URL)

/** 部署 base（子路径） */
export const CONTEXT_PATH = runtime.contextPath ?? import.meta.env.VITE_CONTEXT_PATH ?? '/portal'

/** 本应用 BFF API 前缀（portal-server 同源代理） */
export const API_BASE_URL = runtime.apiBase ?? import.meta.env.VITE_API_BASE_URL ?? '/portal/api'

/** auth-center 公网基址（机密客户端走 BFF，前端只用它做 OIDC 元数据/登出回跳） */
export const OIDC_ISSUER = runtime.issuer ?? import.meta.env.VITE_OIDC_ISSUER ?? 'https://auth.marschat.online'

/** 本应用的 OIDC client_id */
export const OIDC_CLIENT_ID = runtime.clientId ?? import.meta.env.VITE_OIDC_CLIENT_ID ?? 'marschat-portal'

/** 授权码回调地址（按 origin 动态生成，公网 / LAN / 本地开发三环境自适应） */
export const OIDC_REDIRECT_URI = `${window.location.origin}${CONTEXT_PATH}/auth/callback`

/** 开发态走 vite 代理，产物走 BFF 同源前缀 —— 供各视图/工具统一取用 */
export const BFF_API_BASE = import.meta.env.DEV ? '/api' : API_BASE_URL

/**
 * 接入选项（供 `createMarschatApp` 消费）—— Phase 13 配置化接入。
 *
 * 逐字段说明为何这么写（每条都有实测依据，不是照抄默认）：
 *
 * - `tokenKeys.accessTokenKey: 'portal_token'`
 *   🔴 **必须显式**：portal 的凭据键是 `portal_token`（**单键、自管**，`stores/user.ts`），
 *   而组件库默认是 `auth_access_token`。若不覆盖，`createRequest` 的 `getToken()` 恒`null`
 *   ⇒ 401 分流与权限预取静默失效。
 *   ⚠️ 只覆盖这一个键：`tokenKindKey` 不传即按 `tokenKeyPrefix` 派生为
 *   `portal_token_kind`，**与现状完全一致**（`stores/user.ts:23` 原就写死这个键），零漂移。
 *
 * - `permissionsIssuer: '/portal/api'`
 *   🔴 权限查询必须走 **portal-server 同源代理**，浏览器不能直连 auth-center
 *   （直连 401 → `configured=false` → 权限体系静默全放行）。
 *   ⚠️ 刻意**不沿用** `BFF_API_BASE` 的 dev 分支：dev 下 `BFF_API_BASE='/api'`，
 *   而 `vite.config.ts` 的 `/api/auth` 规则指向 **kb.marschat.online**（不是 portal-server）
 *   ⇒ 权限请求会打到错误的服务器。`/portal/api` 同源、无 proxy 规则，两种环境都对。
 *
 * - `homePath: '/'`
 *   🔴 **必须显式**：`DEFAULTS.homePath = '/dashboard'`，而 **portal 路由表里没有 `dashboard`**
 *   （`router/index.ts` 的 children 是 `''` / `manage` / `users` / `admin`）。
 *   守卫 `onDeny` 消费该字段（`router.replace(config.homePath)`）⇒ 不传则在
 *   「权限点被收回」时跳到不存在的路由。`vue-router` 会自动拼 base
 *   （`createBaseLocation() + base + to`），但 base 只加前缀、**不校验路由是否存在**。
 *
 * - ⚠️ **刻意不传** `authApiBase`：`createMarschatApp` 会按 `${apiBase}/auth` 派生
 *   = `/portal/api/auth`，正是 portal-server `AuthController`（`@RequestMapping("/api/auth")`
 *   + `context-path:/portal`）的账密登录入口。
 *   🔴 **它与 `LoginView.vue` 里那个同名但不同义的 `authApiBase` 不是一回事**——
 *   详见 `src/config/runtime.ts` 末尾的语义差异说明。
 */
export const APP_OPTIONS = {
  appId: 'marschat-portal',
  contextPath: CONTEXT_PATH,
  apiBase: API_BASE_URL,
  tokenKeys: { accessTokenKey: 'portal_token' },
  permissionsIssuer: API_BASE_URL,
  homePath: '/',
} as const

// 令牌存储键绑定由**装配层**完成（`createMarschatApp` 内部 `initTokenConfig(config.tokenKeys)`，
// 已读 0.1.4 dist 实证：`(window.__MARSCHAT_APP_BASE__ = t.contextPath), U(t.tokenKeys);`），
// 本文件**不再重复绑定** —— 重复绑定会形成第二处配置源。
// 配套：`stores/user.ts` 原有的 `initTokenConfig({ tokenKindKey })` 也已删除（重复绑定的第三处；
// 其实现是 `{...DEFAULT, ...options}` 对象展开、未传的键保留默认值，所以原本**不会**把四键重置
// —— 删除是「消除重复绑定」而非「修 bug」）。

/**
 * 🔴 语义差异说明：`config.authApiBase` vs `LoginView` 的 `authApiBase`
 *
 * 两者**同名不同物**，迁移时不可合并（Phase 13 实测确认）：
 *
 * | | 值 | 提供方 | 用途 |
 * |---|---|---|---|
 * | **装配层 `authApiBase`**（本文件派生） | `/portal/api/auth` | portal-server `AuthController`（`@RequestMapping("/api/auth")` + `context-path: /portal`） | **账密登录** `POST ${authApiBase}/login`（app-kit dist 实证） |
 * | **`LoginView.vue` 的 `authApiBase`** | `/portal/auth-api` | **portal-server 无此路由**（grep 0 命中），由 nginx / main 域提供 | 仅**忘记密码 / 重置密码**：组件拼 `${authApiBase}/forgot-password` 与 `/reset-password` |
 *
 * ⚠️ 若把 LoginView 改成引用 `config.authApiBase`，密码重置会打到
 * **不存在的** `/portal/api/auth/reset-password` ⇒ 404。
 * ⇒ `LoginView.vue:37` 的硬编码**必须原样保留**。
 */

/** 运行时配置对象（供 `createMarschatApp` 的 `runtime` 选项） */
export { runtime as RUNTIME }
