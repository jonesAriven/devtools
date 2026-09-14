<template>
  <div class="users-view">
    <UserManagementPanel :config="config" />
  </div>
</template>

<script setup lang="ts">
/**
 * 用户管理（Phase 6 · 统一用户管理）
 *
 * 6 个应用共用公共组件 `UserManagementPanel`，本文件只是**薄包装**：
 * 绑定数据源（infra-monitor 持有的就是 auth-center 签发的 OIDC access_token，直连 `/admin/users`）
 * 并注入本应用语境。
 */
import { UserManagementPanel, createUserAdminClient } from '@marschat/auth-components'
import type { UserManagementConfig } from '@marschat/auth-components'
import { getToken } from '@/utils/token'
import { decodeOidcClaims, renewByReauthorize } from '@/utils/sso'

const client = createUserAdminClient({
  baseUrl: 'https://auth.marschat.online/admin/users',
  getToken: () => getToken(),
  // 401（本地 token 过期但 IdP 会话仍在）→ 静默重授权，回来后自动重载列表
  onUnauthorized: () => void renewByReauthorize(),
})

/** 当前登录用户 id —— 面板据此禁止"删除自己 / 禁用自己" */
const claims = decodeOidcClaims(getToken() || '')
const currentUserId = (claims?.uid ?? claims?.sub ?? null) as number | string | null

const config: UserManagementConfig = {
  client,
  // Phase 8 双作用域：本页是**应用作用域**（本系统用户），只显示与本监控系统有关的用户。
  scope: { mode: 'app', clientId: 'marschat-inframon', appName: '基础设施监控' },
  title: '本系统用户',
  subtitle:
    '仅显示与本监控系统有关的用户（在本系统有角色、或有账号映射、或为管理员）。全局身份与全局角色请在门户的「统一认证中心」维护。',
  roles: [
    { value: 'admin', label: '管理员' },
    { value: 'user', label: '普通用户' },
  ],
  currentUserId,
  // Phase 4：用户×应用角色绑定（操作列「应用角色」按钮）
  appRoles: {
    baseUrl: 'https://auth.marschat.online',
    clientId: 'marschat-inframon',
    getToken: () => getToken(),
    onUnauthorized: () => void renewByReauthorize(),
  },
}
</script>

<style scoped>
.users-view {
  padding: 16px;
}
</style>
