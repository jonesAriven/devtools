<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import {
  LayoutDashboard, FolderOpen, Search, Tag, Share2,
  Trash2, FileText, Settings, Server, LogOut, Menu,
  ChevronLeft, Monitor, Activity, Database,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

const menuItems = [
  { path: '/dashboard', label: '工作台', icon: LayoutDashboard },
  { path: '/space', label: '知识空间', icon: FolderOpen },
  { path: '/search', label: '搜索', icon: Search },
  { path: '/tag', label: '标签', icon: Tag },
  { path: '/share', label: '分享', icon: Share2 },
  { path: '/trash', label: '回收站', icon: Trash2 },
  { path: '/file', label: '文件', icon: FileText },
]

const opsItems = [
  { path: '/ops/dashboard', label: '运维看板', icon: Activity },
  { path: '/ops/host', label: '主机管理', icon: Monitor },
  { path: '/ops/service', label: '服务管理', icon: Server },
  { path: '/ops/log', label: '操作日志', icon: Database },
]

const activePath = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || '')

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <!-- 侧栏 -->
    <aside class="sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
      <div class="logo">
        <span class="logo-icon">📚</span>
        <span v-show="!appStore.sidebarCollapsed" class="logo-text">mykng</span>
      </div>

      <nav class="nav">
        <RouterLink v-for="item in menuItems" :key="item.path" :to="item.path"
          class="nav-item" :class="{ active: activePath === item.path }">
          <component :is="item.icon" :size="20" />
          <span v-show="!appStore.sidebarCollapsed" class="nav-label">{{ item.label }}</span>
        </RouterLink>

        <div v-show="!appStore.sidebarCollapsed" class="nav-section">运维中心</div>
        <RouterLink v-for="item in opsItems" :key="item.path" :to="item.path"
          class="nav-item" :class="{ active: activePath === item.path }">
          <component :is="item.icon" :size="20" />
          <span v-show="!appStore.sidebarCollapsed" class="nav-label">{{ item.label }}</span>
        </RouterLink>

        <div v-show="!appStore.sidebarCollapsed" class="nav-section">系统</div>
        <RouterLink to="/settings" class="nav-item" :class="{ active: activePath === '/settings' }">
          <Settings :size="20" />
          <span v-show="!appStore.sidebarCollapsed" class="nav-label">设置</span>
        </RouterLink>
      </nav>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <!-- 顶栏 -->
      <header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="appStore.toggleSidebar">
            <ChevronLeft v-if="!appStore.sidebarCollapsed" :size="20" />
            <Menu v-else :size="20" />
          </button>
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <div class="user-info">
              <el-avatar :size="32" class="avatar">
                {{ authStore.user?.nickname?.charAt(0) || 'A' }}
              </el-avatar>
              <span class="username">{{ authStore.user?.nickname || 'admin' }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/settings')">个人设置</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <LogOut :size="14" style="margin-right: 4px" /> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.layout {
  display: flex;
  height: 100%;
}

.sidebar {
  width: 240px;
  background: #1a2332;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  flex-shrink: 0;

  &.collapsed {
    width: 64px;
  }
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: #d4a574;
  font-weight: 700;
  font-size: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);

  .logo-icon {
    font-size: 24px;
    flex-shrink: 0;
  }
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.15);
  }
}

.nav-section {
  padding: 16px 20px 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  color: rgba(255, 255, 255, 0.65);
  transition: all 0.2s;
  cursor: pointer;

  &:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.05);
  }

  &.active {
    color: #d4a574;
    background: rgba(212, 165, 116, 0.1);
    border-left: 3px solid #d4a574;
  }
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e8e4e0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: #7f8c8d;
  padding: 4px;
  display: flex;
  align-items: center;

  &:hover {
    color: #1a2332;
  }
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;

  &:hover {
    background: #f5f3f0;
  }
}

.avatar {
  background: #1a2332;
  color: #d4a574;
  font-weight: 600;
}

.username {
  font-size: 14px;
  color: #2c3e50;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
