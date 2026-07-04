<template>
  <div class="dependencies-view">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索依赖名/类型"
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
            新增依赖
          </el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="依赖名" min-width="140" />
        <el-table-column prop="depType" label="类型" width="120" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="hostName" label="主机" width="140" />
        <el-table-column prop="installPath" label="安装路径" min-width="180" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑依赖' : '新增依赖'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="依赖名" prop="name">
          <el-input v-model="form.name" placeholder="请输入依赖名" />
        </el-form-item>
        <el-form-item label="依赖类型" prop="depType">
          <el-select v-model="form.depType" placeholder="请选择类型" style="width: 100%">
            <el-option label="JDK" value="JDK" />
            <el-option label="Node.js" value="NODE" />
            <el-option label="Python" value="PYTHON" />
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Redis" value="REDIS" />
            <el-option label="Nginx" value="NGINX" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本号" />
        </el-form-item>
        <el-form-item label="主机ID" prop="hostId">
          <el-input-number v-model="form.hostId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="安装路径" prop="installPath">
          <el-input v-model="form.installPath" placeholder="请输入安装路径" />
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
import { getDependencyList, createDependency, updateDependency, deleteDependency } from '@/api/dependency'
import type { Dependency, DependencyRequest } from '@/types'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const list = ref<Dependency[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<DependencyRequest>({
  name: '',
  depType: 'OTHER',
  version: '',
  hostId: 1,
  installPath: '',
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入依赖名', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await getDependencyList({
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
  form.name = ''
  form.depType = 'OTHER'
  form.version = ''
  form.hostId = 1
  form.installPath = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: Dependency) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    name: row.name,
    depType: row.depType,
    version: row.version,
    hostId: row.hostId,
    installPath: row.installPath,
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
      await updateDependency(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await createDependency(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: Dependency) {
  try {
    await ElMessageBox.confirm(`确定删除依赖「${row.name}」吗？`, '提示', {
      type: 'warning',
    })
    await deleteDependency(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>

<style scoped>
.dependencies-view {
  :deep(.el-table) {
    margin-top: 0;
  }
}
</style>
