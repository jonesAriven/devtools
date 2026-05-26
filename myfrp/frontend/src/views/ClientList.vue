<template>
  <div>
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <h2>客户端管理</h2>
      <el-button type="primary" @click="openDialog(null)" v-if="canEdit">新增客户端</el-button>
    </div>

    <el-table :data="clients" stripe v-loading="loading" @row-click="showTunnels">
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="host" label="内网地址" width="160" />
      <el-table-column label="关联服务端" width="160">
        <template #default="{ row }">
          {{ getServerName(row.serverId) }}
        </template>
      </el-table-column>
      <el-table-column prop="configFormat" label="格式" width="80" />
      <el-table-column prop="osType" label="系统" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click.stop="openDialog(row)" v-if="canEdit">编辑</el-button>
          <el-button size="small" type="success" @click.stop="previewConfig(row.id)">预览</el-button>
          <el-button size="small" type="danger" @click.stop="handleDelete(row)" v-if="canEdit">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!clients.length && !loading" description="暂无客户端" />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="650px">
      <el-form ref="formRef" :model="form" label-width="120px" :rules="rules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="关联服务端" prop="serverId">
          <el-select v-model="form.serverId" style="width: 100%">
            <el-option v-for="s in servers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="内网地址" prop="host">
          <el-input v-model="form.host" placeholder="192.168.31.x" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="配置路径">
              <el-input v-model="form.configPath" placeholder="/etc/frp/frpc.toml" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="配置格式">
              <el-select v-model="form.configFormat">
                <el-option label="TOML" value="toml" />
                <el-option label="INI" value="ini" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider>SSH 连接信息</el-divider>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="SSH 地址">
              <el-input v-model="form.sshHost" placeholder="FRP 隧道地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SSH 端口">
              <el-input-number v-model="form.sshPort" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="SSH 用户">
              <el-input v-model="form.sshUser" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SSH 密码">
              <el-input v-model="form.sshPwd" type="password" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="系统类型">
              <el-select v-model="form.osType">
                <el-option label="Linux" value="linux" />
                <el-option label="Windows" value="windows" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="重启命令">
          <el-input v-model="form.frpcCmd" placeholder="留空则自动生成" />
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
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clientApi, serverApi } from '../utils/api'

const router = useRouter()
const role = localStorage.getItem('role')
const canEdit = computed(() => role === 'ADMIN')
const clients = ref([])
const servers = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const initForm = () => ({
  name: '', serverId: null, host: '', configPath: '/etc/frp/frpc.toml',
  configFormat: 'toml', sshHost: '', sshPort: 22, sshUser: '', sshPwd: '',
  osType: 'linux', frpcCmd: '', status: 1
})

const form = reactive(initForm())

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  serverId: [{ required: true, message: '请选择服务端', trigger: 'change' }],
  host: [{ required: true, message: '请输入内网地址', trigger: 'blur' }]
}

const getServerName = (id) => {
  const s = servers.value.find(s => s.id === id)
  return s ? s.name : '-'
}

const fetchData = async () => {
  loading.value = true
  try {
    const [cRes, sRes] = await Promise.all([clientApi.list(), serverApi.list()])
    clients.value = cRes.data
    servers.value = sRes.data
  } finally {
    loading.value = false
  }
}

const openDialog = (client) => {
  editingId.value = client?.id || null
  dialogTitle.value = client ? '编辑客户端' : '新增客户端'
  Object.assign(form, client ? { ...client } : initForm())
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await clientApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await clientApi.create(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchData()
  } finally {
    saving.value = false
  }
}

const handleDelete = (client) => {
  ElMessageBox.confirm(`确定删除客户端「${client.name}」？`, '警告', { type: 'warning' }).then(async () => {
    await clientApi.delete(client.id)
    ElMessage.success('删除成功')
    await fetchData()
  }).catch(() => {})
}

const previewConfig = (id) => {
  router.push(`/preview/client/${id}`)
}

const showTunnels = (row) => {
  router.push(`/tunnels?clientId=${row.id}`)
}

onMounted(fetchData)
</script>
