/**
 * RBAC 权限接入（Phase 2 下游接入）—— 三层同源的「权限状态源 + 路由守卫」
 *
 * **三层同源** = 同一份权限状态被三个消费方共用，杜绝"菜单藏了但直输 URL 还能进"：
 *   ① 菜单渲染 —— `useMenus().visibleMenus`（本地菜单元数据 + 权限过滤）
 *   ② 路由守卫 —— `createAuthGuard(router, ...)`（本文件）
 *   ③ 组件渲染 —— `<PermissionGate :perm="...">`
 * 三者都读 `@marschat/auth-components` 的同一份模块级权限状态。
 *
 * 权限点由 auth-center `GET /auth/permissions?client=<client_id>` 下发（组件内 60s 缓存），
 * 本应用经同源 BFF 代理转发获取（见下方 permOptions 注释）。
 * **R10 默认策略**：auth-center 侧未为本应用配置任何权限点（`configured=false`）→ 全部放行。
 * 因此本文件接入后，存量应用**行为完全不变**；权限点由 Phase 4 菜单上报逐步产生。
 *
 * ⚠️ 权限点 code 约定（依据 auth-components 0.6.1 的 hasPermission 实现实测）：
 * 含 `:` 的 code 会被**原样使用**，不含才自动补 `<client_id>:` 前缀。
 * 而 `useMenus` 判定的码是 `<client_id>:menu:<key>`。
 * 所以路由 `meta.perm` / `PermissionGate` 的 `perm` **必须传全码**，
 * 传半码（如 `menu:users`）会因"含冒号→原样使用"而与菜单判定码不一致 → 永不匹配。
 * 统一用 `permCode()` 构造，禁止手写半码。
 */
import {
  usePermissions as createPermissions,
  type UsePermissionsOptions,
} from '@marschat/auth-components'
import { createAuthGuard } from '@marschat/frontend-common'
import type { Router } from 'vue-router'
import { API_BASE_URL, CONTEXT_PATH } from '@/config'
import { SSO_CONFIG } from './sso'
import { getToken } from './token'

/**
 * 构造本应用的权限点全码 `<client_id>:<type>:<code>`。
 *
 * @param type `menu` = 菜单级（Phase 4 由菜单上报产生）；`api` = 接口级（后端 @RequirePermission）
 * @param code 权限点短码（菜单 key / 接口标识）
 */
export function permCode(type: 'menu' | 'api', code: string): string {
  return `${SSO_CONFIG.clientId}:${type}:${code}`
}

/**
 * 本应用的权限语境（菜单 / 路由守卫 / PermissionGate **必须共用同一份**）。
 *
 * ⚠️ `issuer` 指向**本应用 BFF 的同源代理**（`AuthController#permissions`），而非 auth-center
 * 直连（对齐 portal `utils/permissions.ts` 先例）：独立登录径下浏览器只持本应用自签 HS384
 * token，中心验不过（2026-09-20 实测直连必 401，Console 恒带报错、权限体系静默失效）。
 * 代理端点以「该用户自己的中心身份」转发：SSO 会话透传中心 OIDC token，
 * 账密/邮箱码会话取服务端 CenterSessionStore 登录时暂存的中心 accessToken。
 *
 * `getToken` 显式注入本应用的凭据读法 —— 各应用的 localStorage 键各不相同
 * （`kb_access_token` 等），代理端点靠它识别「是谁在查权限」。
 *
 * 🔴 issuer 必须是**API 根**（portal 先例同款）：auth-components 内部固定拼
 * `${issuer}/auth/permissions`（0.6.1 dist 实测），带 `/auth` 后缀会 double 成
 * `/auth/auth/permissions` → 404。
 */
export const permOptions: UsePermissionsOptions = {
  issuer: API_BASE_URL,
  clientId: SSO_CONFIG.clientId,
  getToken: () => getToken(),
}

/**
 * 应用内**单例**权限句柄。
 *
 * auth-components 的权限状态本身就是模块级单例，这里再收口一层，
 * 是为了给调用方一个绑定好配置的稳定入口（不必每次重复传 `permOptions`）。
 */
export const permissions = createPermissions(permOptions)

/** 组合式入口（与组件库同名，调用方不用记两套名字） */
export function usePermissions() {
  return permissions
}

/**
 * 挂载路由权限守卫（三层同源之「路由层」）。
 *
 * 语义（依据 frontend-common 0.3.1 的 createAuthGuard 实现实测）：
 * - **只有声明了 `meta.perm` 的路由才受管**，其余原样放行；
 * - `publicPrefixes` 默认放行 `/login`、`/sso-callback`、`/auth/`、`/public`；
 * - `ensure()` 抛错 → **fail-open 放行**（认证中心抖动不能把整站打成 403）；
 * - 判定不通过 → 调 `onDeny` 后中断导航。
 *
 * ⚠️ 必须在 `app.use(router)` **之前**调用 —— 守卫要先于首次导航注册，
 * 否则首屏路由会跳过权限判定（这正是组件库要修正的「先挂路由后拉权限」脆弱点）。
 */
export function setupAuthGuard(router: Router): void {
  createAuthGuard(router, {
    ensure: () => permissions.ensure(),
    hasPerm: (code: string) => permissions.check(code),
    onDeny: () => {
      // 落到工作台而不是 403 空白页：权限点被收回时用户仍可用基础功能
      // 🔴 必须传**路由内路径**：router 已带 /infra base（createWebHistory(ctx)），
      //    再拼 CONTEXT_PATH 会落 /infra/infra/dashboard —— 不匹配任何路由 →
      //    渲染 404 空页（2026-09-14 良哥实测：monitor.marschat.online/infra/infra/services）。
      //    kb-ops 早已修成 `/dashboard`，本处与 kb-web 是漏推广的两处。
      router.replace('/dashboard')
    },
  })
}
