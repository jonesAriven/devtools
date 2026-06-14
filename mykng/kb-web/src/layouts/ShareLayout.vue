<template>
  <div class="share-layout">
    <header class="share-header">
      <div class="share-logo">
        <el-icon :size="24" color="#409eff"><Reading /></el-icon>
        <span class="share-title">知识库分享</span>
      </div>
    </header>
    <main class="share-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.share-layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

.share-header {
  height: 50px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.share-logo {
  display: flex;
  align-items: center;
  gap: 8px;
}

.share-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.share-content {
  flex: 1;
  padding: 24px;
}
</style>
