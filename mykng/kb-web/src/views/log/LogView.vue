<template>
  <div class="log-view">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="page-title">系统日志</span>
          <div class="header-actions">
            <div v-if="activeTab === 'operation'" class="filter-group">
              <el-select
                v-model="filterAction"
                placeholder="动作筛选"
                clearable
                style="width: 120px"
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
                style="width: 120px"
                @change="handleSearch"
              >
                <el-option label="文件" value="file" />
                <el-option label="笔记" value="doc" />
                <el-option label="网页" value="web" />
              </el-select>
            </div>
            <div v-else-if="activeTab === 'error'" class="filter-group">
              <el-select
                v-model="errorFilterLevel"
                placeholder="日志级别"
                clearable
                style="width: 120px"
                @change="handleSearch"
              >
                <el-option label="错误" value="error" />
                <el-option label="警告" value="warn" />
                <el-option label="信息" value="info" />
              </el-select>
              <el-select
                v-model="errorFilterSource"
                placeholder="来源"
                clearable
                style="width: 120px"
                @change="handleSearch"
              >
                <el-option label="前端" value="frontend" />
                <el-option label="后端" value="backend" />
              </el-select>
            </div>
            <div v-else class="filter-group">
              <el-input
                v-model="requestFilterTraceId"
                placeholder="TraceId搜索"
                clearable
                style="width: 180px"
                @keyup.enter="handleSearch"
              />
              <el-select
                v-model="requestFilterStatus"
                placeholder="状态"
                clearable
                style="width: 120px"
                @change="handleSearch"
              >
                <el-option label="成功" value="success" />
                <el-option label="慢请求" value="slow" />
                <el-option label="错误" value="error" />
              </el-select>
              <el-select
                v-model="requestFilterMethod"
                placeholder="方法"
                clearable
                style="width: 100px"
                @change="handleSearch"
              >
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
              <el-select
                v-model="requestFilterService"
                placeholder="服务"
                clearable
                style="width: 140px"
                @change="handleSearch"
              >
                <el-option label="kb-auth" value="kb-auth" />
                <el-option label="kb-file" value="kb-file" />
                <el-option label="kb-knowledge" value="kb-knowledge" />
                <el-option label="kb-intelligence" value="kb-intelligence" />
              </el-select>
            </div>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 320px"
              @change="handleSearch"
            />
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </div>
        </div>
        <el-tabs v-model="activeTab" class="log-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="操作日志" name="operation" />
          <el-tab-pane label="错误日志" name="error" />
          <el-tab-pane label="请求日志" name="request" />
        </el-tabs>
      </template>

      <el-table
        v-if="activeTab === 'operation'"
        :data="operationList"
        v-loading="loading"
        stripe
        @row-click="showOperationDetail"
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

      <el-table
        v-else-if="activeTab === 'error'"
        :data="errorList"
        v-loading="loading"
        stripe
        @row-click="showErrorDetail"
        class="log-table"
      >
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户" width="100">
          <template #default="{ row }">
            <span v-if="row.username">{{ row.username }}</span>
            <span v-else class="muted">未登录</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="错误信息" min-width="280" show-overflow-tooltip />
        <el-table-column prop="url" label="页面/接口" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130">
          <template #default="{ row }">
            <span v-if="row.ip">{{ row.ip }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无错误日志" />
        </template>
      </el-table>

      <el-table
        v-else
        :data="requestList"
        v-loading="loading"
        stripe
        @row-click="showRequestDetail"
        class="log-table"
      >
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="requestStatusTagType(row.status)" size="small">{{ requestStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方法" width="80">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestUri" label="请求URI" min-width="240" show-overflow-tooltip />
        <el-table-column prop="serviceName" label="服务" width="130">
          <template #default="{ row }">
            <span v-if="row.serviceName">{{ row.serviceName }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            <span :class="{ 'slow-request': row.costMs && row.costMs > 500 }">{{ row.costMs }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="TraceId" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.traceId" class="trace-id" @click.stop="copyTraceId(row.traceId)">
              {{ row.traceId }}
              <el-icon class="copy-icon"><CopyDocument /></el-icon>
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130">
          <template #default="{ row }">
            <span v-if="row.ip">{{ row.ip }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无请求日志" />
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

    <el-dialog v-model="detailVisible" title="日志详情" width="700px">
      <el-descriptions v-if="activeTab === 'operation' && currentOperationLog" :column="1" border>
        <el-descriptions-item label="日志ID">{{ currentOperationLog.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDate(currentOperationLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentOperationLog.userId }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ currentOperationLog.username }}</el-descriptions-item>
        <el-descriptions-item label="动作">
          <el-tag :type="actionTagType(currentOperationLog.action)" size="small">{{ currentOperationLog.action }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ currentOperationLog.resourceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ currentOperationLog.resourceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentOperationLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详情">
          <pre class="detail-pre">{{ currentOperationLog.detail }}</pre>
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-else-if="activeTab === 'error' && currentErrorLog" :column="1" border>
        <el-descriptions-item label="日志ID">{{ currentErrorLog.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDate(currentErrorLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="levelTagType(currentErrorLog.level)" size="small">{{ levelLabel(currentErrorLog.level) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源">
          <el-tag type="info" size="small">{{ sourceLabel(currentErrorLog.source) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentErrorLog.username || '未登录' }}</el-descriptions-item>
        <el-descriptions-item label="页面/接口">{{ currentErrorLog.url || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentErrorLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">
          <pre class="detail-pre">{{ currentErrorLog.message }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentErrorLog.stackTrace" label="堆栈信息">
          <pre class="detail-pre">{{ currentErrorLog.stackTrace }}</pre>
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-else-if="activeTab === 'request' && currentRequestLog" :column="1" border>
        <el-descriptions-item label="日志ID">{{ currentRequestLog.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDate(currentRequestLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="TraceId">
          <span class="trace-id-text">{{ currentRequestLog.traceId || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="requestStatusTagType(currentRequestLog.status)" size="small">{{ requestStatusLabel(currentRequestLog.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="HTTP方法">
          <el-tag :type="methodTagType(currentRequestLog.httpMethod)" size="small">{{ currentRequestLog.httpMethod }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求URI">{{ currentRequestLog.requestUri }}</el-descriptions-item>
        <el-descriptions-item label="控制器方法">{{ currentRequestLog.controllerMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务">{{ currentRequestLog.serviceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentRequestLog.costMs }}ms</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentRequestLog.username || '未登录' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentRequestLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentRequestLog.requestArgs" label="请求参数">
          <pre class="detail-pre">{{ currentRequestLog.requestArgs }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentRequestLog.responseResult" label="响应结果">
          <pre class="detail-pre">{{ currentRequestLog.responseResult }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentRequestLog.exception" label="异常信息">
          <pre class="detail-pre">{{ currentRequestLog.exception }}</pre>
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
import { CopyDocument } from '@element-plus/icons-vue'
import { getLogList, getLogDetail } from '@/api/log'
import { getErrorLogList, getErrorLogDetail } from '@/api/errorLog'
import { getRequestLogList, getRequestLogDetail } from '@/api/requestLog'
import type { OperationLog } from '@/types'
import type { ErrorLog } from '@/api/errorLog'
import type { RequestLog } from '@/api/requestLog'
import { formatDate } from '@/utils/format'
import { ElMessage } from 'element-plus'
import { copyToClipboard } from '@/utils/clipboard'

const loading = ref(false)
const activeTab = ref('operation')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const operationList = ref<OperationLog[]>([])
const errorList = ref<ErrorLog[]>([])
const requestList = ref<RequestLog[]>([])

const filterAction = ref('')
const filterResourceType = ref('')
const dateRange = ref<[string, string] | null>(null)

const errorFilterLevel = ref('')
const errorFilterSource = ref('')

const requestFilterTraceId = ref('')
const requestFilterStatus = ref('')
const requestFilterMethod = ref('')
const requestFilterService = ref('')

const detailVisible = ref(false)
const currentOperationLog = ref<OperationLog | null>(null)
const currentErrorLog = ref<ErrorLog | null>(null)
const currentRequestLog = ref<RequestLog | null>(null)

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

function levelTagType(level: string): 'danger' | 'warning' | 'info' {
  const map: Record<string, 'danger' | 'warning' | 'info'> = {
    error: 'danger',
    warn: 'warning',
    info: 'info',
  }
  return map[level] || 'info'
}

function levelLabel(level: string) {
  const map: Record<string, string> = {
    error: '错误',
    warn: '警告',
    info: '信息',
  }
  return map[level] || level
}

function sourceLabel(source: string) {
  const map: Record<string, string> = {
    frontend: '前端',
    backend: '后端',
  }
  return map[source] || source
}

function requestStatusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    success: 'success',
    slow: 'warning',
    error: 'danger',
  }
  return map[status] || 'info'
}

function requestStatusLabel(status: string) {
  const map: Record<string, string> = {
    success: '成功',
    slow: '慢请求',
    error: '错误',
  }
  return map[status] || status || '-'
}

function methodTagType(method: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
  }
  return map[method] || 'info'
}

function copyTraceId(traceId: string) {
  copyToClipboard(traceId)
  ElMessage.success('TraceId已复制')
}

function handleTabChange() {
  page.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      page: page.value,
      size: pageSize.value,
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0].replace(' ', 'T')
      params.endTime = dateRange.value[1].replace(' ', 'T')
    }

    if (activeTab.value === 'operation') {
      params.action = filterAction.value || undefined
      params.resourceType = filterResourceType.value || undefined
      const res = await getLogList(params)
      operationList.value = res.data.data.list
      total.value = res.data.data.total
    } else if (activeTab.value === 'error') {
      params.level = errorFilterLevel.value || undefined
      params.source = errorFilterSource.value || undefined
      const res = await getErrorLogList(params)
      errorList.value = res.data.data.list
      total.value = res.data.data.total
    } else {
      params.traceId = requestFilterTraceId.value || undefined
      params.status = requestFilterStatus.value || undefined
      params.httpMethod = requestFilterMethod.value || undefined
      params.serviceName = requestFilterService.value || undefined
      const res = await getRequestLogList(params)
      requestList.value = res.data.data.list
      total.value = res.data.data.total
    }
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
  errorFilterLevel.value = ''
  errorFilterSource.value = ''
  requestFilterTraceId.value = ''
  requestFilterStatus.value = ''
  requestFilterMethod.value = ''
  requestFilterService.value = ''
  dateRange.value = null
  page.value = 1
  loadData()
}

async function showOperationDetail(row: OperationLog) {
  try {
    const res = await getLogDetail(row.id)
    currentOperationLog.value = res.data.data
    detailVisible.value = true
  } catch {
    currentOperationLog.value = row
    detailVisible.value = true
    ElMessage.error('加载详情失败，显示列表数据')
  }
}

async function showErrorDetail(row: ErrorLog) {
  try {
    const res = await getErrorLogDetail(row.id)
    currentErrorLog.value = res.data.data
    detailVisible.value = true
  } catch {
    currentErrorLog.value = row
    detailVisible.value = true
    ElMessage.error('加载详情失败，显示列表数据')
  }
}

async function showRequestDetail(row: RequestLog) {
  try {
    const res = await getRequestLogDetail(row.id)
    currentRequestLog.value = res.data.data
    detailVisible.value = true
  } catch {
    currentRequestLog.value = row
    detailVisible.value = true
    ElMessage.error('加载详情失败，显示列表数据')
  }
}

onMounted(loadData)
</script>

<style scoped>
.log-view {
  padding: 20px 24px;
  min-height: 100%;
  background-color: #faf8f5;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.log-tabs {
  margin: 0 -20px -21px;
  padding: 0 20px;
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

.trace-id {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
}

.trace-id:hover {
  text-decoration: underline;
}

.copy-icon {
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s;
}

.trace-id:hover .copy-icon {
  opacity: 1;
}

.trace-id-text {
  font-family: 'Courier New', Consolas, monospace;
  font-size: 13px;
  color: #409eff;
}

.slow-request {
  color: #e6a23c;
  font-weight: 600;
}

@media (max-width: 768px) {
  .log-view {
    padding: 12px;
  }

  .header-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
  }

  .filter-group {
    width: 100%;
  }

  .filter-group .el-select,
  .filter-group .el-input {
    flex: 1;
  }

  .header-actions .el-select,
  .header-actions .el-input,
  .header-actions .el-date-editor {
    width: 100% !important;
  }

  .log-view :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
