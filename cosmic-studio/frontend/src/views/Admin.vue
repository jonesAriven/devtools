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
        <h3>用户管理</h3>
        <el-button type="primary" @click="userDlg = true">新建用户</el-button>
      </div>
      <el-table :data="userList" v-loading="userLoading" row-key="id"
                @selection-change="userSel = $event">
        <el-table-column type="selection" width="46" :selectable="canDelete" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="140">
          <template #default="s">
            {{ s.row.username }}
            <el-tag v-if="s.row.id === myId" size="small" type="info" effect="plain"
                    style="margin-left:4px">当前登录</el-tag>
          </template>
        </el-table-column>
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
        <el-table-column prop="created_at" label="创建时间" width="170" :formatter="formatDateTime" />
        <el-table-column label="操作" width="170">
          <template #default="s">
            <el-button size="small" @click="editUser(s.row)">编辑</el-button>
            <el-button size="small" type="danger" plain :disabled="!canDelete(s.row)"
                       @click="delUsers([s.row])">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无用户" /></template>
      </el-table>

      <div class="bar" v-if="userSel.length">
        <span class="muted">已选 {{ userSel.length }} 个用户</span>
        <div class="bar-actions">
          <el-button size="small" type="danger" :loading="deleting"
                     @click="delUsers(userSel)">批量删除</el-button>
        </div>
      </div>

      <!-- 用户量级有限，客户端切片即可，不必为此改后端 -->
      <div class="pager">
        <el-pagination v-model:current-page="userPage" v-model:page-size="userPageSize"
                       :total="userTotal" :page-sizes="[10, 20, 50]" background
                       layout="total, sizes, prev, pager, next" />
      </div>

      <p class="muted" style="margin-top:var(--sp-2)">
        删除为不可恢复操作：会连同该用户的对话日志一并清除。不能删除当前登录账号，
        也不能删除最后一个可用的管理员（想临时停用请用「启用」开关）。
      </p>

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
import { onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { user as currentUser } from '../api'
import { useLocalPaged } from '../composables/usePaged'
import { formatDateTime } from '../utils/format'

const llm = reactive({ enabled: false, base_url: '', model: '', api_key: '', key_masked: '' })
const allUsers = ref([])
const userLoading = ref(false)
const userSel = ref([])
const deleting = ref(false)
const myId = (currentUser() || {}).id

// 用户量级有限，客户端切片即可
const {
  list: userList, total: userTotal, page: userPage, pageSize: userPageSize,
  loading: _ul, error: _ue, empty: _uEmpty,
} = useLocalPaged(allUsers, 20)

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
// keep-alive：切回时刷一次用户列表（新创建/删除的用户可见）
onActivated(() => loadUsers())
async function loadUsers() {
  userLoading.value = true
  try { allUsers.value = (await api.get('/auth/users')).data }
  finally { userLoading.value = false }
}
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

// ── 删除用户 ──
// 前端先把两条硬规则挡掉（自己 / 最后一个可用 admin），其余交给后端兜底
function canDelete(row) {
  return row.id !== myId
}
async function delUsers(rows) {
  const targets = rows.filter(canDelete)
  if (!targets.length) return
  const names = targets.map(r => r.username).slice(0, 5).join('、')
  const more = targets.length > 5 ? ` 等 ${targets.length} 个` : ''
  await ElMessageBox.confirm(
    `确认删除用户「${names}」${more}？该操作不可恢复，会连同其对话日志一并清除。`,
    '删除用户', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  deleting.value = true
  try {
    const { data } = await api.post('/auth/users/bulk-delete',
      { ids: targets.map(r => r.id) })
    if (data.deleted_count) ElMessage.success(`已删除 ${data.deleted_count} 个用户`)
    for (const f of data.failed || []) ElMessage.warning(`id=${f.id}：${f.reason}`)
    userSel.value = []
    loadUsers()
  } catch (e) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error(e.response?.data?.detail || '删除失败')
    }
  } finally { deleting.value = false }
}
</script>

<!-- .bar 已迁入 theme.css -->
<style scoped>
</style>
