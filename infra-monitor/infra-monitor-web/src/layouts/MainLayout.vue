<template>
  <el-container class="main-layout">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-header" @click="router.push('/dashboard')">
        <el-icon :size="24" color="#409eff"><Monitor /></el-icon>
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">基础设施监控</span>
      </div>
      <div class="sidebar-body">
        <el-menu
          :default-active="currentRoute"
          :collapse="appStore.sidebarCollapsed"
          :unique-opened="true"
          background-color="#001529"
          text-color="rgba(255,255,255,0.75)"
          active-text-color="#ffffff"
          router
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <template #title>总览看板</template>
          </el-menu-item>
          <el-menu-item index="/hosts">
            <el-icon><Cpu /></el-icon>
            <template #title>主机管理</template>
          </el-menu-item>
          <el-menu-item index="/credentials">
            <el-icon><Key /></el-icon>
            <template #title>凭据管理</template>
          </el-menu-item>
          <el-menu-item index="/configs">
            <el-icon><Setting /></el-icon>
            <template #title>配置信息</template>
          </el-menu-item>
          <el-menu-item index="/services">
            <el-icon><Connection /></el-icon>
            <template #title>服务监控</template>
          </el-menu-item>
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
                <span class="avatar-letter">{{ (userStore.username || 'U')[0].toUpperCase() }}</span>
              </el-avatar>
              <span class="username">{{ userStore.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const currentRoute = computed(() => route.path)

const pageTitleMap: Record<string, string> = {
  Dashboard: '总览看板',
  Hosts: '主机管理',
  Credentials: '凭据管理',
  Configs: '配置信息',
  Services: '服务监控',
}

const pageTitle = computed(() => {
  const name = route.name as string
  return pageTitleMap[name] || '基础设施监控平台'
})

async function handleCommand(command: string) {
  if (command === 'logout') {
    await userStore.logout()
  }
}
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
  transition: width 0.3s;

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
      font-size: 16px;
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
