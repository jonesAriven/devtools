<template>
  <el-container class="main-layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" :width="appStore.sidebarCollapsed ? '64px' : '240px'" class="sidebar">
      <div class="sidebar-header">
        <span v-if="!appStore.sidebarCollapsed">知识库</span>
        <span v-else>KB</span>
      </div>
      <div class="sidebar-body">
        <el-menu
          :default-active="currentRoute"
          :collapse="appStore.sidebarCollapsed"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          router
        >
          <el-menu-item :index="`${CONTEXT_PATH}/dashboard`">
            <el-icon><DataBoard /></el-icon>
            <template #title>仪表盘</template>
          </el-menu-item>
          <el-menu-item :index="`${CONTEXT_PATH}/trash`">
            <el-icon><Delete /></el-icon>
            <template #title>回收站</template>
          </el-menu-item>
          <el-menu-item :index="`${CONTEXT_PATH}/settings`">
            <el-icon><Setting /></el-icon>
            <template #title>系统设置</template>
          </el-menu-item>
          <el-sub-menu :index="`${CONTEXT_PATH}/ops`">
            <template #title>
              <el-icon><Monitor /></el-icon>
              <span>运维管理</span>
            </template>
            <el-menu-item :index="`${CONTEXT_PATH}/ops`">运维看板</el-menu-item>
            <el-menu-item :index="`${CONTEXT_PATH}/ops/hosts`">主机管理</el-menu-item>
            <el-menu-item :index="`${CONTEXT_PATH}/ops/services`">服务管理</el-menu-item>
            <el-menu-item :index="`${CONTEXT_PATH}/ops/conflicts`">矛盾检测</el-menu-item>
            <el-menu-item :index="`${CONTEXT_PATH}/ops/knowledge`">运维知识</el-menu-item>
          </el-sub-menu>
        </el-menu>

        <div v-if="!appStore.sidebarCollapsed" class="sidebar-section">
          <div class="sidebar-section-title">空间列表</div>
          <el-menu
            :default-active="currentSpaceRoute"
            background-color="#304156"
            text-color="#bfcbd9"
            active-text-color="#409eff"
            router
          >
            <el-menu-item
              v-for="space in spaceStore.spaceList"
              :key="space.id"
              :index="`${CONTEXT_PATH}/space/${space.id}`"
            >
              <el-icon><Folder /></el-icon>
              <template #title>{{ space.name }}</template>
            </el-menu-item>
          </el-menu>
        </div>
      </div>
    </el-aside>

    <!-- 移动端抽屉侧边栏 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      :size="240"
      :with-header="false"
    >
      <div class="sidebar sidebar-drawer">
        <div class="sidebar-header">
          <span>知识库</span>
        </div>
        <div class="sidebar-body">
          <el-menu
            :default-active="currentRoute"
            background-color="#304156"
            text-color="#bfcbd9"
            active-text-color="#409eff"
            router
            @select="drawerVisible = false"
          >
            <el-menu-item :index="`${CONTEXT_PATH}/dashboard`">
              <el-icon><DataBoard /></el-icon>
              <template #title>仪表盘</template>
            </el-menu-item>
            <el-menu-item :index="`${CONTEXT_PATH}/trash`">
              <el-icon><Delete /></el-icon>
              <template #title>回收站</template>
            </el-menu-item>
            <el-menu-item :index="`${CONTEXT_PATH}/settings`">
              <el-icon><Setting /></el-icon>
              <template #title>系统设置</template>
            </el-menu-item>
            <el-sub-menu :index="`${CONTEXT_PATH}/ops`">
              <template #title>
                <el-icon><Monitor /></el-icon>
                <span>运维管理</span>
              </template>
              <el-menu-item :index="`${CONTEXT_PATH}/ops`">运维看板</el-menu-item>
              <el-menu-item :index="`${CONTEXT_PATH}/ops/hosts`">主机管理</el-menu-item>
              <el-menu-item :index="`${CONTEXT_PATH}/ops/services`">服务管理</el-menu-item>
              <el-menu-item :index="`${CONTEXT_PATH}/ops/conflicts`">矛盾检测</el-menu-item>
              <el-menu-item :index="`${CONTEXT_PATH}/ops/knowledge`">运维知识</el-menu-item>
            </el-sub-menu>
          </el-menu>

          <div class="sidebar-section">
            <div class="sidebar-section-title">空间列表</div>
            <el-menu
              :default-active="currentSpaceRoute"
              background-color="#304156"
              text-color="#bfcbd9"
              active-text-color="#409eff"
              router
              @select="drawerVisible = false"
            >
              <el-menu-item
                v-for="space in spaceStore.spaceList"
                :key="space.id"
                :index="`${CONTEXT_PATH}/space/${space.id}`"
              >
                <el-icon><Folder /></el-icon>
                <template #title>{{ space.name }}</template>
              </el-menu-item>
            </el-menu>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-container>
      <el-header class="header-bar" height="50px">
        <div class="header-left">
          <!-- 移动端汉堡菜单 -->
          <el-icon v-if="isMobile" class="collapse-btn" @click="drawerVisible = true">
            <Expand />
          </el-icon>
          <!-- 桌面端折叠按钮 -->
          <el-icon v-else class="collapse-btn" @click="appStore.toggleSidebar()">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <SearchBar />
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" :src="userStore.profile?.avatar" icon="UserFilled" />
              <span class="username">{{ userStore.profile?.nickname || userStore.profile?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
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
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useSpaceStore } from '@/stores/space'
import { useAuth } from '@/composables/useAuth'
import SearchBar from '@/components/SearchBar.vue'
import { CONTEXT_PATH } from '@/config'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const spaceStore = useSpaceStore()
const { logout } = useAuth()

const isMobile = ref(false)
const drawerVisible = ref(false)

const currentRoute = computed(() => route.path)
const currentSpaceRoute = computed(() => {
  if (route.name === 'Space') return route.path
  return ''
})

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  spaceStore.fetchSpaceList()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

async function handleCommand(command: string) {
  if (command === 'logout') {
    await logout()
  } else if (command === 'settings') {
    router.push(`${CONTEXT_PATH}/settings`)
  }
}
</script>

<style scoped lang="scss">
.main-layout {
  height: 100%;
}

.sidebar {
  background-color: #304156;
  overflow: hidden;

  .sidebar-header {
    height: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    font-weight: 600;
    color: #fff;
    border-bottom: 1px solid #3a4a5d;
  }

  .sidebar-body {
    height: calc(100% - 50px);
    overflow-y: auto;
  }

  .sidebar-section {
    border-top: 1px solid #3a4a5d;
  }

  .sidebar-section-title {
    padding: 12px 20px 4px;
    font-size: 12px;
    color: #7a8b9a;
    text-transform: uppercase;
  }
}

.sidebar-drawer {
  height: 100vh;
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
  flex-shrink: 0;

  &:hover {
    color: #409eff;
  }
}

.header-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #606266;

  .username {
    font-size: 14px;
  }

  &:hover {
    color: #409eff;
  }
}

.content-area {
  padding: 16px;
  background-color: #f5f7fa;
  overflow-y: auto;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header-bar {
    padding: 0 12px;
  }

  .header-left {
    gap: 8px;
  }

  .user-info {
    .username {
      display: none;
    }
  }

  .content-area {
    padding: 8px;
  }
}
</style>
