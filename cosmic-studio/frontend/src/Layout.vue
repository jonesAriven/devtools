<template>
  <el-container class="layout" :class="{ mobile: isMobile }">
    <el-aside v-if="!isMobile" width="210px" class="sidebar">
      <div class="logo">cosmic-studio</div>
      <el-menu :default-active="active" router aria-label="主导航" v-bind="menuTheme">
        <el-menu-item v-for="m in menus" :key="m.key" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-drawer v-if="isMobile" v-model="drawer" direction="ltr" size="200px" :with-header="false">
      <div class="logo">cosmic-studio</div>
      <el-menu :default-active="active" router aria-label="主导航（移动端）"
               @select="drawer = false" v-bind="menuTheme">
        <el-menu-item v-for="m in menus" :key="m.key" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="topbar" height="52px">
        <el-icon v-if="isMobile" class="burger" @click="drawer = true"><Menu /></el-icon>
        <span class="title">{{ currentTitle }}</span>
        <div class="spacer" />
        <el-tag size="small" type="info">{{ roleName }}</el-tag>
        <el-dropdown @command="onCommand">
          <span class="uname">{{ uname }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <div class="tabbar">
        <el-tabs type="card" v-model="activeTab" @tab-click="onTabClick" @tab-remove="onTabRemove">
          <el-tab-pane v-for="t in tabs" :key="t.path" :name="t.path"
                       :label="titleOf(t.path)" :closable="t.path !== '/'" />
        </el-tabs>
      </div>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from './api'
import { useBreakpoint } from './composables/useBreakpoint'

const route = useRoute()
const router = useRouter()
const menus = ref([])
const drawer = ref(false)
// 断点逻辑收口到 useBreakpoint（此前 Layout / Chat 各写一份，且没清理监听）
const { isMobile } = useBreakpoint()

// 侧栏配色走 CSS 变量，不再把 #1d2535 之类硬编码进模板属性
const menuTheme = {
  backgroundColor: 'var(--c-sidebar-bg)',
  textColor: 'var(--c-sidebar-text)',
  activeTextColor: 'var(--c-sidebar-active)',
}

onMounted(loadMenus)

async function loadMenus() {
  try {
    menus.value = (await api.get('/studio/menus')).data
  } catch (e) { /* 401 由拦截器跳登录 */ }
}
const active = computed(() => '/' + (route.path.split('/')[1] || ''))
const currentTitle = computed(() => {
  const hit = menus.value.find(m => m.path === active.value)
  return hit ? hit.title : 'cosmic-studio'
})
const uname = ref(JSON.parse(localStorage.getItem('user') || '{}').username || '')
const roleName = ref({ admin: '管理员', editor: '编辑', viewer: '只读' }[(JSON.parse(localStorage.getItem('user') || '{}').role)] || '')

// ── 页内多页签（tagsView）：点过的菜单生成页签，keep-alive 保留各页状态 ──
const HOME = { path: '/' }
const tabs = ref([HOME])
const activeTab = ref('/')

function titleOf(path) {
  if (path === '/') return '对话'
  return menus.value.find(m => m.path === path)?.title || path.slice(1)
}
// 页签组件名清单（script-setup 按文件名推断）；ProjectDetail 等详情页不缓存
const COMP_BY_PATH = {
  '/': 'Chat', '/projects': 'Projects', '/archive': 'Archive', '/lint': 'Lint',
  '/versions': 'Versions', '/specs': 'Specs', '/vocab': 'Vocab', '/admin': 'Admin'
}
const cachedViews = computed(() => [...new Set(tabs.value.map(t => COMP_BY_PATH[t.path]).filter(Boolean))])
const isMenuPath = path => path === '/' || menus.value.some(m => m.path === path)

watch(() => route.path, path => {
  if (route.path.startsWith('/login')) return
  if (isMenuPath(path)) {
    if (!tabs.value.some(t => t.path === path)) tabs.value.push({ path })
    activeTab.value = path
  } else {
    // 详情页等子路由：高亮所属菜单页签，不新开页签
    activeTab.value = active.value
  }
}, { immediate: true })

function onTabClick(pane) {
  if (pane.paneName !== route.path) router.push(pane.paneName)
}
function onTabRemove(path) {
  const idx = tabs.value.findIndex(t => t.path === path)
  if (idx < 0) return
  tabs.value.splice(idx, 1)
  if (activeTab.value !== path) return
  const next = tabs.value[idx - 1] || tabs.value[idx] || HOME
  if (next.path !== route.path) router.push(next.path)
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    localStorage.clear()
    tabs.value = [HOME]
    router.push('/login')
  }
}
</script>

<!--
  布局骨架样式（.layout / .sidebar / .topbar / .tabbar / .main …）已全部迁入
  src/styles/theme.css，色值统一走设计令牌。此处不再重复定义，避免两处漂移。
-->
