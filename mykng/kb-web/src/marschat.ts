/**
 * 应用接入装配（Phase 13 配置化接入）—— **本应用唯一需要写的「接入代码」**。
 *
 * 一行 `createMarschatApp` 完成此前散在 4 个文件里的 7 件事：
 * ① 部署 base 声明 ② 令牌键绑定 ③ SSO 客户端 ④ 统一请求 + 401 分流
 * ⑤ 路由守卫（在 `app.use(router)` 之前注册）⑥ 会话监视（仅 OIDC 会话）⑦ 权限预取。
 *
 * 已删除的手写适配层（历史「改一处要改三处」的根源）：
 * `src/utils/sso.ts`（130 行转发壳）· `src/utils/permissions.ts`（92 行）· `src/utils/token.ts`（36 行）。
 * 其能力分别由 `@marschat/app-kit` 与 `@marschat/auth-components` 提供，本应用不再持有副本。
 *
 * ⚠️ 本文件是**唯一** import `@/router` 的接入文件，且只被 `src/main.ts` 引用。
 *    这样 `config.ts`（被 router / stores / api / views 广泛引用）保持零 router 依赖，
 *    避免 `router → MainLayout → stores → api → config → router` 循环依赖。
 */
import { createMarschatApp } from '@marschat/app-kit'
import router from '@/router'
import { APP_OPTIONS, RUNTIME } from '@/config'

export const marschat = createMarschatApp({
  ...APP_OPTIONS,
  router,
  runtime: RUNTIME,
  /**
   * 路由沿用既有 `router/index.ts`（含 MainLayout 嵌套子路由、`?redirect=` 处理与
   * `/share/:code` 免登录页），故不启用自动路由注册；登录页 / 回调页 / 用户管理页
   * 仍由本应用自己定义。装配层只负责「管道」（配置 / 令牌 / SSO / 401 / 守卫 /
   * 会话监视 / 权限预取），**不接管路由与菜单**。
   *
   * ⚠️ 刻意不传 `menus`：`src/menus.ts` 导出的是**工厂函数** `createKbMenus(ctx)`，
   *    其 `ctx` 依赖 Pinia store 闭包（`spaceStore` / `moduleStore` / `isAdmin`），
   *    必须在组件 `setup()` 期求值。若在模块加载期求值，store 尚未初始化 ⇒ 菜单全灰。
   *    本应用用自写 `MainLayout.vue`（不消费 `createShell()`），菜单仍由
   *    `useMenus(permOptions, createKbMenus({...}))` 在 setup 期注入 —— 求值时机保持不变。
   */
  autoRoutes: false,

  /**
   * 🔴 会话监视**不由装配层启动**，改由 `main.ts` 按 `isOidcToken()` 逐令牌分流。
   *
   * 原因：装配层的判据是**应用级** `sessionMode`（编译期常量），而 kb-web 是
   * **双模应用** —— 账密/邮箱码登录得到 `token_kind=legacy` 的自签 token，
   * SSO 才得到 `token_kind=oidc`。只有后者在浏览器里有真正的 IdP 会话可探测。
   *
   * 若让装配层按 `sessionMode='oidc'` 无条件启动，账密会话也会被挂上监视器：
   * 探针打 `/auth/session` 恒返回 `authenticated:false`，而 `start()` 内
   * `setTimeout(probe, 3000)` 会在 **3 秒后首探** → 每次整页刷新都被判「他处已登出」
   * → 清本地凭据 + 跳 `<base>/login?slo=1`，账密会话活不过 3 秒。
   * 这正是 2026-09-15 回归修复针对的问题，不能在迁移中弄丢（详见 `main.ts` 注释）。
   */
  watchSession: false,
})

export const { config, sso, request, permissions } = marschat
