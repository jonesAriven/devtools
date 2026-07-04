<template>
  <div class="services-view">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索服务名"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        <div class="toolbar-right">
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增服务
          </el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="服务名" min-width="120" />
        <el-table-column prop="hostName" label="主机" width="140" />
        <el-table-column prop="serviceType" label="类型" width="120" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="port" label="端口" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑服务' : '新增服务'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="服务名" prop="name">
          <el-input v-model="form.name" placeholder="请输入服务名" />
        </el-form-item>
        <el-form-item label="主机" prop="hostId">
          <el-input-number v-model="form.hostId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="服务类型" prop="serviceType">
          <el-input v-model="form.serviceType" placeholder="请输入服务类型" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本号" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已停止" value="STOPPED" />
            <el-option label="异常" value="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getServiceList, createService, updateService, deleteService } from '@/api/service'
import type { OpsService, ServiceRequest } from '@/types'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const list = ref<OpsService[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<ServiceRequest>({
  hostId: 1,
  name: '',
  serviceType: '',
  version: '',
  port: 80,
  status: 'RUNNING',
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入服务名', trigger: 'blur' }],
  hostId: [{ required: true, message: '请选择主机', trigger: 'blur' }],
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    RUNNING: 'success',
    STOPPED: 'info',
    ERROR: 'danger',
  }
  return map[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getServiceList({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
    })
    list.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleSizeChange() {
  page.value = 1
  loadData()
}

function resetForm() {
  form.hostId = 1
  form.name = ''
  form.serviceType = ''
  form.version = ''
  form.port = 80
  form.status = 'RUNNING'
  form.remark = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: OpsService) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    hostId: row.hostId,
    name: row.name,
    serviceType: row.serviceType,
    version: row.version,
    port: row.port,
    status: row.status,
    remark: row.remark,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateService(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await createService(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: OpsService) {
  try {
    await ElMessageBox.confirm(`确定删除服务「${row.name}」吗？`, '提示', {
      type: 'warning',
    })
    await deleteService(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>

<style scoped>
.services-view {
  :deep(.el-table) {
    margin-top: 0;
  }
}
</style>
