<template>
  <div class="ops-services">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>服务管理</span>
          <el-button type="primary" @click="showDialog()">新增服务</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="服务名" width="150" />
        <el-table-column prop="hostName" label="主机" width="120" />
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text @click="showDialog(row)">编辑</el-button>
            <el-button text type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑服务' : '新增服务'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="服务名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="主机ID"><el-input-number v-model="form.hostId" :min="1" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.type" /></el-form-item>
        <el-form-item label="健康检查"><el-input v-model="form.healthCheckUrl" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getServiceList, createService, updateService, deleteService } from '@/api/ops'
import type { OpsService } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref<OpsService[]>([])
const dialogVisible = ref(false)
const form = reactive<Partial<OpsService>>({})

async function loadData() {
  loading.value = true
  try {
    const res = await getServiceList({ page: 1, pageSize: 100 })
    list.value = res.data.data.list
  } catch { ElMessage.error('加载服务列表失败') }
  finally { loading.value = false }
}

function showDialog(row?: any) {
  Object.keys(form).forEach(k => delete (form as any)[k])
  if (row) Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.id) await updateService(form.id, form)
    else await createService(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { ElMessage.error('保存失败') }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确认删除该服务？', '提示', { type: 'warning' })
  await deleteService(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.header-bar { display: flex; justify-content: space-between; align-items: center; }

@media (max-width: 768px) {
  .header-bar { flex-wrap: wrap; gap: 8px; }
  .ops-services :deep(.el-table) { font-size: 12px; }
}
</style>
