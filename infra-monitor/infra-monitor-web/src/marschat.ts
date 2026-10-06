/**
 * 应用接入装配（Phase 13 配置化接入）—— **本应用唯一需要写的「接入代码」**。
 *
 * 一行 `createMarschatApp` 完成此前散在 4 个文件里的 7 件事：
 * ① 部署 base 声明 ② 令牌键绑定 ③ SSO 客户端 ④ 统一请求 + 401 分流
 * ⑤ 路由守卫（在 `app.use(router)` 之前注册）⑥ 会话监视（仅 OIDC 会话）⑦ 权限预取。
 *
 * 已删除的手写适配层（历史「改一处要改三处」的根源）：
 * `src/utils/sso.ts`（126 行兼容转发壳）· `src/utils/permissions.ts`（102 行）· `src/utils/token.ts`（36 行）。
 * 其能力分别由 `@marschat/app-kit` 与 `@marschat/auth-components` 提供，本应用不再持有副本。
 */
import { createMarschatApp } from '@marschat/app-kit'
import router from '@/router'
import { INFRA_MENUS } from '@/menus'
import { APP_OPTIONS, RUNTIME } from '@/config'

export const marschat = createMarschatApp({
  ...APP_OPTIONS,
  router,
  menus: INFRA_MENUS,
  runtime: RUNTIME,
  /**
   * 路由沿用既有 `router/index.ts`（含 MainLayout 嵌套子路由与 `?redirect=` 处理），
   * 故不启用自动路由注册；登录页 / 回调页 / 用户管理页仍由本应用自己定义。
   * 装配层只负责「管道」（配置 / 令牌 / SSO / 401 / 守卫 / 会话监视 / 权限预取）。
   */
  autoRoutes: false,
})

export const { config, sso, request, permissions } = marschat
