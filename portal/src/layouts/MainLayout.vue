<template>
  <el-container class="main-layout">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo" @click="$router.push('/')">
          <el-icon :size="28"><Menu /></el-icon>
          <span class="logo-text">devtools 看板</span>
        </div>
        <el-button v-if="isFullWidthPage" class="back-btn" type="primary" plain @click="goBack">
          <el-icon><Back /></el-icon>
          返回首页
        </el-button>
      </div>
      <div class="header-center">
        <el-input
          v-model="searchText"
          placeholder="搜索系统..."
          size="large"
          clearable
          :prefix-icon="Search"
          class="search-input"
          @input="handleSearch"
        />
      </div>
      <div class="header-right">
        <el-button v-if="!isFullWidthPage" type="primary" plain @click="$router.push('/manage')">
          <el-icon><Setting /></el-icon>
          管理
        </el-button>
        <el-button
          v-if="userStore.isAdmin && !isConsolePage"
          type="warning"
          plain
          @click="$router.push('/admin')"
        >
          <el-icon><Setting /></el-icon>
          统一认证中心
        </el-button>
        <el-dropdown @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="36" :icon="UserFilled" />
            <span class="username">{{ userStore.username }}</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="userStore.isAdmin" command="adminConsole">
                <el-icon><Setting /></el-icon>
                统一认证中心
                <span class="item-hint">平台作用域</span>
              </el-dropdown-item>
              <el-dropdown-item v-if="userStore.isAdmin" command="users">
                <el-icon><UserFilled /></el-icon>
                门户用户
                <span class="item-hint">应用作用域</span>
              </el-dropdown-item>
              <!-- Phase 12 · P1-4：口令真源在统一认证中心，本地改密端点已下线（410）。
                   改为直达中心「忘记密码」自助找回，避免用户误以为在此可改平台口令。 -->
              <el-dropdown-item command="resetPassword">
                <el-icon><Key /></el-icon>
                重置密码
                <span class="item-hint">走统一认证</span>
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-body">
      <el-aside v-if="!isFullWidthPage" width="240px" class="layout-aside">
        <div class="sidebar">
          <div class="sidebar-section">
            <div class="sidebar-title">
              <el-icon><Star /></el-icon>
              快捷导航
            </div>
            <div
              class="sidebar-item"
              :class="{ active: activeSection === 'favorites' }"
              @click="handleNavClick('favorites')"
            >
              <el-icon><Star /></el-icon>
              <span>我的收藏</span>
            </div>
          </div>

          <div class="sidebar-section">
            <div class="sidebar-title">
              <el-icon><Menu /></el-icon>
              系统分类
            </div>
            <div
              v-for="cat in categories"
              :key="cat"
              class="sidebar-item"
              :class="{ active: activeSection === `category-${cat}` }"
              @click="handleCategoryClick(cat)"
            >
              <el-icon>
                <component :is="categoryIcons[cat]" />
              </el-icon>
              <span>{{ categoryLabels[cat] }}</span>
              <span class="count-badge">{{ getCategoryCount(cat) }}</span>
            </div>
          </div>
        </div>
      </el-aside>

      <el-main class="layout-main" :class="{ 'full-width': isFullWidthPage }">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="$route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Setting,
  UserFilled,
  ArrowDown,
  SwitchButton,
  Menu,
  Star,
  Back,
  Key
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useFavoritesStore } from '@/stores/favorites'
import { useSystemStore } from '@/stores/system'
import { categoryLabels, categoryIcons, type SystemCategory } from '@/config/systems'
import { OIDC_ISSUER } from '@/config/runtime'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const favoritesStore = useFavoritesStore()
const systemStore = useSystemStore()

const searchText = ref('')
const categories: SystemCategory[] = ['web', 'infra', 'tool', 'doc']
const activeSection = ref<string>('favorites')

const isManagePage = computed(() => route.name === 'Manage')
/** 「统一认证中心」页（用于隐藏重复入口按钮） */
const isConsolePage = computed(() => route.name === 'AdminConsole')
/**
 * 全宽页（隐藏门户分类侧边栏）—— 系统管理 / 门户用户 / 统一认证中心
 * 都属于「后台管理」场景，门户的「系统分类」导航对它们无意义。
 */
const isFullWidthPage = computed(
  () => isManagePage.value || route.name === 'Users' || isConsolePage.value
)

function handleSearch(value: string) {
  document.dispatchEvent(new CustomEvent('portal-search', { detail: { keyword: value } }))
}

function handleCategoryClick(cat: SystemCategory) {
  const sectionId = `category-${cat}`
  setActiveSection(sectionId)
  document.dispatchEvent(new CustomEvent('portal-category-click', { detail: { category: cat } }))
}

function handleNavClick(id: string) {
  setActiveSection(id)
  scrollToSection(id)
}

function setActiveSection(id: string) {
  activeSection.value = id
}

function scrollToSection(id: string) {
  nextTick(() => {
    const el = document.getElementById(id)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  })
}

function getCategoryCount(cat: SystemCategory): number {
  return systemStore.getCountByCategory(cat)
}

function handleCommand(command: string) {
  if (command === 'adminConsole') {
    router.push('/admin')
  } else if (command === 'users') {
    router.push('/users')
  } else if (command === 'resetPassword') {
    // 口令真源在中心：跳中心「忘记密码」自助找回（邮箱验证码四步流程），不走本地端点
    window.open(`${OIDC_ISSUER}/forgot-password.html`, '_blank', 'noopener')
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      router.push('/login')
    }).catch(() => {})
  }
}

function goBack() {
  router.push('/')
}

function handleScroll() {
  if (isManagePage.value) return
  const mainEl = document.querySelector('.layout-main')
  if (!mainEl) return
  const scrollTop = mainEl.scrollTop
  const sectionIds = ['favorites', 'category-web', 'category-infra', 'category-tool', 'category-doc']
  for (const id of sectionIds) {
    const el = document.getElementById(id)
    if (el) {
      const rect = el.getBoundingClientRect()
      const mainRect = mainEl.getBoundingClientRect()
      if (rect.top - mainRect.top <= 100) {
        activeSection.value = id
      }
    }
  }
}

let scrollListener: (() => void) | null = null

onMounted(() => {
  systemStore.fetchSystems()
  nextTick(() => {
    const mainEl = document.querySelector('.layout-main')
    if (mainEl) {
      scrollListener = handleScroll
      mainEl.addEventListener('scroll', scrollListener)
    }
  })
})

onUnmounted(() => {
  if (scrollListener) {
    const mainEl = document.querySelector('.layout-main')
    if (mainEl) {
      mainEl.removeEventListener('scroll', scrollListener)
    }
  }
})
</script>

<style scoped lang="scss">
.main-layout {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0 24px;
  height: 64px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  :deep(.el-header) {
    padding: 0;
  }
}

.header-left {
  flex-shrink: 0;

  .logo {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #fff;
    cursor: pointer;

    .logo-text {
      font-size: 18px;
      font-weight: 600;
    }
  }
}

.header-center {
  flex: 1;
  max-width: 500px;
  margin: 0 32px;

  .search-input {
    :deep(.el-input__wrapper) {
      border-radius: 20px;
    }
  }
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  cursor: pointer;

  .username {
    font-size: 14px;
  }

  .arrow-icon {
    font-size: 12px;
  }
}

.layout-body {
  overflow: hidden;
}

.layout-aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
  overflow-y: auto;
}

.sidebar {
  padding: 16px 0;
}

.sidebar-section {
  margin-bottom: 20px;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 20px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s;

  &:hover {
    background: #f5f7fa;
    color: #667eea;
  }

  &.active {
    background: #ecf5ff;
    color: #667eea;
    font-weight: 500;
    border-right: 3px solid #667eea;
  }

  .count-badge {
    margin-left: auto;
    background: #f0f0f0;
    color: #909399;
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 10px;
  }
}

.layout-main {
  background: #f5f7fa;
  padding: 24px;
  overflow-y: auto;

  &.full-width {
    padding: 24px;
  }
}

.back-btn {
  margin-left: 20px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 下拉项里的作用域提示（区分「平台作用域 / 应用作用域」两个用户管理入口） */
.item-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
