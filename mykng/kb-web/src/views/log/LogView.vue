<template>
  <div class="log-view">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>操作日志</span>
          <div class="header-actions">
            <el-select
              v-model="filterAction"
              placeholder="动作筛选"
              clearable
              style="width: 150px"
              @change="handleSearch"
            >
              <el-option
                v-for="opt in actionOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-select
              v-model="filterResourceType"
              placeholder="资源类型"
              clearable
              style="width: 150px"
              @change="handleSearch"
            >
              <el-option label="文件" value="file" />
              <el-option label="笔记" value="doc" />
              <el-option label="网页" value="web" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 360px"
              @change="handleSearch"
            />
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="list"
        v-loading="loading"
        stripe
        @row-click="showDetail"
        class="log-table"
      >
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="动作" width="110">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resourceType" label="资源类型" width="110">
          <template #default="{ row }">
            <span v-if="row.resourceType">{{ row.resourceType }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="resourceId" label="资源ID" width="90">
          <template #default="{ row }">
            <span v-if="row.resourceId">{{ row.resourceId }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140">
          <template #default="{ row }">
            <span v-if="row.ip">{{ row.ip }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="600px">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="日志ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDate(currentLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ currentLog.username }}</el-descriptions-item>
        <el-descriptions-item label="动作">
          <el-tag :type="actionTagType(currentLog.action)" size="small">{{ currentLog.action }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ currentLog.resourceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ currentLog.resourceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详情">
          <pre class="detail-pre">{{ currentLog.detail }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getLogList, getLogDetail } from '@/api/log'
import type { OperationLog } from '@/types'
import { formatDate } from '@/utils/format'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const list = ref<OperationLog[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filterAction = ref('')
const filterResourceType = ref('')
const dateRange = ref<[string, string] | null>(null)

const detailVisible = ref(false)
const currentLog = ref<OperationLog | null>(null)

const actionOptions = [
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '新增', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '上传', value: 'UPLOAD' },
  { label: '下载', value: 'DOWNLOAD' },
  { label: '分享', value: 'SHARE' },
]

function actionTagType(action: string): 'success' | 'warning' | 'danger' | 'primary' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'primary' | 'info'> = {
    LOGIN: 'success',
    LOGOUT: 'info',
    CREATE: 'primary',
    UPDATE: 'warning',
    DELETE: 'danger',
    UPLOAD: 'primary',
    DOWNLOAD: 'info',
    SHARE: 'success',
  }
  return map[action] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      page: page.value,
      size: pageSize.value,
      action: filterAction.value || undefined,
      resourceType: filterResourceType.value || undefined,
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0].replace(' ', 'T')
      params.endTime = dateRange.value[1].replace(' ', 'T')
    }
    const res = await getLogList(params)
    list.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    ElMessage.error('加载日志列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function resetFilter() {
  filterAction.value = ''
  filterResourceType.value = ''
  dateRange.value = null
  page.value = 1
  loadData()
}

async function showDetail(row: OperationLog) {
  try {
    const res = await getLogDetail(row.id)
    currentLog.value = res.data.data
    detailVisible.value = true
  } catch {
    currentLog.value = row
    detailVisible.value = true
    ElMessage.error('加载详情失败，显示列表数据')
  }
}

onMounted(loadData)
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.muted {
  color: #909399;
}

.log-table :deep(.el-table__row) {
  cursor: pointer;
}

.detail-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  max-height: 240px;
  overflow-y: auto;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .header-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-select,
  .header-actions .el-date-editor {
    width: 100% !important;
  }

  .log-view :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
