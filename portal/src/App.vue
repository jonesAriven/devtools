<template>
  <div class="portal-page">
    <header class="portal-header">
      <h1>devtools 工具看板</h1>
      <p>统一入口 · 系统导航 · 状态监控 · 工具下载</p>
    </header>

    <main class="portal-body">
      <DashboardView />
    </main>

    <footer class="portal-footer">
      devtools Portal &copy; 2026 · Powered by Vue3 + Element Plus
    </footer>
  </div>
</template>

<script setup lang="ts">
import DashboardView from '@/views/DashboardView.vue'
</script>
