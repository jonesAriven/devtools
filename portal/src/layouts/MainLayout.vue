<template>
  <el-container class="main-layout">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo" @click="$router.push('/')">
          <el-icon :size="28"><Menu /></el-icon>
          <span class="logo-text">devtools 看板</span>
        </div>
        <el-button v-if="isManagePage" class="back-btn" type="primary" plain @click="goBack">
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
        <el-button v-if="!isManagePage" type="primary" plain @click="$router.push('/manage')">
          <el-icon><Setting /></el-icon>
          管理
        </el-button>
        <el-dropdown @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="36" :icon="UserFilled" />
            <span class="username">{{ userStore.username }}</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="changePassword">
                <el-icon><Key /></el-icon>
                修改密码
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

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="handlePasswordSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-container class="layout-body">
      <el-aside v-if="!isManagePage" width="240px" class="layout-aside">
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

      <el-main class="layout-main" :class="{ 'full-width': isManagePage }">
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
import { ref, computed, onMounted, onUnmounted, nextTick, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
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
import { changePassword } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const favoritesStore = useFavoritesStore()
const systemStore = useSystemStore()

const searchText = ref('')
const categories: SystemCategory[] = ['web', 'infra', 'tool', 'doc']
const activeSection = ref<string>('favorites')

const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}
const passwordSubmitting = ref(false)

const isManagePage = computed(() => route.name === 'Manage')

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
  if (command === 'changePassword') {
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordDialogVisible.value = true
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

async function handlePasswordSubmit() {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordSubmitting.value = true
    try {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      passwordDialogVisible.value = false
      userStore.logout()
      router.push('/login')
    } catch (e: any) {
      ElMessage.error(e.message || '密码修改失败')
    } finally {
      passwordSubmitting.value = false
    }
  })
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
</style>
