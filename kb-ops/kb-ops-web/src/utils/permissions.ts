/**
 * RBAC 权限接入（Phase 2 下游接入）—— 三层同源的「权限状态源 + 路由守卫」
 *
 * **三层同源** = 同一份权限状态被三个消费方共用，杜绝"菜单藏了但直输 URL 还能进"：
 *   ① 菜单渲染 —— `useMenus().visibleMenus`（本地菜单元数据 + 权限过滤）
 *   ② 路由守卫 —— `createAuthGuard(router, ...)`（本文件）
 *   ③ 组件渲染 —— `<PermissionGate :perm="...">`
 * 三者都读 `@marschat/auth-components` 的同一份模块级权限状态。
 *
 * 权限点由 auth-center `GET /auth/permissions?client=<client_id>` 下发（组件内 60s 缓存）。
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
import { CONTEXT_PATH } from '@/config'
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
 * `issuer` / `clientId` 直接取 `SSO_CONFIG`（本应用 OIDC 配置的唯一真源），
 * 避免两处硬编码漂移；`getToken` 显式注入本应用的凭据读法 —— 各应用的 localStorage 键
 * 各不相同（`kb_access_token` 等），不注入的话 `/auth/permissions` 请求不带 Bearer → 401
 * → 永远 `configured=false`，权限体系静默失效。
 */
export const permOptions: UsePermissionsOptions = {
  issuer: SSO_CONFIG.issuer,
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
      router.replace(`${CONTEXT_PATH}/dashboard`)
    },
  })
}
