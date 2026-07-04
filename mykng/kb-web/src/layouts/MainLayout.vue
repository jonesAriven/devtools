<template>
  <el-container class="main-layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-header" @click="router.push('/dashboard')">
        <div class="logo-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect x="2" y="6" width="10" height="20" rx="2" fill="#e6a23c"/>
            <rect x="9" y="3" width="10" height="23" rx="2" fill="#67c23a"/>
            <rect x="16" y="8" width="10" height="18" rx="2" fill="#409eff"/>
          </svg>
        </div>
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">mykng</span>
      </div>
      <div class="sidebar-body">
        <el-menu
          :default-active="currentRoute"
          :collapse="appStore.sidebarCollapsed"
          :unique-opened="true"
          :default-openeds="defaultOpeneds"
          background-color="#ffffff"
          text-color="#606266"
          active-text-color="#c9a96e"
          router
          class="sidebar-menu"
        >
          <el-menu-item :index="'/dashboard'">
            <el-icon><Grid /></el-icon>
            <template #title>工作台</template>
          </el-menu-item>
          <el-sub-menu index="kb-group" v-if="showKbGroup">
            <template #title>
              <el-icon><FolderOpened /></el-icon>
              <span>知识库</span>
            </template>
            <el-menu-item :index="'/spaces'" v-if="kbKnowledgeAvailable">
              <el-icon><List /></el-icon>
              <template #title>知识空间</template>
            </el-menu-item>
            <el-menu-item :index="`/space/${spaceStore.currentSpace?.id || ''}`" v-if="spaceStore.currentSpace && kbKnowledgeAvailable">
              <el-icon><FolderOpened /></el-icon>
              <template #title>当前空间</template>
            </el-menu-item>
            <el-menu-item :index="'/stars'" v-if="kbKnowledgeAvailable">
              <el-icon><Star /></el-icon>
              <template #title>我的收藏</template>
            </el-menu-item>
            <el-menu-item :index="'/search'" v-if="kbKnowledgeAvailable">
              <el-icon><Search /></el-icon>
              <template #title>搜索</template>
            </el-menu-item>
            <el-menu-item :index="'/file'" v-if="kbFileAvailable">
              <el-icon><Document /></el-icon>
              <template #title>文件</template>
            </el-menu-item>
            <el-menu-item :index="'/tag'" v-if="kbKnowledgeAvailable">
              <el-icon><PriceTag /></el-icon>
              <template #title>标签</template>
            </el-menu-item>
            <el-menu-item :index="'/share'" v-if="kbKnowledgeAvailable">
              <el-icon><Share /></el-icon>
              <template #title>分享</template>
            </el-menu-item>
            <el-menu-item :index="'/trash'" v-if="kbKnowledgeAvailable">
              <el-icon><Delete /></el-icon>
              <template #title>回收站</template>
            </el-menu-item>
            <el-menu-item :index="'/graph'" v-if="kbKnowledgeAvailable">
              <el-icon><Connection /></el-icon>
              <template #title>知识图谱</template>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="system-group">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统</span>
            </template>
            <el-menu-item :index="'/log'" v-if="kbAuthAvailable">
              <el-icon><Tickets /></el-icon>
              <template #title>操作日志</template>
            </el-menu-item>
            <el-menu-item :index="'/settings'">
              <el-icon><Setting /></el-icon>
              <template #title>设置</template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
    </el-aside>

    <!-- 移动端抽屉侧边栏 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      :size="220"
      :with-header="false"
    >
      <div class="sidebar sidebar-drawer">
        <div class="sidebar-header">
          <div class="logo-icon">
            <svg width="24" height="24" viewBox="0 0 28 28" fill="none">
              <rect x="2" y="6" width="10" height="20" rx="2" fill="#e6a23c"/>
              <rect x="9" y="3" width="10" height="23" rx="2" fill="#67c23a"/>
              <rect x="16" y="8" width="10" height="18" rx="2" fill="#409eff"/>
            </svg>
          </div>
          <span class="logo-text">mykng</span>
        </div>
        <div class="sidebar-body">
          <el-menu
            :default-active="currentRoute"
            :unique-opened="true"
            :default-openeds="defaultOpeneds"
            background-color="#ffffff"
            text-color="#606266"
            active-text-color="#c9a96e"
            router
            class="sidebar-menu"
            @select="drawerVisible = false"
          >
            <el-menu-item :index="'/dashboard'">
              <el-icon><Grid /></el-icon>
              <template #title>工作台</template>
            </el-menu-item>
            <el-sub-menu index="kb-group" v-if="showKbGroup">
              <template #title>
                <el-icon><FolderOpened /></el-icon>
                <span>知识库</span>
              </template>
              <el-menu-item :index="'/spaces'" v-if="kbKnowledgeAvailable">
                <el-icon><List /></el-icon>
                <template #title>知识空间</template>
              </el-menu-item>
              <el-menu-item :index="`/space/${spaceStore.currentSpace?.id || ''}`" v-if="spaceStore.currentSpace && kbKnowledgeAvailable">
                <el-icon><FolderOpened /></el-icon>
                <template #title>当前空间</template>
              </el-menu-item>
              <el-menu-item :index="'/stars'" v-if="kbKnowledgeAvailable">
                <el-icon><Star /></el-icon>
                <template #title>我的收藏</template>
              </el-menu-item>
              <el-menu-item :index="'/search'" v-if="kbKnowledgeAvailable">
                <el-icon><Search /></el-icon>
                <template #title>搜索</template>
              </el-menu-item>
              <el-menu-item :index="'/file'" v-if="kbFileAvailable">
                <el-icon><Document /></el-icon>
                <template #title>文件</template>
              </el-menu-item>
              <el-menu-item :index="'/tag'" v-if="kbKnowledgeAvailable">
                <el-icon><PriceTag /></el-icon>
                <template #title>标签</template>
              </el-menu-item>
              <el-menu-item :index="'/share'" v-if="kbKnowledgeAvailable">
                <el-icon><Share /></el-icon>
                <template #title>分享</template>
              </el-menu-item>
              <el-menu-item :index="'/trash'" v-if="kbKnowledgeAvailable">
                <el-icon><Delete /></el-icon>
                <template #title>回收站</template>
              </el-menu-item>
              <el-menu-item :index="'/graph'" v-if="kbKnowledgeAvailable">
                <el-icon><Connection /></el-icon>
                <template #title>知识图谱</template>
              </el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="system-group">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>系统</span>
              </template>
              <el-menu-item :index="'/settings'">
                <el-icon><Setting /></el-icon>
                <template #title>设置</template>
              </el-menu-item>
            </el-sub-menu>
          </el-menu>
        </div>
      </div>
    </el-drawer>

    <el-container>
      <el-header class="header-bar" height="56px">
        <div class="header-left">
          <el-icon v-if="isMobile" class="header-btn" @click="drawerVisible = true">
            <Expand />
          </el-icon>
          <el-icon v-else class="header-btn" @click="appStore.toggleSidebar()">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-icon v-if="canGoBack" class="header-btn back-btn" @click="goBack">
            <ArrowLeft />
          </el-icon>
          <Breadcrumb v-if="!isMobile" class="header-breadcrumb" />
          <span v-else class="header-title">{{ pageTitle }}</span>
        </div>
        <!-- 全局搜索框（桌面端） -->
        <div v-if="!isMobile" class="header-center">
          <SearchBar />
        </div>
        <div class="header-right">
          <el-tooltip :content="appStore.theme === 'dark' ? '切换到明亮模式' : '切换到暗黑模式'" placement="bottom" effect="dark">
            <el-icon class="header-btn theme-toggle-btn" @click="appStore.toggleTheme()">
              <Sunny v-if="appStore.theme === 'dark'" />
              <Moon v-else />
            </el-icon>
          </el-tooltip>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="30" :src="userStore.profile?.avatar" class="user-avatar">
                <span class="avatar-letter">{{ (userStore.profile?.nickname || userStore.profile?.username || 'U')[0].toUpperCase() }}</span>
              </el-avatar>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人信息
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>系统设置
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
    <BackToTop />

    <!-- 全局浮动创建按钮 -->
    <div class="fab-container">
      <el-tooltip content="新建" placement="left" effect="dark">
        <el-button type="primary" class="fab-main-btn" circle @click="showCreateMenu = !showCreateMenu">
          <el-icon :size="22"><Plus /></el-icon>
        </el-button>
      </el-tooltip>
      <transition name="fab-pop">
        <div v-show="showCreateMenu" class="fab-menu">
          <div class="fab-menu-item" @click="handleCreateDoc">
            <el-icon><EditPen /></el-icon>
            <span>新建文档</span>
          </div>
          <div class="fab-menu-item" @click="handleCreateSpace">
            <el-icon><FolderOpened /></el-icon>
            <span>新建空间</span>
          </div>
        </div>
      </transition>
    </div>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useSpaceStore } from '@/stores/space'
import { useModuleStore } from '@/stores/module'
import { useAuth } from '@/composables/useAuth'
import BackToTop from '@/components/BackToTop.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const spaceStore = useSpaceStore()
const moduleStore = useModuleStore()
const { logout } = useAuth()

// 模块动态菜单：各菜单项依赖对应微服务模块的可用性
const kbKnowledgeAvailable = computed(() => moduleStore.isModuleAvailable('kb-knowledge'))
const kbFileAvailable = computed(() => moduleStore.isModuleAvailable('kb-file'))
const kbAuthAvailable = computed(() => moduleStore.isModuleAvailable('kb-auth'))
// 知识库分组同时包含 kb-knowledge 与 kb-file 依赖项，任一可用即显示分组
const showKbGroup = computed(() => kbKnowledgeAvailable.value || kbFileAvailable.value)

const isMobile = ref(false)
const drawerVisible = ref(false)
const showCreateMenu = ref(false)

const currentRoute = computed(() => route.path)

// 根据当前路由自动展开对应的菜单分组
const defaultOpeneds = computed<string[]>(() => {
  const path = route.path
  const groups: string[] = []
  if (path.startsWith('/space') || path.startsWith('/spaces') || path.startsWith('/search') || path.startsWith('/tag') ||
      path.startsWith('/share') || path.startsWith('/trash') || path.startsWith('/file') ||
      path.startsWith('/doc') || path.startsWith('/web') || path.startsWith('/graph') || path.startsWith('/stars')) {
    groups.push('kb-group')
  }
  if (path.startsWith('/settings') || path.startsWith('/log')) {
    groups.push('system-group')
  }
  return groups
})

const pageTitleMap: Record<string, string> = {
  dashboard: '工作台',
  search: '搜索',
  tag: '标签',
  share: '分享中心',
  trash: '回收站',
  file: '文件',
  settings: '设置',
  'doc-create': '新建文档',
  'doc-edit': '编辑文档',
  'file-detail': '文件详情',
  space: '知识空间',
  OperationLog: '操作日志',
  web: '网页详情',
  Graph: '知识图谱',
  Stars: '我的收藏',
}

const pageTitle = computed(() => {
  const name = route.name as string
  if (name === 'Space' && route.params.spaceId) return '知识空间'
  if (name === 'DocEdit') return '编辑文档'
  if (name === 'FileDetail') return '文件详情'
  if (name === 'WebDetail') return '网页详情'
  return pageTitleMap[name] || '工作台'
})

const canGoBack = computed(() => {
  return route.name !== 'Dashboard' && window.history.length > 1
})

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('click', handleClickOutside)
  spaceStore.fetchSpaceList()
  if (!userStore.profile) {
    userStore.fetchProfile()
  }
  // 拉取模块状态用于动态菜单（若 main.ts 已拉取则刷新一次，失败时内部降级）
  moduleStore.fetchModules()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('click', handleClickOutside)
})

// 全局键盘快捷键：Ctrl+K 聚焦搜索
function handleGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    const searchInput = document.querySelector('.header-center .el-input__inner') as HTMLInputElement
    if (searchInput) {
      searchInput.focus()
    } else {
      router.push('/search')
    }
  }
}

async function handleCommand(command: string) {
  if (command === 'logout') {
    await logout()
  } else if (command === 'settings') {
    router.push('/settings')
  } else if (command === 'profile') {
    router.push('/settings')
  }
}

function handleCreateDoc() {
  showCreateMenu.value = false
  router.push('/doc/create')
}

function handleCreateSpace() {
  showCreateMenu.value = false
  router.push('/spaces')
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.fab-container')) {
    showCreateMenu.value = false
  }
}
</script>

<style scoped lang="scss">
.main-layout {
  height: 100%;
}

.sidebar {
  background-color: #fff;
  border-right: 1px solid #e8e8e8;
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
    border-bottom: 1px solid #e8e8e8;
    flex-shrink: 0;

    .logo-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
    }

    .logo-text {
      font-size: 20px;
      font-weight: 700;
      color: #c9a96e;
      letter-spacing: 1px;
    }
  }

  .sidebar-body {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
  }

  .sidebar-menu {
    border-right: none;
    background-color: #fff;

    :deep(.el-menu-item) {
      color: #606266;
      &:hover {
        background-color: #f5f5f5;
        color: #c9a96e;
      }
      &.is-active {
        color: #c9a96e;
        background-color: #fdf6ec;
        font-weight: 600;
      }
    }

    :deep(.el-sub-menu__title) {
      color: #606266;
      &:hover {
        background-color: #f5f5f5;
        color: #c9a96e;
      }
    }

    &:not(.el-menu--collapse) {
      width: 100%;
    }
  }
}

.sidebar-drawer {
  height: 100vh;
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background-color: #fff;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: none;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  min-width: 0;
}

.header-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 480px;
  margin: 0 20px;
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
    color: #c9a96e;
    background-color: #f5f5f5;
  }
}

.back-btn {
  font-size: 16px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-left: 4px;
}

.header-breadcrumb {
  margin-left: 8px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
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
    color: #c9a96e;
  }
}

.user-avatar {
  background-color: #c9a96e;
  color: #fff;
  font-size: 14px;
  font-weight: 600;

  .avatar-letter {
    line-height: 30px;
  }
}

.content-area {
  padding: 0;
  background-color: #faf8f5;
  overflow-y: auto;
}

.fab-container {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 1000;
  display: flex;
  flex-direction: column-reverse;
  align-items: center;
  gap: 12px;
}

.fab-main-btn {
  width: 56px !important;
  height: 56px !important;
  box-shadow: 0 4px 16px rgba(201, 169, 110, 0.4);
  background-color: #c9a96e !important;
  border-color: #c9a96e !important;
  transition: all 0.3s ease;

  &:hover {
    transform: scale(1.08) rotate(90deg);
    box-shadow: 0 6px 20px rgba(201, 169, 110, 0.5);
  }

  &:deep(.el-icon) {
    transition: transform 0.3s ease;
  }
}

.fab-menu {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 4px;
}

.fab-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  font-size: 14px;
  color: #303133;

  &:hover {
    background-color: #faf8f5;
    color: #c9a96e;
    transform: translateX(-2px);
  }

  .el-icon {
    font-size: 18px;
  }
}

.fab-pop-enter-active,
.fab-pop-leave-active {
  transition: all 0.25s ease;
}

.fab-pop-enter-from,
.fab-pop-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

@media (max-width: 768px) {
  .fab-container {
    right: 20px;
    bottom: 20px;
  }

  .fab-main-btn {
    width: 52px !important;
    height: 52px !important;
  }
}
</style>
