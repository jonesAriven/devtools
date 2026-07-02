<template>
  <div class="not-found">
    <div class="not-found__content">
      <div class="not-found__code">404</div>
      <div class="not-found__text">页面走丢了</div>
      <div class="not-found__desc">您访问的页面不存在或已被移动</div>
      <el-button type="primary" @click="goHome">返回首页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/dashboard')
}
</script>

<style scoped lang="scss">
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 48px 16px;

  &__content {
    text-align: center;
  }

  &__code {
    font-size: 96px;
    font-weight: 700;
    color: #409eff;
    line-height: 1;
    margin-bottom: 16px;
  }

  &__text {
    font-size: 20px;
    color: #303133;
    margin-bottom: 8px;
  }

  &__desc {
    font-size: 14px;
    color: #909399;
    margin-bottom: 24px;
  }
}
</style>
