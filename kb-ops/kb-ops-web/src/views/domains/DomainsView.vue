<template>
  <div class="domains-view">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索域名"
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
            新增域名
          </el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="domain" label="主域名" min-width="150" />
        <el-table-column prop="subDomain" label="子域名" width="140" />
        <el-table-column prop="targetHostName" label="目标主机" width="140" />
        <el-table-column prop="targetPort" label="目标端口" width="100" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑域名' : '新增域名'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="主域名" prop="domain">
          <el-input v-model="form.domain" placeholder="请输入主域名，如 example.com" />
        </el-form-item>
        <el-form-item label="子域名" prop="subDomain">
          <el-input v-model="form.subDomain" placeholder="请输入子域名，如 www" />
        </el-form-item>
        <el-form-item label="目标主机ID" prop="targetHostId">
          <el-input-number v-model="form.targetHostId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="目标端口" prop="targetPort">
          <el-input-number v-model="form.targetPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
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
import { getDomainList, createDomain, updateDomain, deleteDomain } from '@/api/domain'
import type { Domain, DomainRequest } from '@/types'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const list = ref<Domain[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<DomainRequest>({
  domain: '',
  subDomain: '',
  targetHostId: 1,
  targetPort: 80,
  status: 'ACTIVE',
  remark: '',
})

const rules: FormRules = {
  domain: [{ required: true, message: '请输入主域名', trigger: 'blur' }],
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    ERROR: 'danger',
  }
  return map[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getDomainList({
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
  form.domain = ''
  form.subDomain = ''
  form.targetHostId = 1
  form.targetPort = 80
  form.status = 'ACTIVE'
  form.remark = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: Domain) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    domain: row.domain,
    subDomain: row.subDomain,
    targetHostId: row.targetHostId,
    targetPort: row.targetPort,
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
      await updateDomain(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await createDomain(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: Domain) {
  try {
    await ElMessageBox.confirm(`确定删除域名「${row.domain}」吗？`, '提示', {
      type: 'warning',
    })
    await deleteDomain(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>

<style scoped>
.domains-view {
  :deep(.el-table) {
    margin-top: 0;
  }
}
</style>
