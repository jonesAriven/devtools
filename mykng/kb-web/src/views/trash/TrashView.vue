<template>
  <div class="trash-page">
    <div class="page-header">
      <div class="page-title">回收站</div>
      <el-button type="danger" size="small" @click="handleEmptyTrash">清空回收站</el-button>
    </div>

    <el-alert
      title="回收站中的资源将在30天后自动永久删除"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <div class="info-card">
      <div class="table-wrapper">
      <el-table :data="trashList" stripe style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deletedAt" label="删除时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.deletedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="expireAt" label="过期时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.expireAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleRestore(row)">恢复</el-button>
            <el-button link type="danger" size="small" @click="handlePermanentDelete(row)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTrash"
          @size-change="loadTrash"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTrashList, restoreResource, permanentDelete, emptyTrash } from '@/api/trash'
import { formatDate } from '@/utils/format'
import type { TrashItem } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const trashList = ref<TrashItem[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

onMounted(() => {
  loadTrash()
})

async function loadTrash() {
  const res = await getTrashList({ page: page.value, pageSize: pageSize.value })
  trashList.value = res.data.data.list
  total.value = res.data.data.total
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { file: '文件', doc: '笔记', web: '网页' }
  return map[type] || type
}

async function handleRestore(row: TrashItem) {
  await restoreResource(row.id, row.type)
  ElMessage.success('已恢复')
  loadTrash()
}

async function handlePermanentDelete(row: TrashItem) {
  await ElMessageBox.confirm('永久删除后不可恢复，确定要删除吗？', '警告', { type: 'warning' })
  await permanentDelete(row.id, row.type)
  ElMessage.success('已永久删除')
  loadTrash()
}

async function handleEmptyTrash() {
  await ElMessageBox.confirm('清空回收站后所有资源将永久删除，确定要清空吗？', '警告', { type: 'warning' })
  await emptyTrash()
  ElMessage.success('已清空回收站')
  loadTrash()
}
</script>

<style scoped lang="scss">
.trash-page {
  .table-wrapper {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 16px;
  }
}

@media (max-width: 768px) {
  .trash-page {
    .page-header {
      flex-direction: column;
      gap: 8px;
      align-items: stretch;
    }
  }
}
</style>
