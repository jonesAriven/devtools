<template>
  <span class="status-badge" :class="statusClass">
    <span class="status-dot"></span>
    <span class="status-text">{{ statusText }}</span>
    <span v-if="latency !== undefined && status === 'online'" class="status-latency">
      {{ latency }}ms
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SystemStatus } from '@/config/systems'

const props = defineProps<{
  status: SystemStatus
  latency?: number
}>()

const statusClass = computed(() => `is-${props.status}`)

const statusText = computed(() => {
  switch (props.status) {
    case 'online':
      return '在线'
    case 'offline':
      return '离线'
    case 'checking':
      return '检测中'
    default:
      return '未知'
  }
})
</script>

<style scoped lang="scss">
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.is-online {
  color: #67c23a;
  background: #f0f9eb;
  .status-dot {
    background: #67c23a;
  }
}

.is-offline {
  color: #f56c6c;
  background: #fef0f0;
  .status-dot {
    background: #f56c6c;
  }
}

.is-checking {
  color: #e6a23c;
  background: #fdf6ec;
  .status-dot {
    background: #e6a23c;
    animation: pulse 1s infinite;
  }
}

.is-unknown {
  color: #909399;
  background: #f4f4f5;
  .status-dot {
    background: #909399;
  }
}

.status-latency {
  opacity: 0.7;
  font-size: 11px;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
</style>
