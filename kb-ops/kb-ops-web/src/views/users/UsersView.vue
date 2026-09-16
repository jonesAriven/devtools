<template>
  <div class="users-view">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户" name="users">
        <UserManagementPanel :config="config" />
      </el-tab-pane>
      <el-tab-pane label="菜单授权" name="menus" lazy>
        <MenuPermissionPanel :config="menuPermConfig" />
      </el-tab-pane>
      <el-tab-pane label="账号映射" name="mappings" lazy v-if="isPlatformAdmin">
        <AccountMappingPanel :config="mappingConfig" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
/**
 * 用户与授权管理（Phase 6 用户管理 + Phase 4 菜单授权）
 *
 * 两个页签均为公共组件薄包装（数据直连 auth-center /admin）：
 * - 用户：UserManagementPanel（Phase 6）
 * - 菜单授权：MenuPermissionPanel（Phase 4，角色 × 应用菜单）
 */
import { ref } from 'vue'
import {
  UserManagementPanel,
  MenuPermissionPanel,
  AccountMappingPanel,
  createUserAdminClient,
  createAccountMappingClient,
  createAccountMappingUserSearch,
} from '@marschat/auth-components'
import type {
  UserManagementConfig,
  MenuPermissionConfig,
  AccountMappingConfig,
} from '@marschat/auth-components'
import { getToken } from '@/utils/token'
import { decodeOidcClaims, renewByReauthorize, SSO_CONFIG } from '@/utils/sso'
// 🔴 T7 组件收敛（2026-09-15）：中心基址一律取自运行时配置，不再硬编码域名
import { API_BASE_URL } from '@/config'

const activeTab = ref('users')

const client = createUserAdminClient({
  baseUrl: `${API_BASE_URL}/admin/users`,
  getToken: () => getToken(),
  // 401（本地 token 过期但 IdP 会话仍在）→ 静默重授权，回来后自动重载列表
  onUnauthorized: () => void renewByReauthorize(),
})

/** 当前登录用户 id —— 面板据此禁止"删除自己 / 禁用自己" */
const claims = decodeOidcClaims(getToken() || '')
const currentUserId = (claims?.uid ?? claims?.sub ?? null) as number | string | null

/** 平台管理员（superadmin）才可见「账号映射」页签；应用管理员（admin）不可见（Phase 12 · 3.2-A）。
 * 判据取自 auth-center 签发的 OIDC token `role` claim（不另发请求）。
 * 中心侧 /admin/mappings 由 @PreAuthorize 挡非管理员，BFF 白名单放行 /admin/mappings* 作同源透传。 */
const isPlatformAdmin = claims?.role === 'superadmin'

const config: UserManagementConfig = {
  client,
  // Phase 8 双作用域：本页是**应用作用域**（本系统用户），只显示与运维后台有关的用户。
  // 平台作用域（全平台统一身份 / 跨应用授权 / 账号映射）已迁到门户的「统一认证中心」。
  scope: { mode: 'app', clientId: SSO_CONFIG.clientId, appName: '运维后台 kb-ops' },
  title: '本系统用户',
  subtitle:
    '仅显示与运维后台有关的用户（在本系统有角色、或有账号映射、或为管理员）。全局身份与全局角色请在门户的「统一认证中心」维护。',
  roles: [
    { value: 'superadmin', label: '超级管理员' },
    { value: 'admin', label: '管理员' },
    { value: 'user', label: '普通用户' },
  ],
  currentUserId,
  // Phase 4：用户×应用角色绑定（操作列「应用角色」按钮）
  appRoles: {
    baseUrl: API_BASE_URL,
    clientId: SSO_CONFIG.clientId,
    getToken: () => getToken(),
    onUnauthorized: () => void renewByReauthorize(),
  },
}

const menuPermConfig: MenuPermissionConfig = {
  baseUrl: API_BASE_URL,
  getToken: () => getToken(),
  clientId: SSO_CONFIG.clientId,
  title: '菜单授权',
  onUnauthorized: () => void renewByReauthorize(),
}

/**
 * 账号映射（Phase 7）：统一身份 ↔ 各系统本地账号。
 *
 * 数据直连 auth-center `/admin/mappings*`（与用户/角色同一鉴权面：Bearer + ROLE_ADMIN）。
 * 用途：各应用上报的本地账号在此汇总，"待绑定"的账号可手工认领到中心用户。
 */
const mappingClient = createAccountMappingClient({
  issuer: API_BASE_URL,
  getToken: () => getToken(),
  onUnauthorized: () => void renewByReauthorize(),
})

const mappingConfig: AccountMappingConfig = {
  client: mappingClient,
  title: '账号映射',
  subtitle:
    '各系统本地账号 ↔ 中心统一身份。应用启动时自动上报本地账号并尝试自动认领；未认领的可在右侧手工绑定。',
  searchUsers: createAccountMappingUserSearch({
    issuer: API_BASE_URL,
    getToken: () => getToken(),
    onUnauthorized: () => void renewByReauthorize(),
  }),
  clientLabels: {
    'marschat-portal': '门户 Portal',
    'marschat-kbops': '运维后台 kb-ops',
    'marschat-kbweb': '知识库 kb-web',
    'marschat-inframon': '基础设施监控',
    'marschat-activecode': '激活码系统',
    'cosmic-studio': 'COSMIC 度量表',
  },
  pageSize: 10,
  // 应用作用域：默认只看本应用的映射（切「全部应用」仍可看跨系统全貌，但那是中心职责）
  defaultClient: SSO_CONFIG.clientId,
}
</script>

<style scoped>
.users-view {
  padding: 16px;
}
</style>
