<template>
  <el-container style="height: 100vh">
    <el-header class="header">
      <div class="header-left">
        <span class="header-title">FRP 管理平台</span>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            {{ username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container>
      <el-aside width="200px" class="aside">
        <el-menu :default-active="activeMenu" router>
          <el-menu-item index="/servers">
            <el-icon><Monitor /></el-icon>
            <span>服务端管理</span>
          </el-menu-item>
          <el-menu-item index="/clients">
            <el-icon><Connection /></el-icon>
            <span>客户端管理</span>
          </el-menu-item>
          <el-menu-item index="/tunnels">
            <el-icon><Share /></el-icon>
            <span>隧道管理</span>
          </el-menu-item>
          <el-menu-item index="/users" v-if="role === 'ADMIN'">
            <el-icon><UserFilled /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Monitor, Connection, Share, UserFilled, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const username = localStorage.getItem('username') || ''
const role = localStorage.getItem('role') || 'USER'

const activeMenu = computed(() => route.path)

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示').then(() => {
      localStorage.clear()
      router.push('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #409eff;
  color: white;
  padding: 0 20px;
  height: 60px;
}
.header-title {
  font-size: 20px;
  font-weight: bold;
}
.user-info {
  cursor: pointer;
  color: white;
  display: flex;
  align-items: center;
  gap: 4px;
}
.aside {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}
.main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
