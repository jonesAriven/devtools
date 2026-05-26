<template>
  <div>
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <h2>服务端管理</h2>
      <el-button type="primary" @click="openDialog(null)" v-if="canEdit">新增服务端</el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="8" v-for="server in servers" :key="server.id" style="margin-bottom: 20px">
        <el-card shadow="hover">
          <div class="server-card">
            <div class="server-header">
              <span class="server-name">{{ server.name }}</span>
              <el-tag :type="server.status === 1 ? 'success' : 'danger'" size="small">
                {{ server.status === 1 ? '已启用' : '已停用' }}
              </el-tag>
            </div>
            <div class="server-info">
              <p><el-icon><Monitor /></el-icon> {{ server.host }}</p>
              <p><el-icon><Link /></el-icon> 端口: {{ server.bindPort || 7000 }}</p>
              <p v-if="server.dashboardPort"><el-icon><DataBoard /></el-icon> 仪表盘: :{{ server.dashboardPort }}</p>
              <p v-if="server.remark" class="remark">{{ server.remark }}</p>
            </div>
            <div class="server-actions" v-if="canEdit">
              <el-button size="small" @click="openDialog(server)">编辑</el-button>
              <el-button size="small" type="success" @click="previewConfig(server.id)">预览配置</el-button>
              <el-button size="small" type="danger" @click="handleDelete(server)">删除</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!servers.length" description="暂无服务端" />

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="form" label-width="120px" :rules="rules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="地址" prop="host">
          <el-input v-model="form.host" placeholder="IP 或域名" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="绑定端口" prop="bindPort">
              <el-input-number v-model="form.bindPort" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Token">
              <el-input v-model="form.token" type="password" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="仪表盘端口">
              <el-input-number v-model="form.dashboardPort" :min="0" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="HTTP 端口">
              <el-input-number v-model="form.vhostHttpPort" :min="0" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Link, DataBoard } from '@element-plus/icons-vue'
import { serverApi } from '../utils/api'

const router = useRouter()
const role = localStorage.getItem('role')
const canEdit = computed(() => role === 'ADMIN')
const servers = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const initForm = () => ({
  name: '', host: '', bindPort: 7000, token: '',
  dashboardPort: 7500, dashboardUser: '', dashboardPwd: '',
  vhostHttpPort: null, remark: '', status: 1
})

const form = reactive(initForm())

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入地址', trigger: 'blur' }],
  bindPort: [{ required: true, message: '请输入绑定端口', trigger: 'blur' }]
}

const fetchList = async () => {
  const res = await serverApi.list()
  servers.value = res.data
}

const openDialog = (server) => {
  editingId.value = server?.id || null
  dialogTitle.value = server ? '编辑服务端' : '新增服务端'
  Object.assign(form, server ? { ...server } : initForm())
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await serverApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await serverApi.create(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

const handleDelete = (server) => {
  ElMessageBox.confirm(`确定删除服务端「${server.name}」？`, '警告', { type: 'warning' }).then(async () => {
    await serverApi.delete(server.id)
    ElMessage.success('删除成功')
    await fetchList()
  }).catch(() => {})
}

const previewConfig = (id) => {
  router.push(`/preview/server/${id}`)
}

onMounted(fetchList)
</script>

<style scoped>
.server-card { min-height: 160px; }
.server-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.server-name { font-size: 18px; font-weight: bold; }
.server-info p { margin: 6px 0; color: #606266; display: flex; align-items: center; gap: 6px; }
.server-info .remark { color: #909399; font-size: 13px; }
.server-actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
</style>
