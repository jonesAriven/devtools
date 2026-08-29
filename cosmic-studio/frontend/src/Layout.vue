<template>
  <el-container class="layout" :class="{ mobile: isMobile }">
    <el-aside v-if="!isMobile" width="210px" class="sidebar">
      <div class="logo">cosmic-studio</div>
      <el-menu :default-active="active" router background-color="#1d2535" text-color="#aeb6c5"
               active-text-color="#409eff">
        <el-menu-item v-for="m in menus" :key="m.key" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-drawer v-if="isMobile" v-model="drawer" direction="ltr" size="200px" :with-header="false">
      <div class="logo">cosmic-studio</div>
      <el-menu :default-active="active" router @select="drawer = false"
               background-color="#1d2535" text-color="#aeb6c5" active-text-color="#409eff">
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
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from './api'

const route = useRoute()
const router = useRouter()
const menus = ref([])
const drawer = ref(false)
const isMobile = ref(window.innerWidth < 768)
const onResize = () => { isMobile.value = window.innerWidth < 768 }
onMounted(() => {
  window.addEventListener('resize', onResize)
  loadMenus()
})
onUnmounted(() => window.removeEventListener('resize', onResize))

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

function onCommand(cmd) {
  if (cmd === 'logout') {
    localStorage.clear()
    router.push('/login')
  }
}
</script>

<style>
html, body, #app { height: 100%; margin: 0; background: #f5f7fa; }
.layout { height: 100%; }
.sidebar { background: #1d2535; }
.logo { color: #fff; font-weight: 700; font-size: 17px; padding: 18px 20px; letter-spacing: .5px; }
.sidebar .el-menu { border-right: none; }
.topbar { display: flex; align-items: center; gap: 10px; border-bottom: 1px solid #e8eaee; background: #fff; }
.topbar .title { font-weight: 600; }
.topbar .spacer { flex: 1; }
.topbar .uname { cursor: pointer; color: #409eff; }
.main { background: #f5f7fa; padding: 16px; }
.burger { font-size: 20px; cursor: pointer; }
@media (max-width: 767px) {
  .main { padding: 10px; }
}
</style>
