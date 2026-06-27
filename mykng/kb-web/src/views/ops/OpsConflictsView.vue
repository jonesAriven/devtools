<template>
  <div class="ops-conflicts">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>矛盾检测</span>
          <el-button type="primary" :loading="detecting" @click="handleDetect">手动检测</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="type" label="类型" width="180">
          <template #default="{ row }">
            <el-tag :type="conflictTypeColor(row.type) as any">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'HIGH' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务" width="120" />
        <el-table-column prop="hostName" label="主机" width="120" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="resolved" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'danger'">{{ row.resolved ? '已解决' : '未解决' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="检测时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.resolved" text type="success" @click="handleResolve(row.id)">标记解决</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getConflictList, triggerConflictDetection, resolveConflict } from '@/api/ops'
import type { OpsConflict } from '@/types'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const detecting = ref(false)
const list = ref<OpsConflict[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await getConflictList({ page: 1, size: 100 })
    list.value = res.data.data.list
  } catch { ElMessage.error('加载矛盾列表失败') }
  finally { loading.value = false }
}

async function handleDetect() {
  detecting.value = true
  try {
    const res = await triggerConflictDetection()
    list.value = res.data.data
    ElMessage.success(`检测完成，发现 ${res.data.data.length} 个矛盾`)
  } catch { ElMessage.error('检测失败') }
  finally { detecting.value = false }
}

async function handleResolve(id: number) {
  await resolveConflict(id)
  ElMessage.success('已标记为已解决')
  loadData()
}

function conflictTypeColor(type: string) {
  const map: Record<string, string> = {
    VERSION_MISMATCH: 'danger',
    PORT_CONFLICT: 'danger',
    HOST_DOWN_SERVICE_RUNNING: 'warning',
    DUPLICATE_HOST_IP: 'warning',
    MISSING_DEPENDENCY: 'info',
    DUPLICATE_SERVICE_NAME: 'warning',
  }
  return map[type] || 'info'
}

onMounted(loadData)
</script>

<style scoped>
.header-bar { display: flex; justify-content: space-between; align-items: center; }

@media (max-width: 768px) {
  .header-bar { flex-wrap: wrap; gap: 8px; }
  .ops-conflicts :deep(.el-table) { font-size: 12px; }
}
</style>
