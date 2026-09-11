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
 * 绑定数据源（kb-ops 持有的就是 auth-center 签发的 OIDC access_token，直连 `/admin/users`）
 * 并注入本应用语境。
 */
import { UserManagementPanel, createUserAdminClient } from '@marschat/auth-components'
import type { UserManagementConfig } from '@marschat/auth-components'
import { getToken } from '@/utils/token'
import { decodeOidcClaims } from '@/utils/sso'

const client = createUserAdminClient({
  baseUrl: 'https://auth.marschat.online/admin/users',
  getToken: () => getToken(),
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
