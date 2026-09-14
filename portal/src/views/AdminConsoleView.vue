<template>
  <div class="admin-console">
    <!-- 定位说明：明确「中心」与「应用」两套用户管理的分工，避免管理员混淆 -->
    <el-card shadow="never" class="console-head">
      <div class="head-row">
        <div>
          <h2 class="head-title">统一认证中心</h2>
          <p class="head-sub">
            平台作用域 —— 管的是<b>所有系统的所有人</b>。各应用内还有自己的「本系统用户」，
            只显示与该系统有关的用户；两者数据同源（auth-center），职责不同。
          </p>
        </div>
        <el-tag type="warning" effect="dark" class="scope-tag">平台作用域</el-tag>
      </div>
    </el-card>

    <el-tabs v-model="tab" class="console-tabs">
      <el-tab-pane label="统一用户" name="users" lazy>
        <UserManagementPanel :config="userConfig" />
      </el-tab-pane>

      <el-tab-pane label="跨应用授权" name="authz" lazy>
        <CrossAppAuthPanel :config="authzConfig" />
      </el-tab-pane>

      <el-tab-pane label="账号映射" name="mappings" lazy>
        <AccountMappingPanel :config="mappingConfig" />
      </el-tab-pane>

      <el-tab-pane label="角色与菜单授权" name="roles" lazy>
        <div class="role-toolbar">
          <span class="role-label">应用：</span>
          <el-select v-model="roleClientId" class="role-select" @change="onRoleClientChange">
            <el-option v-for="a in APPS" :key="a.clientId" :label="a.label" :value="a.clientId" />
          </el-select>
          <span class="role-hint">
            左侧选角色，右侧勾选该应用可见的菜单 / 允许的接口；保存后最迟 60 秒生效。
          </span>
        </div>
        <MenuPermissionPanel :config="menuPermConfig" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一认证中心（Phase 8）—— portal 承载的**平台作用域**管理台。
 *
 * ## 为什么放在 portal
 * portal 是平台主入口，且是 OIDC **机密客户端（BFF）** —— 浏览器里只有 portal-server 自签的
 * `portal_token`，所有 `/admin/**` 请求都经 portal-server 以「该用户自己的 auth-center 身份」
 * 转发（含服务端鉴权与审计），**不需要**把 auth-center 的管理接口暴露成可跨域直连。
 * 这比在 auth-center 里新起一个前端构建链更稳（auth-center 是 12 应用依赖的枢纽）。
 *
 * ## 与各应用「本系统用户」的分工（本轮核心）
 * | | 本页（中心） | 应用内「本系统用户」 |
 * |---|---|---|
 * | 作用域 | platform（全平台所有人） | client（只与本系统有关的人） |
 * | 列表 | 统一身份全量 | GET /admin/users?client= 过滤 |
 * | 核心操作 | 身份 CRUD · 全局角色 · **跨应用授权** · 账号映射总览 | 本系统角色 · 本系统菜单授权 |
 * | 安全边界 | 全量 | 不改全局角色、不删统一身份 |
 *
 * ## 数据源
 * 全部经 BFF：`${origin}/portal/api/admin/**`（开发态走 vite 代理 `/api`）。
 */
import { ref } from 'vue'
import {
  UserManagementPanel,
  MenuPermissionPanel,
  AccountMappingPanel,
  CrossAppAuthPanel,
  createUserAdminClient,
  createAccountMappingClient,
  createAccountMappingUserSearch,
  createAuthorizationMatrixClient,
} from '@marschat/auth-components'
import type {
  UserManagementConfig,
  MenuPermissionConfig,
  AccountMappingConfig,
  AuthorizationMatrixConfig,
} from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { bffAuthorizeUrl } from '@/utils/sso'

const userStore = useUserStore()

/** BFF 根（与 src/utils/permissions.ts 的 BFF_API_BASE 同口径） */
const BFF = import.meta.env.DEV ? '/api' : '/portal/api'

/** 401（门户会话过期）→ 重走 BFF 授权（服务端静默换票），回到本页后自动重载 */
function reauth() {
  window.location.href = bffAuthorizeUrl(window.location.origin + '/portal/admin')
}

/** 受管应用清单（与 apps-registry.yml 一致；中心授权界面按此提供应用选择） */
const APPS = [
  { clientId: 'marschat-portal', label: '门户 Portal' },
  { clientId: 'marschat-kbops', label: '运维后台 kb-ops' },
  { clientId: 'marschat-kbweb', label: '知识库 kb-web' },
  { clientId: 'marschat-inframon', label: '基础设施监控' },
  { clientId: 'marschat-activecode', label: '激活码系统' },
  { clientId: 'cosmic-studio', label: 'COSMIC 度量表' },
]

/** clientId → 友好名（授权矩阵表头 / 账号映射的应用名**共用同一份**，避免两处漂移） */
const APP_LABELS: Record<string, string> = Object.fromEntries(
  APPS.map((a) => [a.clientId, a.label])
)

// ---------------- 页签 ----------------
const tab = ref('users')

// ---------------- ① 统一用户（平台作用域） ----------------
const userClient = createUserAdminClient({
  baseUrl: `${BFF}/admin/users`,
  getToken: () => userStore.token,
  onUnauthorized: reauth,
})

const userConfig: UserManagementConfig = {
  client: userClient,
  // scope 缺省即 platform：全平台统一身份，不做 client 过滤
  scope: { mode: 'platform' },
  title: '统一用户',
  subtitle: '全平台统一身份（auth-center）—— 新增 / 编辑 / 停用 / 重置密码，并维护全局角色',
  roles: [
    { value: 'superadmin', label: '超级管理员' },
    { value: 'admin', label: '管理员' },
    { value: 'user', label: '普通用户' },
  ],
  // portal 的 token 不含 uid，用用户名作为「不能删除自己」的判据
  currentUsername: userStore.username || null,
  appRoles: {
    baseUrl: BFF,
    clientId: 'marschat-portal',
    getToken: () => userStore.token,
    onUnauthorized: reauth,
  },
}

// ---------------- ② 跨应用授权矩阵（本轮新增核心能力） ----------------
const authzConfig: AuthorizationMatrixConfig = {
  client: createAuthorizationMatrixClient({
    issuer: BFF,
    getToken: () => userStore.token,
    onUnauthorized: reauth,
  }),
  title: '跨应用授权',
  subtitle:
    '全平台统一授权矩阵：纵向是用户，横向是各应用，单元格是该用户在该系统拥有的角色。点击单元格即可分配/回收。',
  clientLabels: APP_LABELS,
}

// ---------------- ③ 账号映射（全局） ----------------
const mappingConfig: AccountMappingConfig = {
  client: createAccountMappingClient({
    issuer: BFF,
    getToken: () => userStore.token,
    onUnauthorized: reauth,
  }),
  title: '账号映射',
  subtitle:
    '各系统本地账号 ↔ 中心统一身份。应用启动时自动上报本地账号并尝试自动认领；未认领的可在右侧手工绑定。',
  searchUsers: createAccountMappingUserSearch({
    issuer: BFF,
    getToken: () => userStore.token,
    onUnauthorized: reauth,
  }),
  clientLabels: APP_LABELS,
  pageSize: 10,
}

// ---------------- ④ 角色与菜单授权（按应用切换） ----------------
const roleClientId = ref('marschat-portal')
const menuPermConfig = ref<MenuPermissionConfig>({
  baseUrl: BFF,
  getToken: () => userStore.token,
  clientId: roleClientId.value,
  title: '角色与菜单授权',
  onUnauthorized: reauth,
})

function onRoleClientChange(v: string) {
  // 换应用 = 换 clientId，重建 config 触发面板重载该应用的权限树
  menuPermConfig.value = { ...menuPermConfig.value, clientId: v, title: `角色与菜单授权 — ${v}` }
}
</script>

<style scoped>
.admin-console {
  padding: 16px;
}
.console-head {
  border-radius: 8px;
  margin-bottom: 12px;
}
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.head-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
}
.head-sub {
  margin: 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}
.scope-tag {
  flex-shrink: 0;
}
.console-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.role-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}
.role-select {
  width: 200px;
}
.role-hint {
  color: #909399;
}
</style>
