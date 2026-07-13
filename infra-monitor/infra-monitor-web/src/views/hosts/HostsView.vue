<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">主机管理</h2>
      <div class="page-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增主机
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchForm.keyword"
        placeholder="搜索名称/IP/位置"
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
        placeholder="分类"
        clearable
        style="width: 160px"
        @change="handleSearch"
      >
        <el-option label="生产环境" value="prod" />
        <el-option label="测试环境" value="test" />
        <el-option label="开发环境" value="dev" />
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
      <el-table-column prop="category" label="分类" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.category || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="{ row }">
          {{ row.extra?.ip || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="SSH端口" width="100">
        <template #default="{ row }">
          {{ row.extra?.sshPort || 22 }}
        </template>
      </el-table-column>
      <el-table-column label="操作系统" width="120">
        <template #default="{ row }">
          {{ row.extra?.os || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="CPU核数" width="100">
        <template #default="{ row }">
          {{ row.extra?.cpuCores || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="内存(GB)" width="100">
        <template #default="{ row }">
          {{ row.extra?.memoryGb || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="磁盘(GB)" width="100">
        <template #default="{ row }">
          {{ row.extra?.diskGb || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="位置" width="120">
        <template #default="{ row }">
          {{ row.extra?.location || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
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
          <el-input v-model="formData.name" placeholder="请输入主机名称" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="生产环境" value="prod" />
            <el-option label="测试环境" value="test" />
            <el-option label="开发环境" value="dev" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="IP地址" prop="extra.ip">
          <el-input v-model="formData.extra.ip" placeholder="请输入IP地址" />
        </el-form-item>
        <el-form-item label="SSH端口" prop="extra.sshPort">
          <el-input-number v-model="formData.extra.sshPort" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="操作系统" prop="extra.os">
          <el-input v-model="formData.extra.os" placeholder="如: CentOS 7, Ubuntu 20.04" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="CPU核数" prop="extra.cpuCores">
              <el-input-number v-model="formData.extra.cpuCores" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="内存(GB)" prop="extra.memoryGb">
              <el-input-number v-model="formData.extra.memoryGb" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="磁盘(GB)" prop="extra.diskGb">
              <el-input-number v-model="formData.extra.diskGb" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="位置" prop="extra.location">
          <el-input v-model="formData.extra.location" placeholder="如: 阿里云-上海-可用区A" />
        </el-form-item>
        <el-form-item label="标签" prop="extra.tags">
          <el-input v-model="formData.extra.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" />
        </el-form-item>
        <el-divider content-position="left">自定义字段</el-divider>
        <el-form-item label="自定义字段">
          <div class="custom-fields">
            <div v-for="(field, index) in customFields" :key="index" class="custom-field-row">
              <el-input v-model="field.key" placeholder="字段名" style="width: 140px" />
              <el-input v-model="field.value" placeholder="字段值" style="flex: 1" />
              <el-button link type="danger" @click="removeCustomField(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addCustomField">
              <el-icon><Plus /></el-icon>
              添加字段
            </el-button>
          </div>
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
import type { InfraItem } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增主机')
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<string | null>(null)

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
    ip: '',
    sshPort: 22,
    os: '',
    cpuCores: undefined as number | undefined,
    memoryGb: undefined as number | undefined,
    diskGb: undefined as number | undefined,
    location: '',
    tags: '',
  } as Record<string, any>,
})

const customFields = ref<{ key: string; value: string }[]>([])

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const data = await request.get('/items/list', {
      params: {
        type: 'host',
        keyword: searchForm.keyword,
        category: searchForm.category || undefined,
        page: pagination.page,
        size: pagination.size,
      },
    })
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
    ip: '',
    sshPort: 22,
    os: '',
    cpuCores: undefined,
    memoryGb: undefined,
    diskGb: undefined,
    location: '',
    tags: '',
  }
  customFields.value = []
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  dialogTitle.value = '新增主机'
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑主机'
  resetForm()
  
  formData.name = row.name
  formData.category = row.category
  formData.description = row.description
  formData.sortOrder = row.sortOrder
  
  const extra = row.extra || {}
  const knownKeys = ['ip', 'sshPort', 'os', 'cpuCores', 'memoryGb', 'diskGb', 'location', 'tags']
  knownKeys.forEach(key => {
    if (extra[key] !== undefined) {
      formData.extra[key] = extra[key]
    }
  })
  
  customFields.value = Object.entries(extra)
    .filter(([key]) => !knownKeys.includes(key))
    .map(([key, value]) => ({ key, value: String(value) }))
  
  dialogVisible.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除主机「${row.name}」吗？`, '提示', {
      type: 'warning',
    })
    await request.delete(`/items/${row.id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
  }
}

function addCustomField() {
  customFields.value.push({ key: '', value: '' })
}

function removeCustomField(index: number) {
  customFields.value.splice(index, 1)
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const extra = { ...formData.extra }
    customFields.value.forEach(field => {
      if (field.key) {
        extra[field.key] = field.value
      }
    })

    const payload = {
      type: 'host',
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
.custom-fields {
  width: 100%;

  .custom-field-row {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
    align-items: center;
  }
}
</style>
