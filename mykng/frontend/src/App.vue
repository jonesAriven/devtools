<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

onMounted(() => {
  // 应用启动时若已登录，刷新用户信息（覆盖 localStorage 中的旧缓存，避免昵称等字段过期/乱码）
  if (authStore.accessToken) {
    authStore.fetchProfile()
  }
})
</script>

<template>
  <RouterView v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </RouterView>
</template>
