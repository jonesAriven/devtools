<template>
  <div class="dashboard">
    <!-- 状态总览 -->
    <div class="overview-bar">
      <div class="overview-stats">
        <div class="stat-item">
          <span class="stat-value">{{ totalSystems }}</span>
          <span class="stat-label">总系统数</span>
        </div>
        <div class="stat-item online">
          <span class="stat-value">{{ onlineCount }}</span>
          <span class="stat-label">在线</span>
        </div>
        <div class="stat-item offline">
          <span class="stat-value">{{ offlineCount }}</span>
          <span class="stat-label">离线</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ unknownCount }}</span>
          <span class="stat-label">未检测</span>
        </div>
      </div>
      <el-button type="primary" :loading="checking" @click="runHealthCheck">
        <el-icon><Refresh /></el-icon>
        {{ checking ? '检测中...' : '刷新状态' }}
      </el-button>
    </div>

    <!-- 分类展示 -->
    <template v-for="cat in categories" :key="cat">
      <div class="section-title">
        <el-icon>
          <component :is="categoryIcons[cat]" />
        </el-icon>
        {{ categoryLabels[cat] }}
        <span class="section-count">({{ getSystemsByCategory(cat).length }})</span>
      </div>
      <div class="cards-grid">
        <SystemCard
          v-for="sys in getSystemsByCategory(cat)"
          :key="sys.id"
          :config="sys"
          :status="healthMap.get(sys.id)?.status || 'unknown'"
          :latency="healthMap.get(sys.id)?.latency"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { systems, categoryLabels, categoryIcons, type SystemCategory, type SystemStatus } from '@/config/systems'
import { checkHealth, type HealthResult } from '@/api/health'
import SystemCard from '@/components/SystemCard.vue'

const categories: SystemCategory[] = ['web', 'infra', 'tool', 'doc']
const healthMap = ref<Map<string, HealthResult>>(new Map())
const checking = ref(false)

const totalSystems = computed(() => systems.length)
const onlineCount = computed(() => countByStatus('online'))
const offlineCount = computed(() => countByStatus('offline'))
const unknownCount = computed(() => systems.length - onlineCount.value - offlineCount.value)

function countByStatus(status: SystemStatus): number {
  let count = 0
  for (const sys of systems) {
    if (sys.healthCheckUrl) {
      if (healthMap.value.get(sys.id)?.status === status) count++
    }
  }
  return count
}

function getSystemsByCategory(cat: SystemCategory) {
  return systems.filter((s) => s.category === cat)
}

async function runHealthCheck() {
  checking.value = true
  try {
    const result = await checkHealth(systems)
    healthMap.value = result
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  runHealthCheck()
})
</script>

<style scoped lang="scss">
.overview-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 8px;
}

.overview-stats {
  display: flex;
  gap: 32px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;

  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: #303133;
  }

  .stat-label {
    font-size: 12px;
    color: #909399;
  }

  &.online .stat-value {
    color: #67c23a;
  }

  &.offline .stat-value {
    color: #f56c6c;
  }
}

.section-count {
  font-size: 14px;
  font-weight: 400;
  color: #909399;
  margin-left: 4px;
}

@media (max-width: 768px) {
  .overview-bar {
    flex-direction: column;
    gap: 16px;
    padding: 16px;
  }

  .overview-stats {
    gap: 20px;
  }

  .stat-item .stat-value {
    font-size: 22px;
  }
}
</style>
