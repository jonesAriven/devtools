<template>
  <el-container class="layout" :class="{ mobile: isMobile }">
    <el-aside v-if="!isMobile" width="210px" class="sidebar">
      <div class="logo">cosmic-studio</div>
      <el-menu :default-active="active" aria-label="主导航" v-bind="menuTheme" @select="onMenuSelect">
        <el-menu-item v-for="m in menus" :key="m.key" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-drawer v-if="isMobile" v-model="drawer" direction="ltr" size="200px" :with-header="false">
      <div class="logo">cosmic-studio</div>
      <el-menu :default-active="active" aria-label="主导航（移动端）"
               @select="onMenuSelect" v-bind="menuTheme">
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
          <!--
            keep-alive 让所有访问过的页面常驻内存，组件内的 ref（搜索词/展开行/选中项/表单草稿/
            滚动位置…）全部自动保持，无需逐个加 localStorage。
            关键：不要给 <component> 加 :key="route.fullPath"——key 一变 Vue 就当作新组件，
            keep-alive 会销毁旧实例、新建一个，所有 ref 归零。
            详情页切换不同 id（如 /projects/1 → /projects/2）需要在页面内 watch route.params.id 重拉数据。
            不加 :include：component name 在 script setup 下推断不稳定，全缓存更可靠。
            cosmic-studio 10 个视图级别，缓存全部完全可承受。
          -->
          <keep-alive>
            <component :is="Component" />
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
import { getSubRoute, memorySnapshot, rememberSubRoute } from './composables/useMenuState'
import { useBreakpoint } from './composables/useBreakpoint'
import { navLog } from './utils/navLog'

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
    // 菜单异步加载完再补一次页签同步：首屏直链 /projects 时，watch 的 immediate
    // 在 menus 空时跑过、漏加了页签，这里补上，否则 tab 栏只显示「对话」
    // 菜单异步加载完再补一次页签同步：watch 的 immediate 跑在 menus 为空时，
    // 此时 isMenuPath 恒 false，会漏掉首屏直链（/projects 或 /projects/1）的页签，
    // 表现为顶部 tab 栏只剩「对话」。这里按当前路由补一次。
    const p = route.path
    activeTab.value = isMenuPath(p) ? p : active.value
    ensureTab(isMenuPath(p) ? p : active.value)
    navLog('menus-loaded', { path: p, activeTab: activeTab.value, tabs: tabs.value.map(t => t.path) })
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
// 详情页也走 keep-alive（不再用 :include 显式列出）—— 切回时筛选/视图模式/展开节点全保留
const isMenuPath = path => path === '/' || menus.value.some(m => m.path === path)

// 确保某个菜单页签存在（不存在则追加）。菜单是异步加载的，首屏直链详情页时
// 所属菜单页签可能漏生成，导致顶部根本没这个页签可点。
function ensureTab(path) {
  if (!path || path === '/login') return
  if (tabs.value.some(t => t.path === path)) return
  tabs.value.push({ path })
  navLog('tab-add', { path, tabs: tabs.value.map(t => t.path) })
}

watch(() => route.path, path => {
  if (route.path.startsWith('/login')) return
  // 记住每个菜单上次停留的子路由（详情页也算），切回该菜单时直接回到这里
  rememberSubRoute(active.value, path)
  const isMenu = isMenuPath(path)
  if (isMenu) {
    ensureTab(path)
    activeTab.value = path
  } else {
    // 详情页等子路由：高亮所属菜单页签，不新开页签；但页签本身要保证存在
    ensureTab(active.value)
    activeTab.value = active.value
  }
  navLog('route-change', {
    path,
    menu: active.value,
    kind: isMenu ? 'menu-root' : 'sub-route',
    activeTab: activeTab.value,
    tabs: tabs.value.map(t => t.path),
    memory: memorySnapshot(),
  })
}, { immediate: true })

// 点顶部页签：与点侧栏菜单走同一套「回到该菜单上次停留的子路由」逻辑。
// ⚠️ 此前这里直接 push 页签自身的路径（菜单根），于是从详情页切走再点页签回来
// 会掉回列表页，用户得重新点「进入」——#56 的第二处根因。
function onTabClick(pane) {
  const target = getSubRoute(pane.paneName)
  navLog('tab-click', { pane: pane.paneName, target, from: route.path, memory: memorySnapshot() })
  if (target !== route.path) router.push(target)
}

// 点侧栏/抽屉菜单：回到该菜单上次停留的子路由（如编写库上次打开的项目详情），
// 而非永远回列表根
function onMenuSelect(index) {
  drawer.value = false
  const target = getSubRoute(index)
  navLog('menu-click', { index, target, from: route.path, memory: memorySnapshot() })
  if (target !== route.path) router.push(target)
}

function onTabRemove(path) {
  const idx = tabs.value.findIndex(t => t.path === path)
  if (idx < 0) return
  tabs.value.splice(idx, 1)
  navLog('tab-remove', { path, tabs: tabs.value.map(t => t.path) })
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
