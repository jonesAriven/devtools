<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">配置信息</h2>
      <div class="page-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增配置
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchForm.keyword"
        placeholder="搜索名称/描述"
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
        <el-option label="网络配置" value="NETWORK" />
        <el-option label="存储配置" value="STORAGE" />
        <el-option label="缓存配置" value="CACHE" />
        <el-option label="证书配置" value="CERT" />
        <el-option label="部署配置" value="DEPLOY" />
        <el-option label="代理配置" value="PROXY" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-select
        v-model="searchForm.configType"
        placeholder="配置类型"
        clearable
        style="width: 160px"
        @change="handleSearch"
      >
        <el-option label="文本" value="TEXT" />
        <el-option label="JSON" value="JSON" />
        <el-option label="键值对" value="KEY_VALUE" />
        <el-option label="列表" value="LIST" />
        <el-option label="表格" value="TABLE" />
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
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="getCategoryTagType(row.category)">{{ row.category || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="配置类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ getConfigTypeLabel(row.extra?.configType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
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
          <el-input v-model="formData.name" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="网络配置" value="NETWORK" />
            <el-option label="存储配置" value="STORAGE" />
            <el-option label="缓存配置" value="CACHE" />
            <el-option label="证书配置" value="CERT" />
            <el-option label="部署配置" value="DEPLOY" />
            <el-option label="代理配置" value="PROXY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置类型" prop="configType">
          <el-select v-model="formData.configType" placeholder="请选择配置类型" style="width: 100%">
            <el-option label="文本" value="TEXT" />
            <el-option label="JSON" value="JSON" />
            <el-option label="键值对" value="KEY_VALUE" />
            <el-option label="列表" value="LIST" />
            <el-option label="表格" value="TABLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <div v-if="formData.configType === 'TEXT'">
            <el-input v-model="formData.content" type="textarea" :rows="8" placeholder="请输入文本内容" />
          </div>
          <div v-else-if="formData.configType === 'JSON'">
            <el-input v-model="formData.content" type="textarea" :rows="8" placeholder="请输入JSON内容" />
            <el-button type="primary" link size="small" @click="formatJson">格式化JSON</el-button>
          </div>
          <div v-else-if="formData.configType === 'KEY_VALUE'">
            <div class="kv-editor">
              <div v-for="(item, index) in kvList" :key="index" class="kv-row">
                <el-input v-model="item.key" placeholder="键" style="width: 180px" />
                <el-input v-model="item.value" placeholder="值" style="flex: 1" />
                <el-button link type="danger" @click="removeKvItem(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" plain size="small" @click="addKvItem">
                <el-icon><Plus /></el-icon>
                添加键值对
              </el-button>
            </div>
          </div>
          <div v-else-if="formData.configType === 'LIST'">
            <div class="list-editor">
              <div v-for="(item, index) in listItems" :key="index" class="list-row">
                <el-input v-model="listItems[index]" placeholder="列表项" style="flex: 1" />
                <el-button link type="danger" @click="removeListItem(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" plain size="small" @click="addListItem">
                <el-icon><Plus /></el-icon>
                添加列表项
              </el-button>
            </div>
          </div>
          <div v-else-if="formData.configType === 'TABLE'">
            <div class="table-editor">
              <el-table :data="tableRows" border size="small">
                <el-table-column v-for="(col, cIndex) in tableColumns" :key="cIndex" :label="col.label || `列${cIndex + 1}`">
                  <template #default="{ row, $index }">
                    <el-input v-model="row[col.key]" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80">
                  <template #default="{ $index }">
                    <el-button link type="danger" size="small" @click="removeTableRow($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div style="margin-top: 8px; display: flex; gap: 8px;">
                <el-button type="primary" plain size="small" @click="addTableRow">
                  <el-icon><Plus /></el-icon>
                  添加行
                </el-button>
                <el-button type="primary" plain size="small" @click="addTableColumn">
                  <el-icon><Plus /></el-icon>
                  添加列
                </el-button>
              </div>
            </div>
          </div>
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

    <el-dialog v-model="viewDialogVisible" title="配置详情" width="700px">
      <el-descriptions :column="2" border style="margin-bottom: 16px;">
        <el-descriptions-item label="名称">{{ viewData.name }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ viewData.category }}</el-descriptions-item>
        <el-descriptions-item label="配置类型">{{ getConfigTypeLabel(viewData.extra?.configType) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ viewData.updatedAt }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ viewData.description }}</el-descriptions-item>
      </el-descriptions>
      <div class="view-content">
        <div class="view-content-title">配置内容</div>
        <div v-if="viewConfigType === 'JSON'" class="json-viewer">
          <pre>{{ formatJsonView(viewContent) }}</pre>
        </div>
        <div v-else-if="viewConfigType === 'KEY_VALUE'">
          <el-descriptions :column="1" border size="small">
            <template v-for="(item, index) in parseKeyValue(viewContent)" :key="index">
              <el-descriptions-item :label="item.key">{{ item.value }}</el-descriptions-item>
            </template>
          </el-descriptions>
        </div>
        <div v-else-if="viewConfigType === 'LIST'">
          <ul class="list-view">
            <li v-for="(item, index) in parseList(viewContent)" :key="index">{{ item }}</li>
          </ul>
        </div>
        <div v-else-if="viewConfigType === 'TABLE'">
          <el-table :data="parseTable(viewContent).rows" border size="small">
            <el-table-column v-for="(col, cIndex) in parseTable(viewContent).columns" :key="cIndex" :prop="col.key" :label="col.label" />
          </el-table>
        </div>
        <div v-else>
          <pre class="text-view">{{ viewContent }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import type { InfraItem } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const dialogTitle = ref('新增配置')
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<string | null>(null)

const searchForm = reactive({
  keyword: '',
  category: '',
  configType: '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

const tableData = ref<InfraItem[]>([])

const formData = reactive({
  name: '',
  category: 'OTHER',
  configType: 'TEXT',
  description: '',
  content: '',
  sortOrder: 0,
})

const kvList = ref<{ key: string; value: string }[]>([])
const listItems = ref<string[]>([])
const tableColumns = ref<{ key: string; label: string }[]>([{ key: 'col1', label: '列1' }])
const tableRows = ref<Record<string, string>[]>([])

const viewData = ref<InfraItem>({} as InfraItem)
const viewContent = ref('')
const viewConfigType = ref('TEXT')

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  configType: [{ required: true, message: '请选择配置类型', trigger: 'change' }],
}

function getCategoryTagType(category: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const typeMap: Record<string, any> = {
    NETWORK: 'primary',
    STORAGE: 'success',
    CACHE: 'warning',
    CERT: 'danger',
    DEPLOY: 'primary',
    PROXY: 'warning',
    OTHER: 'info',
  }
  return typeMap[category] || 'info'
}

function getConfigTypeLabel(type: string): string {
  const labelMap: Record<string, string> = {
    TEXT: '文本',
    JSON: 'JSON',
    KEY_VALUE: '键值对',
    LIST: '列表',
    TABLE: '表格',
  }
  return labelMap[type] || type || '-'
}

function formatJson() {
  try {
    const obj = JSON.parse(formData.content)
    formData.content = JSON.stringify(obj, null, 2)
  } catch {
    ElMessage.error('JSON格式错误，无法格式化')
  }
}

function formatJsonView(content: string): string {
  try {
    const obj = JSON.parse(content)
    return JSON.stringify(obj, null, 2)
  } catch {
    return content
  }
}

function addKvItem() {
  kvList.value.push({ key: '', value: '' })
}

function removeKvItem(index: number) {
  kvList.value.splice(index, 1)
}

function addListItem() {
  listItems.value.push('')
}

function removeListItem(index: number) {
  listItems.value.splice(index, 1)
}

function addTableRow() {
  const row: Record<string, string> = {}
  tableColumns.value.forEach(col => {
    row[col.key] = ''
  })
  tableRows.value.push(row)
}

function removeTableRow(index: number) {
  tableRows.value.splice(index, 1)
}

function addTableColumn() {
  const colIndex = tableColumns.value.length + 1
  const key = `col${colIndex}`
  tableColumns.value.push({ key, label: `列${colIndex}` })
  tableRows.value.forEach(row => {
    row[key] = ''
  })
}

function parseKeyValue(content: string): { key: string; value: string }[] {
  try {
    return JSON.parse(content)
  } catch {
    return []
  }
}

function parseList(content: string): string[] {
  try {
    return JSON.parse(content)
  } catch {
    return []
  }
}

function parseTable(content: string): { columns: { key: string; label: string }[]; rows: Record<string, string>[] } {
  try {
    return JSON.parse(content)
  } catch {
    return { columns: [], rows: [] }
  }
}

function buildContent(): string {
  if (formData.configType === 'KEY_VALUE') {
    return JSON.stringify(kvList.value.filter(item => item.key))
  }
  if (formData.configType === 'LIST') {
    return JSON.stringify(listItems.value.filter(item => item))
  }
  if (formData.configType === 'TABLE') {
    return JSON.stringify({ columns: tableColumns.value, rows: tableRows.value })
  }
  return formData.content
}

function loadContentFromData(content: string, configType: string) {
  formData.content = content || ''
  kvList.value = []
  listItems.value = []
  tableColumns.value = [{ key: 'col1', label: '列1' }]
  tableRows.value = []

  if (configType === 'KEY_VALUE') {
    try {
      const arr = JSON.parse(content)
      if (Array.isArray(arr)) {
        kvList.value = arr
      }
    } catch {}
  } else if (configType === 'LIST') {
    try {
      const arr = JSON.parse(content)
      if (Array.isArray(arr)) {
        listItems.value = arr
      }
    } catch {}
  } else if (configType === 'TABLE') {
    try {
      const obj = JSON.parse(content)
      if (obj.columns && obj.rows) {
        tableColumns.value = obj.columns
        tableRows.value = obj.rows
      }
    } catch {}
  }
}

watch(() => formData.configType, (newType, oldType) => {
  if (newType !== oldType) {
    const oldContent = buildContent()
    loadContentFromData('', newType)
  }
})

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/items/list', {
      params: {
        type: 'config',
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
  searchForm.configType = ''
  handleSearch()
}

function resetForm() {
  formData.name = ''
  formData.category = 'OTHER'
  formData.configType = 'TEXT'
  formData.description = ''
  formData.content = ''
  formData.sortOrder = 0
  kvList.value = []
  listItems.value = []
  tableColumns.value = [{ key: 'col1', label: '列1' }]
  tableRows.value = []
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  dialogTitle.value = '新增配置'
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑配置'
  resetForm()
  
  formData.name = row.name
  formData.category = row.category
  formData.configType = row.extra?.configType || 'TEXT'
  formData.description = row.description
  formData.sortOrder = row.sortOrder
  
  const content = row.extra?.content || ''
  loadContentFromData(content, formData.configType)
  
  dialogVisible.value = true
}

async function handleView(row: any) {
  viewData.value = row
  viewConfigType.value = row.extra?.configType || 'TEXT'
  viewContent.value = row.extra?.content || ''
  viewDialogVisible.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除配置「${row.name}」吗？`, '提示', {
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
    const content = buildContent()
    const extra = {
      configType: formData.configType,
      content,
    }
    const payload = {
      type: 'config',
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
.kv-editor,
.list-editor,
.table-editor {
  width: 100%;

  .kv-row,
  .list-row {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
    align-items: center;
  }
}

.view-content {
  .view-content-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 12px;
  }

  .json-viewer,
  .text-view {
    background: #f5f7fa;
    padding: 12px;
    border-radius: 4px;
    overflow-x: auto;
    max-height: 400px;
    overflow-y: auto;

    pre {
      margin: 0;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 13px;
      line-height: 1.6;
    }
  }

  .list-view {
    list-style: none;
    padding: 0;
    margin: 0;

    li {
      padding: 8px 12px;
      border-bottom: 1px solid #ebeef5;

      &:last-child {
        border-bottom: none;
      }

      &::before {
        content: '•';
        color: #409eff;
        margin-right: 8px;
      }
    }
  }
}
</style>
