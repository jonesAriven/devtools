<template>
  <div class="users-view">
    <UserManagementPanel :config="config" />
  </div>
</template>

<script setup lang="ts">
/**
 * 用户管理（Phase 6 · 统一用户管理）
 *
 * 与 kb-web 等应用的差异：portal 是 **OIDC 机密客户端（BFF）**，
 * 用户管理接口由 portal-server 代理到 auth-center（生产 `/portal/api/admin/users`）。
 * 因此这里不复用「直连 auth-center」的数据源，而是指向自家 BFF 路径 ——
 * 同一份面板组件、两种数据源接入方式，UI 零改动。
 *
 * ⚠️ 薄代理模型的好处：portal 不需要让浏览器直连认证中心，
 * 管理动作全部经过门户服务端，天然带服务端鉴权与审计。
 */
import { UserManagementPanel, createUserAdminClient } from '@marschat/auth-components'
import type { UserManagementConfig } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const client = createUserAdminClient({
  // 与 src/api/request.ts 的 adminBaseURL 保持同一口径（开发走 vite 代理）
  baseUrl: import.meta.env.DEV ? '/api/admin/users' : '/portal/api/admin/users',
  getToken: () => userStore.token,
})

const config: UserManagementConfig = {
  client,
  title: '用户管理',
  subtitle: '统一账号池（auth-center）—— 全平台用户在此新增、编辑、停用与重置密码',
  roles: [
    { value: 'admin', label: '管理员' },
    { value: 'user', label: '普通用户' },
  ],
  // portal 的 token 不含 uid，用用户名作为「不能删除自己」的判据
  currentUsername: userStore.username || null,
}
</script>

<style scoped>
.users-view {
  padding: 16px;
}
</style>
