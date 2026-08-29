<template>
  <div>
    <el-card style="margin-bottom:14px">
      <h3 style="margin-top:0">LLM 配置（对话式工作台引擎）</h3>
      <el-form label-width="120px" style="max-width:640px">
        <el-form-item label="启用">
          <el-switch v-model="llm.enabled" />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="llm.base_url" placeholder="如 https://api.deepseek.com/v1（OpenAI 兼容）" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="llm.model" placeholder="如 deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="llm.api_key" type="password" show-password
                    :placeholder="llm.key_masked ? '已保存（留空则不修改）' : 'sk-...'" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveLlm">保存</el-button>
          <el-tag v-if="llm.key_masked" size="small" style="margin-left:8px">当前：{{ llm.key_masked }}</el-tag>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="bar">
        <h3 style="margin:0">用户管理</h3>
        <el-button type="primary" @click="userDlg = true">新建用户</el-button>
      </div>
      <el-table :data="users">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="display_name" label="显示名" width="140" />
        <el-table-column prop="role" label="角色" width="110">
          <template #default="s">
            <el-tag :type="s.row.role === 'admin' ? 'danger' : s.row.role === 'editor' ? 'warning' : 'info'">
              {{ { admin: '管理员', editor: '编辑', viewer: '只读' }[s.row.role] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="s">
            <el-switch :model-value="!!s.row.enabled" @change="v => toggle(s.row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="110">
          <template #default="s">
            <el-button size="small" @click="editUser(s.row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="userDlg" :title="editingId ? '编辑用户' : '新建用户'" width="440px">
        <el-form label-width="80px">
          <el-form-item label="用户名"><el-input v-model="uform.username" :disabled="!!editingId" /></el-form-item>
          <el-form-item label="显示名"><el-input v-model="uform.display_name" /></el-form-item>
          <el-form-item :label="editingId ? '新密码' : '初始密码'">
            <el-input v-model="uform.password" type="password" show-password />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="uform.role">
              <el-option label="只读 viewer" value="viewer" />
              <el-option label="编辑 editor" value="editor" />
              <el-option label="管理员 admin" value="admin" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="userDlg = false">取消</el-button>
          <el-button type="primary" @click="saveUser">保存</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const llm = reactive({ enabled: false, base_url: '', model: '', api_key: '', key_masked: '' })
const users = ref([])
const userDlg = ref(false)
const editingId = ref(null)
const uform = reactive({ username: '', password: '', display_name: '', role: 'viewer' })

onMounted(async () => {
  const { data } = await api.get('/studio/llm-config')
  llm.enabled = !!data.enabled
  llm.base_url = data.base_url || ''
  llm.model = data.model || ''
  llm.key_masked = data.api_key || ''
  loadUsers()
})
async function loadUsers() { users.value = (await api.get('/auth/users')).data }
async function saveLlm() {
  try {
    await api.put('/studio/llm-config', {
      enabled: llm.enabled, base_url: llm.base_url, model: llm.model, api_key: llm.api_key
    })
    ElMessage.success('已保存')
  } catch (e) { ElMessage.error(e.response?.data?.detail || '保存失败') }
}
function editUser(row) {
  editingId.value = row.id
  uform.username = row.username
  uform.display_name = row.display_name
  uform.role = row.role
  uform.password = ''
  userDlg.value = true
}
async function saveUser() {
  try {
    if (editingId.value) await api.put(`/auth/users/${editingId.value}`, uform)
    else await api.post('/auth/users', uform)
    ElMessage.success('已保存')
    userDlg.value = false
    editingId.value = null
    loadUsers()
  } catch (e) { ElMessage.error(e.response?.data?.detail || '失败') }
}
async function toggle(row, v) {
  await api.put(`/auth/users/${row.id}`, { username: row.username, role: row.role, enabled: v })
  loadUsers()
}
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
</style>
