<template>
  <div class="credentials-view">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名/类型"
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
            新增凭据
          </el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="credType" label="类型" width="120" />
        <el-table-column prop="hostName" label="主机" width="140" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column label="密码" width="120">
          <template #default="{ row }">
            <span v-if="row.passwordHint">{{ row.passwordHint }}</span>
            <span v-else class="muted">-</span>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑凭据' : '新增凭据'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="主机ID" prop="hostId">
          <el-input-number v-model="form.hostId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="凭据类型" prop="credType">
          <el-select v-model="form.credType" placeholder="请选择类型" style="width: 100%">
            <el-option label="SSH" value="SSH" />
            <el-option label="数据库" value="DATABASE" />
            <el-option label="Web" value="WEB" />
            <el-option label="API" value="API" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="密码提示" prop="passwordHint">
          <el-input v-model="form.passwordHint" placeholder="请输入密码提示" />
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
import { getCredentialList, createCredential, updateCredential, deleteCredential } from '@/api/credential'
import type { Credential, CredentialRequest } from '@/types'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const list = ref<Credential[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<CredentialRequest>({
  hostId: 1,
  credType: 'SSH',
  username: '',
  password: '',
  passwordHint: '',
  remark: '',
})

const rules: FormRules = {
  credType: [{ required: true, message: '请选择凭据类型', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCredentialList({
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
  form.credType = 'SSH'
  form.username = ''
  form.password = ''
  form.passwordHint = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: Credential) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    hostId: row.hostId,
    credType: row.credType,
    username: row.username,
    password: '',
    passwordHint: row.passwordHint,
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
      await updateCredential(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await createCredential(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: Credential) {
  try {
    await ElMessageBox.confirm(`确定删除凭据「${row.username}」吗？`, '提示', {
      type: 'warning',
    })
    await deleteCredential(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>

<style scoped>
.credentials-view {
  :deep(.el-table) {
    margin-top: 0;
  }
}

.muted {
  color: #909399;
}
</style>
