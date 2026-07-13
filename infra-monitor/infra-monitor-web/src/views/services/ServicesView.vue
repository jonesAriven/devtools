<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">服务监控</h2>
      <div class="page-actions">
        <el-button type="success" @click="handleCheckAll" :loading="checkingAll">
          <el-icon><Refresh /></el-icon>
          一键检查
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增服务
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchForm.keyword"
        placeholder="搜索名称/地址"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select
        v-model="searchForm.status"
        placeholder="状态"
        clearable
        style="width: 140px"
        @change="handleSearch"
      >
        <el-option label="在线" value="online" />
        <el-option label="离线" value="offline" />
        <el-option label="未知" value="unknown" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
      <el-button @click="handleReset">
        <el-icon><Refresh /></el-icon>
        重置
      </el-button>
    </div>

    <div class="status-summary">
      <div class="summary-item total">
        <span class="label">总计</span>
        <span class="value">{{ summary.total || 0 }}</span>
      </div>
      <div class="summary-item online">
        <span class="label">在线</span>
        <span class="value">{{ summary.online || 0 }}</span>
      </div>
      <div class="summary-item offline">
        <span class="label">离线</span>
        <span class="value">{{ summary.offline || 0 }}</span>
      </div>
      <div class="summary-item unknown">
        <span class="label">未知</span>
        <span class="value">{{ summary.unknown || 0 }}</span>
      </div>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.category || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            size="small"
            :class="`status-tag ${getStatusClass(row.extra?.status)}`"
            :type="getStatusType(row.extra?.status)"
          >
            {{ getStatusLabel(row.extra?.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="延迟" width="120">
        <template #default="{ row }">
          <span v-if="row.extra?.latencyMs !== undefined && row.extra?.latencyMs !== null">
            {{ row.extra?.latencyMs }}ms
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="地址" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.extra?.url || row.extra?.host || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="端口" width="80">
        <template #default="{ row }">
          {{ row.extra?.port || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="最近检查时间" width="180">
        <template #default="{ row }">
          {{ row.extra?.lastCheckTime || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" size="small" @click="handleCheck(row)" :loading="row._checking">
            立即检测
          </el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入服务名称" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="formData.category" placeholder="请输入分类" />
        </el-form-item>
        <el-form-item label="检测类型" prop="extra.checkType">
          <el-select v-model="formData.extra.checkType" placeholder="请选择检测类型" style="width: 100%">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="SHELL" value="SHELL" />
          </el-select>
        </el-form-item>
        <template v-if="formData.extra.checkType === 'HTTP'">
          <el-form-item label="主机地址" prop="extra.host">
            <el-input v-model="formData.extra.host" placeholder="请输入主机地址" />
          </el-form-item>
          <el-form-item label="端口" prop="extra.port">
            <el-input-number v-model="formData.extra.port" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
          <el-form-item label="URL路径" prop="extra.url">
            <el-input v-model="formData.extra.url" placeholder="如: /health" />
          </el-form-item>
          <el-form-item label="超时时间(秒)" prop="extra.timeout">
            <el-input-number v-model="formData.extra.timeout" :min="1" :max="60" style="width: 100%" />
          </el-form-item>
        </template>
        <template v-if="formData.extra.checkType === 'SHELL'">
          <el-form-item label="关联主机" prop="extra.hostId">
            <el-select v-model="formData.extra.hostId" placeholder="请选择关联主机" style="width: 100%" filterable>
              <el-option
                v-for="host in hostList"
                :key="host.id"
                :label="host.name"
                :value="host.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="SSH凭据" prop="extra.credentialId">
            <el-select v-model="formData.extra.credentialId" placeholder="请选择SSH凭据" style="width: 100%" filterable>
              <el-option
                v-for="cred in credentialList"
                :key="cred.id"
                :label="`${cred.name} (${cred.extra?.username || ''})`"
                :value="cred.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Shell脚本" prop="extra.script">
            <el-input
              v-model="formData.extra.script"
              type="textarea"
              :rows="6"
              placeholder="请输入Shell脚本内容"
              class="shell-editor"
            />
          </el-form-item>
          <el-form-item label="超时时间(秒)" prop="extra.timeout">
            <el-input-number v-model="formData.extra.timeout" :min="1" :max="300" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import type { InfraItem, HealthCheckResult, HealthCheckAllResult } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const checkingAll = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增服务')
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<string | null>(null)

const searchForm = reactive({
  keyword: '',
  status: '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

const summary = reactive({
  total: 0,
  online: 0,
  offline: 0,
  unknown: 0,
})

const tableData = ref<InfraItem[]>([])
const hostList = ref<InfraItem[]>([])
const credentialList = ref<InfraItem[]>([])

const formData = reactive({
  name: '',
  category: '',
  description: '',
  sortOrder: 0,
  extra: {
    checkType: 'HTTP',
    host: '',
    port: undefined as number | undefined,
    url: '',
    timeout: 10,
    hostId: '',
    credentialId: '',
    script: '',
  } as Record<string, any>,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  'extra.checkType': [{ required: true, message: '请选择检查类型', trigger: 'change' }],
}

function getStatusClass(status?: string): string {
  const s = status?.toUpperCase()
  if (s === 'ONLINE') return 'online'
  if (s === 'OFFLINE') return 'offline'
  return 'unknown'
}

function getStatusType(status?: string): 'success' | 'danger' | 'warning' {
  const s = status?.toUpperCase()
  if (s === 'ONLINE') return 'success'
  if (s === 'OFFLINE') return 'danger'
  return 'warning'
}

function getStatusLabel(status?: string): string {
  const s = status?.toUpperCase()
  if (s === 'ONLINE') return '在线'
  if (s === 'OFFLINE') return '离线'
  return '未知'
}

function updateSummary() {
  let total = 0
  let online = 0
  let offline = 0
  let unknown = 0

  tableData.value.forEach(item => {
    total++
    const status = item.extra?.status?.toUpperCase()
    if (status === 'ONLINE') online++
    else if (status === 'OFFLINE') offline++
    else unknown++
  })

  summary.total = total
  summary.online = online
  summary.offline = offline
  summary.unknown = unknown
}

async function fetchList() {
  loading.value = true
  try {
    const data = await request.get('/items/list', {
      params: {
        type: 'service',
        keyword: searchForm.keyword,
        page: pagination.page,
        size: pagination.size,
      },
    })
    if (data) {
      tableData.value = (data.list || []).map((item: any) => ({
        ...item,
        extra: {
          ...item.extra,
          status: item.extra?.status || 'UNKNOWN',
          latencyMs: item.extra?.latencyMs,
          lastCheckTime: item.extra?.lastCheckTime,
        },
        _checking: false,
      }))
      pagination.total = data.total || 0
      updateSummary()
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  handleSearch()
}

function resetForm() {
  formData.name = ''
  formData.category = ''
  formData.description = ''
  formData.sortOrder = 0
  formData.extra = {
    checkType: 'HTTP',
    host: '',
    port: undefined,
    url: '',
    timeout: 10,
    hostId: '',
    credentialId: '',
    script: '',
  }
  formRef.value?.clearValidate()
}

async function fetchHostList() {
  try {
    const data = await request.get('/items/all', {
      params: { type: 'host' },
    })
    hostList.value = data || []
  } catch (e) {
    console.error('获取主机列表失败', e)
  }
}

async function fetchCredentialList() {
  try {
    const data = await request.get('/credentials/all')
    credentialList.value = data || []
  } catch (e) {
    console.error('获取凭据列表失败', e)
  }
}

async function handleAdd() {
  isEdit.value = false
  editId.value = null
  dialogTitle.value = '新增服务'
  resetForm()
  await Promise.all([fetchHostList(), fetchCredentialList()])
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑服务'
  resetForm()
  
  formData.name = row.name
  formData.category = row.category
  formData.description = row.description
  formData.sortOrder = row.sortOrder
  
  const extra = row.extra || {}
  formData.extra = {
    checkType: extra.checkType || 'HTTP',
    host: extra.host || '',
    port: extra.port,
    url: extra.url || '',
    timeout: extra.timeout || (extra.checkType === 'SHELL' ? 30 : 10),
    hostId: extra.hostId || '',
    credentialId: extra.credentialId || '',
    script: extra.script || '',
  }
  
  await Promise.all([fetchHostList(), fetchCredentialList()])
  dialogVisible.value = true
}

async function handleCheck(row: any) {
  row._checking = true
  try {
    const data = await request.post(`/health/check/${row.id}`)
    if (data) {
      row.extra.status = data.status
      row.extra.latencyMs = data.latencyMs
      row.extra.lastCheckTime = new Date().toLocaleString()
      if (data.errorMsg) {
        ElMessage.warning(`${row.name}: ${data.errorMsg}`)
      } else {
        ElMessage.success(`${row.name}: 检查完成，状态${getStatusLabel(data.status)}`)
      }
      updateSummary()
    }
  } catch (e) {
    ElMessage.error('检查失败')
  } finally {
    row._checking = false
  }
}

async function handleCheckAll() {
  checkingAll.value = true
  try {
    const data = await request.post('/health/check-all')
    if (data && data.results) {
      data.results.forEach((result: HealthCheckResult) => {
        const item = tableData.value.find(i => i.id === result.id)
        if (item) {
          item.extra.status = result.status
          item.extra.latencyMs = result.latencyMs
          item.extra.lastCheckTime = new Date().toLocaleString()
        }
      })
      summary.total = data.total
      summary.online = data.online
      summary.offline = data.offline
      summary.unknown = data.unknown
      ElMessage.success(`检查完成：在线${data.online}个，离线${data.offline}个，未知${data.unknown}个`)
    }
  } catch (e) {
    ElMessage.error('一键检查失败')
  } finally {
    checkingAll.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除服务「${row.name}」吗？`, '提示', {
      type: 'warning',
    })
    await request.delete(`/items/${row.id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const extra = { ...formData.extra }
    const payload = {
      type: 'service',
      name: formData.name,
      category: formData.category,
      description: formData.description,
      sortOrder: formData.sortOrder,
      extra,
    }

    if (isEdit.value && editId.value) {
      await request.put(`/items/${editId.value}`, payload)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/items', payload)
      ElMessage.success('新增成功')
    }

    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.shell-editor {
  :deep(.el-textarea__inner) {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.6;
    min-height: 150px;
  }
}

.status-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;

  .summary-item {
    flex: 1;
    background: #fff;
    border-radius: 8px;
    padding: 16px 20px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

    .label {
      font-size: 14px;
      color: #909399;
    }

    .value {
      font-size: 24px;
      font-weight: 600;
    }

    &.total .value {
      color: #409eff;
    }

    &.online .value {
      color: #67c23a;
    }

    &.offline .value {
      color: #f56c6c;
    }

    &.unknown .value {
      color: #909399;
    }
  }
}
</style>
