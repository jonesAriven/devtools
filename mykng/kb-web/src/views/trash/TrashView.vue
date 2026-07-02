<template>
  <div class="trash-page">
    <div class="page-header">
      <div class="page-title">回收站</div>
      <el-button type="danger" size="small" :disabled="loading || trashList.length === 0" @click="handleEmptyTrash">清空回收站</el-button>
    </div>

    <el-alert
      title="回收站中的资源将在30天后自动永久删除"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <div class="info-card">
      <div v-show="!loadError" v-loading="loading" class="table-wrapper">
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
          <template #empty>
            <el-empty description="回收站为空" />
          </template>
        </el-table>
      </div>

      <div v-if="loadError" class="error-state">
        <el-result icon="error" title="加载失败" sub-title="回收站数据加载失败，请重试">
          <template #extra>
            <el-button type="primary" @click="loadTrash">重新加载</el-button>
          </template>
        </el-result>
      </div>

      <div v-if="!loadError && total > pageSize" class="pagination-wrapper">
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
import { formatDate, typeLabel } from '@/utils/format'
import { confirmDelete, confirmDanger } from '@/utils/confirm'
import type { TrashItem } from '@/types'
import { ElMessage } from 'element-plus'

const trashList = ref<TrashItem[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const loadError = ref(false)

onMounted(() => {
  loadTrash()
})

async function loadTrash() {
  loading.value = true
  loadError.value = false
  try {
    const res = await getTrashList({ page: page.value, size: pageSize.value })
    trashList.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    loadError.value = true
    trashList.value = []
  } finally {
    loading.value = false
  }
}

async function handleRestore(row: TrashItem) {
  try {
    await restoreResource(row.id, row.type)
    ElMessage.success('已恢复')
    loadTrash()
  } catch {
    // 错误已在拦截器处理
  }
}

async function handlePermanentDelete(row: TrashItem) {
  try {
    await confirmDelete('永久删除后不可恢复，确定要删除吗？')
    await permanentDelete(row.id, row.type)
    ElMessage.success('已永久删除')
    loadTrash()
  } catch {
    // 用户取消或错误已在拦截器处理
  }
}

async function handleEmptyTrash() {
  try {
    await confirmDanger('清空回收站后所有资源将永久删除，确定要清空吗？', '确定清空')
    await emptyTrash()
    ElMessage.success('已清空回收站')
    loadTrash()
  } catch {
    // 用户取消或错误已在拦截器处理
  }
}
</script>

<style scoped lang="scss">
.trash-page {
  .table-wrapper {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .error-state {
    padding: 40px 0;
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
