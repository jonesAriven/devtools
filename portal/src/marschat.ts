/**
 * portal 应用接入装配（Phase 13 配置化接入）—— **本应用唯一需要写的「接入代码」。
 *
 * 一行 `createMarschatApp` 完成此前散在 6 个文件里的 7 件事：
 * ① 部署 base 声明 ② 令牌键绑定 ③ SSO 客户端 ④ 统一请求 + 401 分流
 * ⑤ 路由守卫（在 `install(app)` 之前注册）⑥ 权限预取 ⑦ 登出额外清理。
 *
 * 已删除的手写适配层：`src/utils/permissions.ts`（93 行）+ `src/utils/sso.ts`（168 行）= 261 行。
 *
 * ⚠️ 本文件是**唯一** import `@/router` 的接入文件，且只被 `src/main.ts` 引用。
 *    这样 `config.ts`（被 views / stores / api 广泛引用）保持零 router 依赖，避免循环依赖。
 */
import { createMarschatApp } from '@marschat/app-kit'
import router from '@/router'
import { APP_OPTIONS, RUNTIME } from '@/config/runtime'
import { useUserStore } from '@/stores/user'

export const marschat = createMarschatApp({
  ...APP_OPTIONS,
  router,
  runtime: RUNTIME,

  /**
   * 路由沿用既有 `router/index.ts`（含 MainLayout 嵌套子路由、`?redirect=` 回跳、
   * `requiresAdmin` 本地强闸），故不启用自动路由注册。
   * 装配层只负责「管道」，不接管路由与菜单。
   */
  autoRoutes: false,

  /**
   * 🔴 会话监视**不由装配层启动**，由 `main.ts` 按 `isOidcToken()` 逐会话分流。
   *
   * 原因（两条，任一条都足以否决交给装配层）：
   * 1. **portal 是双模应用** —— 账密/邮箱码走 portal-server BFF 换票（浏览器侧无 IdP 会话），
   *    SSO 才有。判据必须是**逐会话**的 `token_kind`，而 `sessionMode` 是应用级常量。
   * 2. **装配层硬编码的注入项对 portal 全部不适用**（已读0.1.4 dist 实证）：
   *    - `getLocalIdentity: decodeOidcClaims(portal_token).sub`
   *      ⇒ `portal_token` 是 portal-server **hutool 自签 HS256**（`JwtUtil.java:15-19`），
   *        不是 auth-center RS256，其 `sub` **恒undefined** ⇒ 身份一致性守卫永远拿不到身份；
   *    - `onIdentityMismatch: sso.renew()`
   *      ⇒ 对机密客户端是「浏览器直换票」，与 BFF 模型冲突，且丢掉 D-1 的 `reauthInFlight` 单飞；
   *    - `getToken` 读组件库默认键，读不到 `portal_token`。
   *    这三项都必须由 `main.ts` 用 pinia store 闭包注入。
   */
  watchSession: false,

  /**
   * 🔴 清理「令牌四键之外」的应用自管会话残留。
   *
   * 装配层的 `removeToken()` 只删令牌键，组件库 `clearLocalAuth()` 另删一个硬编码的
   * `auth_user` —— portal 的 `portal_user` / `portal_role` / `portal_auth_uid` **都不在其中**。
   *
   * ⚠️ 这不只是「不干净」，是**安全问题**：`portal_role` 承载 `isAdmin` 判定
   *    （`stores/user.ts` 的 `role === 'admin' || 'superadmin'`）。
   *    登出未清 ⇒ 下一个用同一浏览器的人（共享/公共机）在**尚未登录**时，
   *    前端 gate 读到残留的 `superadmin` 即渲染管理员入口 ⇒ 权限信息泄漏。
   *
   * 装配层在三条路径调用它（登出 / 401 / 会话丢失）⇒ **必须幂等**。
   * `clearSession()` 本身已是「逐键 removeItem」的幂等写法，直接复用，不重复实现。
   */
  clearExtraAuth: () => {
    useUserStore().clearSession()
  },
})

export const { config, sso, request, permissions } = marschat
