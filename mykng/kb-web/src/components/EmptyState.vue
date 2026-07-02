<template>
  <div class="empty-state" :class="{ 'empty-state--compact': compact }">
    <div class="empty-icon" :class="`empty-icon--${variant}`">
      <el-icon :size="compact ? 36 : 56">
        <component :is="iconComponent" v-if="iconComponent" />
        <Files v-else />
      </el-icon>
    </div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="description" class="empty-description">{{ description }}</div>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Files } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  icon?: Component
  title?: string
  description?: string
  variant?: 'default' | 'primary' | 'warning' | 'info'
  compact?: boolean
}>(), {
  title: '暂无数据',
  variant: 'default',
  compact: false,
})

const iconComponent = computed(() => props.icon)
</script>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;

  &--compact {
    padding: 24px 16px;
  }
}

.empty-icon {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  background-color: #f5f7fa;
  color: #c0c4cc;

  &--primary {
    background-color: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  &--warning {
    background-color: #fdf6ec;
    color: #e6a23c;
  }

  &--info {
    background-color: #f4f4f5;
    color: #909399;
  }
}

.empty-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}

.empty-description {
  font-size: 13px;
  color: #909399;
  max-width: 320px;
  line-height: 1.6;
  margin-bottom: 20px;
}

.empty-action {
  margin-top: 4px;
}

.empty-state--compact {
  .empty-icon {
    width: 64px;
    height: 64px;
    margin-bottom: 12px;
  }

  .empty-title {
    font-size: 13px;
    margin-bottom: 4px;
  }

  .empty-description {
    font-size: 12px;
    margin-bottom: 12px;
  }
}
</style>
