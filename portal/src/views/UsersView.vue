<template>
  <div class="users-view">
    <UserManagementPanel :config="config" />
  </div>
</template>

<script setup lang="ts">
/**
 * 门户用户（Phase 8 · **应用作用域**）
 *
 * ## 与「统一认证中心 → 统一用户」的区别（本轮核心设计）
 * | | 本页（应用） | 统一认证中心（平台） |
 * |---|---|---|
 * | 作用域 | `client=marschat-portal` | platform（全量） |
 * | 列表 | 只含与本门户有关的用户 | 全平台统一身份 |
 * | 全局角色 | 只读展示，**不可改** | 可改 |
 * | 删除 | **无**（改为「移出本系统」= 解绑本应用角色） | 可软删除统一身份 |
 *
 * 由 `scope.mode='app'` 驱动：组件据此给 `GET /admin/users` 追加 `client` 参数
 * （服务端强过滤，不是前端过滤），并切换按钮/列语义。
 *
 * ## 数据源
 * portal 是 OIDC **机密客户端（BFF）**：浏览器只有 portal-server 自签的 `portal_token`，
 * 所有 `/admin/**` 请求经 portal-server 以该用户自己的 auth-center 身份转发
 * （portal-server 已把 `/admin/**` 改为**前缀透传**，中心的 keyword/page/size/client
 * 参数都能带到位）。开发态走 vite 代理 `/api`。
 */
import { UserManagementPanel, createUserAdminClient, createUserMenuOverrideClient } from '@marschat/auth-components'
import type { UserManagementConfig } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { bffAuthorizeUrl } from '@/utils/sso'

const userStore = useUserStore()

/** BFF 根（与 AdminConsoleView 同口径；开发态走 vite 代理 `/api`） */
const BFF = import.meta.env.DEV ? '/api' : '/portal/api'

const client = createUserAdminClient({
  // 与 src/utils/permissions.ts 的 BFF_API_BASE 保持同一口径
  baseUrl: import.meta.env.DEV ? '/api/admin/users' : '/portal/api/admin/users',
  getToken: () => userStore.token,
  // 401（门户会话过期）→ 重新走 BFF 授权（服务端静默换票），回来后自动重载列表
  onUnauthorized: () => {
    window.location.href = bffAuthorizeUrl(window.location.origin + '/portal/users')
  },
})

const config: UserManagementConfig = {
  client,
  scope: { mode: 'app', clientId: 'marschat-portal', appName: '门户 Portal' },
  title: '门户用户',
  subtitle:
    '仅显示与本门户有关的用户（在本门户有角色、或有账号映射、或为管理员）。全局身份与全局角色请在「统一认证中心」维护。',
  roles: [
    { value: 'superadmin', label: '超级管理员' },
    { value: 'admin', label: '管理员' },
    { value: 'user', label: '普通用户' },
  ],
  // portal 的 token 不含 uid，用用户名作为「不能删除自己」的判据
  currentUsername: userStore.username || null,
  // 应用角色绑定（操作列「本系统角色」按钮）；组件据此拼 `${baseUrl}/admin/...`
  appRoles: {
    baseUrl: BFF,
    clientId: 'marschat-portal',
    getToken: () => userStore.token,
    onUnauthorized: () => {
      window.location.href = bffAuthorizeUrl(window.location.origin + '/portal/users')
    },
  },
  // 用户级菜单减法（Phase 9 / G1）：app 作用域下缺省用 scope.clientId，
  // 行操作列出现「菜单权限」按钮（角色上限内做减法，永不越权新增）。
  menuOverrides: {
    client: createUserMenuOverrideClient({
      issuer: BFF,
      getToken: () => userStore.token,
      onUnauthorized: () => {
        window.location.href = bffAuthorizeUrl(window.location.origin + '/portal/users')
      },
    }),
  },
}
</script>

<style scoped>
.users-view {
  padding: 16px;
}
</style>
