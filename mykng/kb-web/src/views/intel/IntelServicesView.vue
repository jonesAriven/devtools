<template>
  <div class="intel-services">
    <el-alert
      type="info"
      :closable="false"
      title="数据来源：知识引擎自动解析，只读视图"
      show-icon
      class="top-alert"
    />
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="card-title">服务总览</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="按服务名筛选"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-model="statusFilter"
              placeholder="状态筛选"
              clearable
              style="width: 150px"
            >
              <el-option
                v-for="s in statusOptions"
                :key="s"
                :label="s"
                :value="s"
              />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column prop="name" label="服务名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="serviceType" label="服务类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="hostId" label="主机ID" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getServiceList } from '@/api/intelligence'
import type { IntelService } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelService[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const statusFilter = ref('')

const statusOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach(s => { if (s.status) set.add(s.status) })
  return Array.from(set)
})

const filteredList = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  return list.value.filter(s => {
    if (statusFilter.value && s.status !== statusFilter.value) return false
    if (kw) {
      const name = (s.name || '').toLowerCase()
      if (!name.includes(kw)) return false
    }
    return true
  })
})

function statusType(status: string) {
  const s = (status || '').toLowerCase()
  if (s === 'running') return 'success'
  if (s === 'stopped') return 'info'
  if (s === 'maintenance') return 'warning'
  return 'info'
}

function handleSearch() {
  appliedKeyword.value = keyword.value
}

async function loadData() {
  loading.value = true
  try {
    const res = await getServiceList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载服务列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-services .top-alert {
  margin-bottom: 12px;
}
.intel-services .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-services .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-services .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 768px) {
  .intel-services .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-services .header-actions {
    flex-wrap: wrap;
  }
  .intel-services :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
