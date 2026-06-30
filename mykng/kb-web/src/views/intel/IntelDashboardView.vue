<template>
  <div class="intel-dashboard">
    <div class="dashboard-header">
      <h2 class="dashboard-title">知识引擎看板</h2>
      <div class="header-actions">
        <el-button type="primary" :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="3" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__inner">
            <el-icon class="stat-icon">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-value">{{ stats?.[card.key] ?? 0 }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-4">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>文档类型分布</template>
          <div v-if="docTypeEntries.length" class="doc-type-tags">
            <el-tag
              v-for="[type, count] in docTypeEntries"
              :key="type"
              :type="docTypeColor(type)"
              class="doc-type-tag"
            >
              {{ type }}：{{ count }}
            </el-tag>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>快捷入口</template>
          <div class="quick-entries">
            <el-button :icon="Document" @click="$router.push('/intel/docs')">文档库</el-button>
            <el-button :icon="Cpu" @click="$router.push('/intel/hosts')">主机列表</el-button>
            <el-button :icon="Platform" @click="$router.push('/intel/commands')">命令库</el-button>
            <el-button :icon="Clock" @click="$router.push('/intel/timelines')">时间线</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getStats } from '@/api/intelligence'
import type { IntelStats } from '@/types'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  Document,
  Cpu,
  Connection,
  Position,
  Key,
  Platform,
  Link,
  Clock,
} from '@element-plus/icons-vue'

const loading = ref(false)
const stats = ref<IntelStats | null>(null)

const statCards = [
  { key: 'docCount', label: '文档数', icon: Document },
  { key: 'hostCount', label: '主机数', icon: Cpu },
  { key: 'serviceCount', label: '服务数', icon: Connection },
  { key: 'portCount', label: '端口数', icon: Position },
  { key: 'credentialCount', label: '凭据数', icon: Key },
  { key: 'commandCount', label: '命令数', icon: Platform },
  { key: 'domainCount', label: '域名数', icon: Link },
  { key: 'timelineCount', label: '时间线数', icon: Clock },
] as const

const docTypeEntries = computed<[string, number][]>(() => {
  const types = stats.value?.byType
  if (!types || !Array.isArray(types)) return []
  return types.map(item => [item.docType, item.count])
})

function docTypeColor(type: string) {
  const map: Record<string, string> = {
    TABLE: 'warning',
    PLAN: 'primary',
    TIMELINE: 'success',
    GRAPH: 'info',
    RULE: 'danger',
    GENERAL: 'info',
  }
  return (map[type] || 'info') as any
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStats()
    stats.value = res.data.data
  } catch {
    ElMessage.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.dashboard-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #c9a96e;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.stat-card {
  text-align: center;
}
.stat-card__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 0;
}
.stat-icon {
  font-size: 24px;
  color: #c9a96e;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.doc-type-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.doc-type-tag {
  margin: 0;
}
.quick-entries {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.mt-4 {
  margin-top: 16px;
}

@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .quick-entries {
    gap: 8px;
  }
}
</style>
