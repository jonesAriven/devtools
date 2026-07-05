<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">凭据管理</h2>
      <div class="page-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增凭据
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchForm.keyword"
        placeholder="搜索名称/用户名/服务名称"
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
        v-model="searchForm.category"
        placeholder="类型"
        clearable
        style="width: 160px"
        @change="handleSearch"
      >
        <el-option label="WEB" value="WEB" />
        <el-option label="DB" value="DB" />
        <el-option label="SSH" value="SSH" />
        <el-option label="API_TOKEN" value="API_TOKEN" />
        <el-option label="OTHER" value="OTHER" />
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

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="getCredentialTagType(row.extra?.type)">{{ row.extra?.type || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用户名" width="140">
        <template #default="{ row }">
          {{ row.extra?.username || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="密码" width="120">
        <template #default="{ row }">
          <span v-if="row.extra?.password">******</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="端口" width="80">
        <template #default="{ row }">
          {{ row.extra?.port || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="关联主机" width="140">
        <template #default="{ row }">
          {{ row.extra?.host || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="服务名称" width="140">
        <template #default="{ row }">
          {{ row.extra?.serviceName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="URL" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.extra?.url || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleViewPassword(row)">查看</el-button>
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
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入凭据名称" />
        </el-form-item>
        <el-form-item label="类型" prop="extra.type">
          <el-select v-model="formData.extra.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="WEB" value="WEB" />
            <el-option label="DB" value="DB" />
            <el-option label="SSH" value="SSH" />
            <el-option label="API_TOKEN" value="API_TOKEN" />
            <el-option label="OTHER" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名" prop="extra.username">
          <el-input v-model="formData.extra.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="extra.password">
          <el-input v-model="formData.extra.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item v-if="formData.extra.type === 'SSH'" label="SSH端口" prop="extra.port">
          <el-input-number v-model="formData.extra.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联主机" prop="extra.host">
          <el-input v-model="formData.extra.host" placeholder="请输入关联主机" />
        </el-form-item>
        <el-form-item label="服务名称" prop="extra.serviceName">
          <el-input v-model="formData.extra.serviceName" placeholder="请输入服务名称" />
        </el-form-item>
        <el-form-item label="URL" prop="extra.url">
          <el-input v-model="formData.extra.url" placeholder="请输入URL地址" />
        </el-form-item>
        <el-form-item label="备注" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入备注" />
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

    <el-dialog v-model="viewDialogVisible" title="凭据详情" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ viewData.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ viewData.extra?.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ viewData.extra?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="密码">
          <span class="password-text">{{ viewPassword }}</span>
          <el-button link type="primary" size="small" @click="copyPassword">
            <el-icon><CopyDocument /></el-icon>
            复制
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item v-if="viewData.extra?.port" label="SSH端口">{{ viewData.extra.port }}</el-descriptions-item>
        <el-descriptions-item label="关联主机">{{ viewData.extra?.host || '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务名称">{{ viewData.extra?.serviceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="URL">{{ viewData.extra?.url || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewData.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import type { InfraItem } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const dialogTitle = ref('新增凭据')
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<string | null>(null)
const viewData = ref<InfraItem>({} as InfraItem)
const viewPassword = ref('')

const searchForm = reactive({
  keyword: '',
  category: '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

const tableData = ref<InfraItem[]>([])

const formData = reactive({
  name: '',
  category: '',
  description: '',
  sortOrder: 0,
  extra: {
    type: 'WEB',
    username: '',
    password: '',
    port: 22,
    host: '',
    serviceName: '',
    url: '',
  } as Record<string, any>,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  'extra.type': [{ required: true, message: '请选择类型', trigger: 'change' }],
}

function getCredentialTagType(type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const typeMap: Record<string, any> = {
    WEB: 'primary',
    DB: 'success',
    SSH: 'warning',
    API_TOKEN: 'danger',
    OTHER: 'info',
  }
  return typeMap[type] || 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/credentials/list', {
      params: {
        keyword: searchForm.keyword,
        category: searchForm.category || undefined,
        page: pagination.page,
        size: pagination.size,
      },
    })
    const data = res.data?.data || res.data
    if (data) {
      tableData.value = data.list || []
      pagination.total = data.total || 0
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
  searchForm.category = ''
  handleSearch()
}

function resetForm() {
  formData.name = ''
  formData.category = ''
  formData.description = ''
  formData.sortOrder = 0
  formData.extra = {
    type: 'WEB',
    username: '',
    password: '',
    port: 22,
    host: '',
    serviceName: '',
    url: '',
  }
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  dialogTitle.value = '新增凭据'
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑凭据'
  resetForm()
  
  formData.name = row.name
  formData.category = row.category
  formData.description = row.description
  formData.sortOrder = row.sortOrder
  
  const extra = row.extra || {}
  formData.extra = {
    type: extra.type || 'WEB',
    username: extra.username || '',
    password: '',
    port: extra.port || 22,
    host: extra.host || '',
    serviceName: extra.serviceName || '',
    url: extra.url || '',
  }
  
  dialogVisible.value = true
}

async function handleViewPassword(row: any) {
  try {
    const res = await request.get(`/credentials/${row.id}`)
    const data = res.data?.data || res.data
    viewData.value = data || row
    viewPassword.value = data?.extra?.password || data?.password || ''
    viewDialogVisible.value = true
  } catch (e) {
    viewData.value = row
    viewPassword.value = '******'
    viewDialogVisible.value = true
  }
}

function copyPassword() {
  if (viewPassword.value && viewPassword.value !== '******') {
    navigator.clipboard.writeText(viewPassword.value)
    ElMessage.success('密码已复制到剪贴板')
  } else {
    ElMessage.warning('无法复制密码')
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除凭据「${row.name}」吗？`, '提示', {
      type: 'warning',
    })
    await request.delete(`/credentials/${row.id}`)
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
      type: 'credential',
      name: formData.name,
      category: formData.extra.type,
      description: formData.description,
      sortOrder: formData.sortOrder,
      extra,
    }

    if (isEdit.value && editId.value) {
      await request.put(`/credentials/${editId.value}`, payload)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/credentials', payload)
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
.password-text {
  font-family: monospace;
  margin-right: 8px;
}
</style>
