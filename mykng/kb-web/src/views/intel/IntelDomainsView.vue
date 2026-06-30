<template>
  <div class="intel-domains">
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
          <span class="card-title">域名总览</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="按域名筛选"
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
        <el-table-column prop="domain" label="主域名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="subDomain" label="子域名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="targetHostId" label="目标主机ID" width="120" />
        <el-table-column prop="targetPort" label="目标端口" width="100" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getDomainList } from '@/api/intelligence'
import type { IntelDomain } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelDomain[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const statusFilter = ref('')

const statusOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach(d => { if (d.status) set.add(d.status) })
  return Array.from(set)
})

const filteredList = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  return list.value.filter(d => {
    if (statusFilter.value && d.status !== statusFilter.value) return false
    if (kw) {
      const domain = (d.domain || '').toLowerCase()
      const subDomain = (d.subDomain || '').toLowerCase()
      if (!domain.includes(kw) && !subDomain.includes(kw)) return false
    }
    return true
  })
})

function statusType(status: string) {
  const s = (status || '').toLowerCase()
  if (s === 'active') return 'success'
  if (s === 'expired') return 'danger'
  if (s === 'pending') return 'warning'
  return 'info'
}

function handleSearch() {
  appliedKeyword.value = keyword.value
}

async function loadData() {
  loading.value = true
  try {
    const res = await getDomainList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载域名列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-domains .top-alert {
  margin-bottom: 12px;
}
.intel-domains .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-domains .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-domains .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 768px) {
  .intel-domains .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-domains .header-actions {
    flex-wrap: wrap;
  }
  .intel-domains :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
