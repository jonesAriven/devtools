<template>
  <el-container class="main-layout">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-header" @click="router.push('/dashboard')">
        <el-icon :size="24" color="#409eff"><Setting /></el-icon>
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">运维管理</span>
      </div>
      <div class="sidebar-body">
        <el-menu
          :default-active="currentRoute"
          :collapse="appStore.sidebarCollapsed"
          :unique-opened="true"
          :default-openeds="defaultOpeneds"
          background-color="#001529"
          text-color="rgba(255,255,255,0.75)"
          active-text-color="#ffffff"
          router
          class="sidebar-menu"
        >
          <!-- Phase 5 菜单定义数据化：v-for 渲染 menus.ts 定义（与 menu-registry.yml 同构），
               权限过滤收敛到 useMenus（与路由守卫同一份权限状态；configured=false 或超管恒全显） -->
          <template v-for="m in visibleMenus" :key="m.key">
            <el-sub-menu v-if="m.children?.length" :index="m.key">
              <template #title>
                <el-icon><component :is="ICONS[m.icon]" /></el-icon>
                <span>{{ m.title }}</span>
              </template>
              <el-menu-item v-for="c in m.children" :key="c.key" :index="c.path">
                <el-icon><component :is="ICONS[c.icon]" /></el-icon>
                <template #title>{{ c.title }}</template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else-if="m.path" :index="m.path">
              <el-icon><component :is="ICONS[m.icon]" /></el-icon>
              <template #title>{{ m.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header-bar" height="56px">
        <div class="header-left">
          <el-icon class="header-btn" @click="appStore.toggleSidebar()">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <span class="header-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" class="user-avatar">
                <span class="avatar-letter">{{ (userStore.profile?.nickname || userStore.profile?.username || 'U')[0].toUpperCase() }}</span>
              </el-avatar>
              <span class="username">{{ userStore.profile?.nickname || userStore.profile?.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人信息
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="content-area">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis, Cpu, Monitor, Connection, Position, Key, Link, Box,
  Upload, List, Warning, Tools, Reading, Download, Tickets, UserFilled,
} from '@element-plus/icons-vue'
import { useMenus, fetchPermissions } from '@marschat/auth-components'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/token'
import { permOptions } from '@/utils/permissions'
import { KB_OPS_MENUS } from '@/menus'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const currentRoute = computed(() => route.path)

/** menus.ts 的 icon 名 → 组件实例映射（模板 component :is 消费）。 */
const ICONS: Record<string, Component> = {
  DataAnalysis, Cpu, Monitor, Connection, Position, Key, Link, Box,
  Upload, List, Warning, Tools, Reading, Download, Tickets, UserFilled,
}

/**
 * Phase 5 菜单定义数据化：useMenus 基于**同一份权限单例状态**过滤 KB_OPS_MENUS
 * （与路由守卫 createAuthGuard 三层同源；configured=false 或超管恒全显——R10）。
 * 组节点 children 全被过滤后由模板 v-if="m.children?.length" 隐藏。
 */
const { visibleMenus } = useMenus(permOptions, KB_OPS_MENUS)

const defaultOpeneds = computed<string[]>(() => {
  const path = route.path
  const groups: string[] = []
  if (['/hosts', '/services', '/ports', '/credentials', '/domains', '/dependencies'].some(p => path.startsWith(p))) {
    groups.push('resource-group')
  }
  if (['/deployments', '/conflicts'].some(p => path.startsWith(p))) {
    groups.push('deploy-group')
  }
  if (['/knowledge', '/import', '/logs'].some(p => path.startsWith(p))) {
    groups.push('system-group')
  }
  return groups
})

const pageTitleMap: Record<string, string> = {
  Dashboard: '看板',
  Hosts: '主机管理',
  Services: '服务管理',
  Ports: '端口管理',
  Credentials: '凭据管理',
  Domains: '域名管理',
  Dependencies: '依赖管理',
  Deployments: '部署记录',
  Conflicts: '矛盾检测',
  Knowledge: '运维知识库',
  Import: '数据导入',
  Logs: '操作日志',
}

const pageTitle = computed(() => {
  const name = route.name as string
  return pageTitleMap[name] || '运维管理'
})

async function handleCommand(command: string) {
  if (command === 'logout') {
    await userStore.logout()
  }
}

onMounted(() => {
  if (!userStore.profile && userStore.isLoggedIn) {
    userStore.fetchProfile()
  }
  // 三层同源：进入布局即拉权限（60s 缓存；守卫/菜单/门共用）
  void fetchPermissions(permOptions)
})
</script>

<style scoped lang="scss">
.main-layout {
  height: 100%;
}

.sidebar {
  background-color: #001529;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    cursor: pointer;
    border-bottom: 1px solid #1f3a5f;
    flex-shrink: 0;

    .logo-text {
      font-size: 18px;
      font-weight: 700;
      color: #fff;
      letter-spacing: 1px;
      white-space: nowrap;
    }
  }

  .sidebar-body {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
  }

  .sidebar-menu {
    border-right: none;

    :deep(.el-menu-item) {
      &:hover {
        background-color: #1f3a5f;
        color: #fff;
      }
      &.is-active {
        background-color: #409eff !important;
        color: #fff !important;
      }
    }

    :deep(.el-sub-menu__title) {
      &:hover {
        background-color: #1f3a5f;
        color: #fff;
      }
    }

    :deep(.el-menu--inline) {
      background-color: #000c17 !important;
    }
  }
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  min-width: 0;
}

.header-btn {
  font-size: 18px;
  cursor: pointer;
  color: #606266;
  flex-shrink: 0;
  padding: 6px;
  border-radius: 4px;
  transition: all 0.2s;

  &:hover {
    color: #409eff;
    background-color: #ecf5ff;
  }
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-left: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #606266;

  &:hover {
    color: #409eff;
  }
}

.user-avatar {
  background-color: #409eff;
  color: #fff;
  font-size: 14px;
  font-weight: 600;

  .avatar-letter {
    line-height: 32px;
  }
}

.username {
  font-size: 14px;
}

.content-area {
  padding: 16px;
  background-color: #f0f2f5;
  overflow-y: auto;
}
</style>
