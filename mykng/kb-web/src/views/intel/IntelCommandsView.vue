<template>
  <div class="intel-commands">
    <el-alert
      type="warning"
      :closable="false"
      title="命令来源于知识引擎解析的文档，执行前请确认风险"
      show-icon
      class="top-alert"
    />
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="card-title">命令库</span>
          <div class="header-actions">
            <el-select
              v-model="categoryFilter"
              placeholder="分类筛选"
              clearable
              style="width: 150px"
            >
              <el-option
                v-for="c in categoryOptions"
                :key="c"
                :label="c"
                :value="c"
              />
            </el-select>
            <el-select
              v-model="riskFilter"
              placeholder="风险等级"
              clearable
              style="width: 140px"
            >
              <el-option label="LOW" value="LOW" />
              <el-option label="MEDIUM" value="MEDIUM" />
              <el-option label="HIGH" value="HIGH" />
              <el-option label="CRITICAL" value="CRITICAL" />
            </el-select>
            <el-input
              v-model="keyword"
              placeholder="按命令内容筛选"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column label="命令内容" min-width="280">
          <template #default="{ row }">
            <el-text style="font-family: monospace" truncated>{{ row.command }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="110">
          <template #default="{ row }">
            <el-tag :type="riskType(row.riskLevel)">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="osType" label="适用系统" width="120" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              plain
              :icon="DocumentCopy"
              @click="handleCopy(row.command)"
            >复制</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getCommandList } from '@/api/intelligence'
import type { IntelCommand } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh, DocumentCopy } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelCommand[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const categoryFilter = ref('')
const riskFilter = ref('')

const categoryOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach(c => { if (c.category) set.add(c.category) })
  return Array.from(set)
})

const filteredList = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  return list.value.filter(c => {
    if (categoryFilter.value && c.category !== categoryFilter.value) return false
    if (riskFilter.value && c.riskLevel !== riskFilter.value) return false
    if (kw) {
      const cmd = (c.command || '').toLowerCase()
      if (!cmd.includes(kw)) return false
    }
    return true
  })
})

function riskType(level: string) {
  const l = (level || '').toUpperCase()
  if (l === 'LOW') return 'success'
  if (l === 'MEDIUM') return 'warning'
  if (l === 'HIGH') return 'danger'
  if (l === 'CRITICAL') return 'danger'
  return 'info'
}

function handleSearch() {
  appliedKeyword.value = keyword.value
}

async function handleCopy(command: string) {
  try {
    await navigator.clipboard.writeText(command)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCommandList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载命令列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-commands .top-alert {
  margin-bottom: 12px;
}
.intel-commands .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-commands .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-commands .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 768px) {
  .intel-commands .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-commands .header-actions {
    flex-wrap: wrap;
  }
  .intel-commands :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
