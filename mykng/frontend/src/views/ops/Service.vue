<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { serviceApi } from '@/api/ops'
import type { OpsService } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<OpsService[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function loadList() {
  loading.value = true
  try {
    const res = await serviceApi.list({ page: page.value, size: size.value })
    list.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
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

function statusMeta(status: number) {
  if (status === 1) return { text: '运行中', type: 'success' as const }
  if (status === 2) return { text: '异常', type: 'danger' as const }
  return { text: '已停止', type: 'info' as const }
}

onMounted(loadList)
</script>

<template>
  <div class="service-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="title">服务管理</h2>
        <p class="desc">查看各主机部署的服务及运行状态</p>
      </div>
      <el-button :icon="RefreshCw" @click="loadList">刷新</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column label="服务名" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="所属主机" min-width="140">
          <template #default="{ row }">
            <span v-if="row.hostName">{{ row.hostName }}</span>
            <span v-else class="muted">主机 #{{ row.hostId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="端口" prop="port" width="100" align="center" />
        <el-table-column label="版本" prop="version" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.version" size="small" type="info" effect="plain">v{{ row.version }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无服务记录" />
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
.service-page {
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

  .title { font-size: 20px; font-weight: 600; color: #2c3e50; }
  .desc { margin-top: 4px; font-size: 13px; color: #7f8c8d; }
}

.table-card { border-radius: 8px; }
.muted { color: #bbb; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
