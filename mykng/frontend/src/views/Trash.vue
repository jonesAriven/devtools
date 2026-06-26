<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RotateCcw, Trash2 } from 'lucide-vue-next'
import { trashApi } from '@/api/knowledge'
import type { TrashItem } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<TrashItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const filterType = ref('')

const typeOptions = [
  { label: '全部', value: '' },
  { label: '文档', value: 'doc' },
  { label: '目录', value: 'folder' },
  { label: '网页', value: 'page' },
]

const filteredList = computed(() => {
  if (!filterType.value) return list.value
  return list.value.filter((item) => item.resourceType === filterType.value)
})

async function loadList() {
  loading.value = true
  try {
    const res = await trashApi.list({ page: page.value, size: size.value })
    list.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
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

async function handleRestore(item: TrashItem) {
  await ElMessageBox.confirm(
    `确定恢复「${item.title}」吗？恢复后可重新访问。`,
    '恢复确认',
    { type: 'info', confirmButtonText: '恢复', cancelButtonText: '取消' },
  )
  await trashApi.restore(item.resourceType, item.resourceId)
  ElMessage.success('恢复成功')
  loadList()
}

async function handlePurge(item: TrashItem) {
  await ElMessageBox.confirm(
    `彻底删除「${item.title}」后将无法恢复，确定继续吗？`,
    '彻底删除确认',
    { type: 'warning', confirmButtonText: '彻底删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' },
  )
  await trashApi.delete(item.resourceType, item.resourceId)
  ElMessage.success('已彻底删除')
  loadList()
}

function typeLabel(type: string) {
  const map: Record<string, string> = { doc: '文档', folder: '目录', page: '网页' }
  return map[type] || type
}

function typeTag(type: string) {
  const map: Record<string, string> = { doc: '', folder: 'warning', page: 'success' }
  return map[type] || ''
}

onMounted(loadList)
</script>

<template>
  <div class="trash-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="title">回收站</h2>
        <p class="desc">已删除的资源可在此恢复或彻底清除</p>
      </div>
      <div class="filter-bar">
        <span class="filter-label">资源类型</span>
        <el-select v-model="filterType" placeholder="全部" style="width: 140px" @change="onFilterChange">
          <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="filteredList" stripe style="width: 100%">
        <el-table-column label="资源标题" prop="title" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.resourceType)" size="small">{{ typeLabel(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源ID" prop="resourceId" width="100" align="center" />
        <el-table-column label="删除时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="RotateCcw" @click="handleRestore(row)">恢复</el-button>
            <el-button link type="danger" :icon="Trash2" @click="handlePurge(row)">彻底删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="回收站为空" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.trash-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .title {
    font-size: 20px;
    font-weight: 600;
    color: #2c3e50;
  }

  .desc {
    margin-top: 4px;
    font-size: 13px;
    color: #7f8c8d;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;

  .filter-label {
    font-size: 13px;
    color: #7f8c8d;
  }
}

.table-card {
  border-radius: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
