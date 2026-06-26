<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search, RefreshCw } from 'lucide-vue-next'
import { logApi } from '@/api/ops'
import type { OperationLog } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<OperationLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filterAction = ref('')
const keyword = ref('')

const actionOptions = [
  { label: '全部操作', value: '' },
  { label: '创建', value: 'create' },
  { label: '更新', value: 'update' },
  { label: '删除', value: 'delete' },
  { label: '登录', value: 'login' },
  { label: '分享', value: 'share' },
  { label: '恢复', value: 'restore' },
]

// 用户名搜索为前端筛选（API 仅支持 action 过滤）
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((item) => (item.username || '').toLowerCase().includes(kw))
})

async function loadList() {
  loading.value = true
  try {
    const res = await logApi.list({
      page: page.value,
      size: size.value,
      action: filterAction.value || undefined,
    })
    list.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function onActionChange() {
  page.value = 1
  loadList()
}

function onPageChange(p: number) {
  page.value = p
  loadList()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  loadList()
}

function actionTag(action: string) {
  const map: Record<string, string> = {
    create: 'success', update: 'warning', delete: 'danger',
    login: 'info', share: '', restore: 'success',
  }
  return map[action] || ''
}

function actionLabel(action: string) {
  const map: Record<string, string> = {
    create: '创建', update: '更新', delete: '删除',
    login: '登录', share: '分享', restore: '恢复',
  }
  return map[action] || action
}

function typeLabel(type: string) {
  const map: Record<string, string> = { doc: '文档', folder: '目录', page: '网页', file: '文件' }
  return map[type] || type
}

onMounted(loadList)
</script>

<template>
  <div class="log-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="title">操作日志</h2>
        <p class="desc">记录系统内全部操作行为，便于追溯审计</p>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-label">操作类型</span>
          <el-select v-model="filterAction" style="width: 140px" @change="onActionChange">
            <el-option v-for="opt in actionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">用户名</span>
          <el-input
            v-model="keyword"
            placeholder="搜索用户名"
            clearable
            style="width: 200px"
            :prefix-icon="Search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        </div>
        <el-button :icon="RefreshCw" @click="loadList">刷新</el-button>
      </div>

      <el-table :data="filteredList" stripe style="width: 100%" class="log-table">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="用户" prop="username" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="actionTag(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源类型" width="100" align="center">
          <template #default="{ row }">{{ typeLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column label="资源ID" prop="resourceId" width="90" align="center" />
        <el-table-column label="详情" prop="detail" min-width="220" show-overflow-tooltip />
        <el-table-column label="IP" prop="ip" width="140" />
        <template #empty>
          <el-empty description="暂无日志记录" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[20, 50, 100]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.log-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  .title { font-size: 20px; font-weight: 600; color: #2c3e50; }
  .desc { margin-top: 4px; font-size: 13px; color: #7f8c8d; }
}

.table-card { border-radius: 8px; }

.filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;

  .filter-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .filter-label {
    font-size: 13px;
    color: #7f8c8d;
    white-space: nowrap;
  }
}

.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
