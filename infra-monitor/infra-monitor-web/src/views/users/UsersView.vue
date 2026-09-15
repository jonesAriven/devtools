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
 * 绑定数据源并注入本应用语境。
 *
 * ## 数据源：经本应用 BFF 代理（2026-09-15 修正）
 * 原先直连 `https://auth.marschat.online/admin/users`（把本地 token 当中心 token 用）。
 * 账密登录统一到认证中心后，本应用会话持有的是**本应用自签 HS256 token**，中心不认 →
 * 请求 401 → 组件 onUnauthorized 触发静默重授权，**点「用户管理」直接跳 IdP 登录页**。
 * 现改为同源调本应用 BFF `${API_BASE_URL}/api/admin/users`，由 infra-monitor-server
 * 以「该用户本人的中心身份」转发（见后端 `AdminProxyController`，无服务账号兜底）。
 */
import { UserManagementPanel, createUserAdminClient } from '@marschat/auth-components'
import type { UserManagementConfig } from '@marschat/auth-components'
import { getToken } from '@/utils/token'
import { decodeOidcClaims, renewByReauthorize } from '@/utils/sso'
import { API_BASE_URL } from '@/config'

const client = createUserAdminClient({
  // 同源 BFF：/infra/api/api/admin/users → nginx → infra-monitor-server /infra/api/admin/users
  baseUrl: `${API_BASE_URL}/api/admin/users`,
  getToken: () => getToken(),
  // 401（本应用会话过期 / 中心会话失效）→ 静默重授权，回来后自动重载列表
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
  // Phase 4：用户×应用角色绑定（操作列「应用角色」按钮）；组件拼 `${baseUrl}/admin/...`
  // 同样走 BFF，否则账密会话下该按钮仍会 401 跳 IdP
  appRoles: {
    baseUrl: API_BASE_URL,
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
