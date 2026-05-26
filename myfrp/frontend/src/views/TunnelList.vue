<template>
  <div>
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <h2>隧道管理</h2>
      <div>
        <el-select v-model="selectedClient" placeholder="选择客户端" style="width: 200px; margin-right: 8px" @change="fetchTunnels" clearable>
          <el-option v-for="c in clients" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="openDialog(null)" v-if="canEdit && selectedClient">新增隧道</el-button>
      </div>
    </div>

    <el-table :data="tunnels" stripe v-loading="loading">
      <el-table-column prop="name" label="隧道名" width="120" />
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.type === 'tcp' ? 'primary' : row.type === 'udp' ? 'warning' : 'success'" size="small">
            {{ row.type?.toUpperCase() }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="本地" width="200">
        <template #default="{ row }">
          {{ row.localIp || '127.0.0.1' }}:{{ row.localPort }}
        </template>
      </el-table-column>
      <el-table-column label="远程端口" prop="remotePort" width="120" />
      <el-table-column label="加密/压缩" width="120">
        <template #default="{ row }">
          <span v-if="row.useEncryption">🔒</span>
          <span v-if="row.useCompression">📦</span>
          <span v-if="!row.useEncryption && !row.useCompression">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)" v-if="canEdit">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)" v-if="canEdit">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!tunnels.length && !loading" description="暂无隧道，请先选择客户端" />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="form" label-width="110px" :rules="rules">
        <el-form-item label="隧道名" prop="name">
          <el-input v-model="form.name" placeholder="如: rdp, ssh" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option label="TCP" value="tcp" />
                <el-option label="UDP" value="udp" />
                <el-option label="HTTP" value="http" />
                <el-option label="HTTPS" value="https" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="本地 IP">
              <el-input v-model="form.localIp" placeholder="127.0.0.1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="本地端口" prop="localPort">
              <el-input-number v-model="form.localPort" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="远程端口" prop="remotePort">
              <el-input-number v-model="form.remotePort" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="加密/压缩">
          <el-switch v-model="form.useEncryption" :active-value="1" :inactive-value="0" active-text="加密" style="margin-right: 20px" />
          <el-switch v-model="form.useCompression" :active-value="1" :inactive-value="0" active-text="压缩" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tunnelApi, clientApi } from '../utils/api'

const route = useRoute()
const role = localStorage.getItem('role')
const canEdit = role === 'ADMIN'

const tunnels = ref([])
const clients = ref([])
const selectedClient = ref(null)
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const initForm = () => ({
  clientId: null, name: '', type: 'tcp', localIp: '127.0.0.1',
  localPort: null, remotePort: null, useEncryption: 0, useCompression: 0,
  status: 1, remark: ''
})

const form = reactive(initForm())

const rules = {
  name: [{ required: true, message: '请输入隧道名', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  localPort: [{ required: true, message: '请输入本地端口', trigger: 'blur' }],
  remotePort: [{ required: true, message: '请输入远程端口', trigger: 'blur' }]
}

const fetchClients = async () => {
  const res = await clientApi.list()
  clients.value = res.data
}

const fetchTunnels = async () => {
  if (!selectedClient.value) {
    tunnels.value = []
    return
  }
  loading.value = true
  try {
    const res = await tunnelApi.list(selectedClient.value)
    tunnels.value = res.data
  } finally {
    loading.value = false
  }
}

const openDialog = (tunnel) => {
  editingId.value = tunnel?.id || null
  dialogTitle.value = tunnel ? '编辑隧道' : '新增隧道'
  const data = tunnel ? { ...tunnel } : initForm()
  data.clientId = selectedClient.value
  Object.assign(form, data)
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  form.clientId = selectedClient.value
  try {
    if (editingId.value) {
      await tunnelApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await tunnelApi.create(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchTunnels()
  } finally {
    saving.value = false
  }
}

const handleDelete = (tunnel) => {
  ElMessageBox.confirm(`确定删除隧道「${tunnel.name}」？`, '警告', { type: 'warning' }).then(async () => {
    await tunnelApi.delete(tunnel.id)
    ElMessage.success('删除成功')
    await fetchTunnels()
  }).catch(() => {})
}

onMounted(async () => {
  await fetchClients()
  if (route.query.clientId) {
    selectedClient.value = parseInt(route.query.clientId)
    await fetchTunnels()
  }
})
</script>
