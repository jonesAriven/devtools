<template>
  <div class="users-view">
    <UserManagementPanel :config="config" />
  </div>
</template>

<script setup lang="ts">
/**
 * 用户管理（Phase 6 · 统一用户管理）
 *
 * 6 个应用共用公共组件 `UserManagementPanel`（一份实现），本文件只是**薄包装**：
 * 1. 绑定数据源 —— kb-web 持有的本就是 auth-center 签发的 OIDC access_token，
 *    因此直接跨域调 `/admin/users`（Bearer 鉴权），无需自建后端代理；
 * 2. 注入本应用语境（标题、角色文案、当前登录用户 id 用于"不能删自己"）。
 *
 * 后端契约见 `@marschat/auth-components` 的 `createUserAdminClient`。
 */
import { UserManagementPanel, createUserAdminClient } from '@marschat/auth-components'
import type { UserManagementConfig } from '@marschat/auth-components'
import { getToken } from '@/utils/token'
import { decodeOidcClaims, renewByReauthorize } from '@/utils/sso'

const client = createUserAdminClient({
  // auth-center 是平台唯一账号池；4 个直换票应用统一走这个地址
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
  title: '用户管理',
  subtitle: '统一账号池（auth-center）—— 全平台用户在此新增、编辑、停用与重置密码',
  roles: [
    { value: 'admin', label: '管理员' },
    { value: 'user', label: '普通用户' },
  ],
  currentUserId,
}
</script>

<style scoped>
.users-view {
  padding: 16px;
}
</style>
