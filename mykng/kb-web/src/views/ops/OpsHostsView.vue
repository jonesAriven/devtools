<template>
  <div class="ops-hosts">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>主机管理</span>
          <el-button type="primary" @click="showDialog()">新增主机</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="sshPort" label="SSH端口" width="80" />
        <el-table-column prop="os" label="系统" width="100" />
        <el-table-column prop="cpuCores" label="CPU" width="60" />
        <el-table-column prop="memoryMb" label="内存(MB)" width="90" />
        <el-table-column prop="diskGb" label="磁盘(GB)" width="90" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑主机' : '新增主机'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="IP"><el-input v-model="form.ip" /></el-form-item>
        <el-form-item label="SSH端口"><el-input-number v-model="form.sshPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="系统"><el-input v-model="form.os" /></el-form-item>
        <el-form-item label="CPU核数"><el-input-number v-model="form.cpuCores" :min="1" /></el-form-item>
        <el-form-item label="内存(MB)"><el-input-number v-model="form.memoryMb" :min="0" /></el-form-item>
        <el-form-item label="磁盘(GB)"><el-input-number v-model="form.diskGb" :min="0" /></el-form-item>
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
import { getHostList, createHost, updateHost, deleteHost } from '@/api/ops'
import type { OpsHost } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref<OpsHost[]>([])
const dialogVisible = ref(false)
const form = reactive<Partial<OpsHost>>({})

async function loadData() {
  loading.value = true
  try {
    const res = await getHostList({ page: 1, size: 100 })
    list.value = res.data.data.list
  } catch { ElMessage.error('加载主机列表失败') }
  finally { loading.value = false }
}

function showDialog(row?: any) {
  Object.keys(form).forEach(k => delete (form as any)[k])
  if (row) Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.id) await updateHost(form.id, form)
    else await createHost(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { ElMessage.error('保存失败') }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确认删除该主机？', '提示', { type: 'warning' })
  await deleteHost(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.header-bar { display: flex; justify-content: space-between; align-items: center; }

@media (max-width: 768px) {
  .header-bar { flex-wrap: wrap; gap: 8px; }
  .ops-hosts :deep(.el-table) { font-size: 12px; }
}
</style>
