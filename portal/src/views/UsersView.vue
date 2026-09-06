<template>
  <div class="users-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理（统一账号池）</span>
          <div class="header-actions">
            <el-input
              v-model="realmFilter"
              placeholder="按账号池过滤（如 kb）"
              clearable
              style="width: 220px"
              @keyup.enter="loadUsers"
            />
            <el-button type="primary" @click="loadUsers">查询</el-button>
            <el-button type="success" @click="openCreate">新增用户</el-button>
          </div>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="role" label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'">{{ row.role === 'admin' ? '管理员' : '用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="realmId" label="账号池" width="90" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openEdit(scope.row as CenterUser)">编辑</el-button>
            <el-button size="small" :type="scope.row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(scope.row as CenterUser)">
              {{ scope.row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="primary" plain @click="openResetPwd(scope.row as CenterUser)">重置密码</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row as CenterUser)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="editVisible" :title="editingId ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="editForm.username" :disabled="!!editingId" placeholder="字母数字_.-，2-50位" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" required>
          <el-input v-model="editForm.password" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="editForm.role">
            <el-radio value="user">用户</el-radio>
            <el-radio value="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!editingId" label="账号池">
          <el-input v-model="editForm.realmId" placeholder="默认 kb" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
      <el-input v-model="newPassword" type="password" show-password placeholder="新密码（至少6位）" />
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCenterUsers,
  createCenterUser,
  updateCenterUser,
  deleteCenterUser,
  resetCenterUserPassword,
  type CenterUser
} from '@/api/auth'

const users = ref<CenterUser[]>([])
const loading = ref(false)
const saving = ref(false)
const realmFilter = ref('')

const editVisible = ref(false)
const editingId = ref<number | null>(null)
const editForm = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  role: 'user',
  realmId: ''
})

const pwdVisible = ref(false)
const pwdUserId = ref<number | null>(null)
const newPassword = ref('')

async function loadUsers() {
  loading.value = true
  try {
    users.value = await listCenterUsers(realmFilter.value || undefined)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(editForm, { username: '', password: '', nickname: '', email: '', role: 'user', realmId: '' })
  editVisible.value = true
}

function openEdit(row: CenterUser) {
  editingId.value = row.id
  Object.assign(editForm, {
    username: row.username,
    password: '',
    nickname: row.nickname || '',
    email: row.email || '',
    role: row.role,
    realmId: row.realmId || ''
  })
  editVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateCenterUser(editingId.value, {
        role: editForm.role,
        nickname: editForm.nickname,
        email: editForm.email
      })
      ElMessage.success('修改成功')
    } else {
      await createCenterUser({
        username: editForm.username,
        password: editForm.password,
        role: editForm.role,
        nickname: editForm.nickname,
        email: editForm.email,
        realmId: editForm.realmId || undefined
      })
      ElMessage.success('新增成功')
    }
    editVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: CenterUser) {
  await updateCenterUser(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
  await loadUsers()
}

function openResetPwd(row: CenterUser) {
  pwdUserId.value = row.id
  newPassword.value = ''
  pwdVisible.value = true
}

async function handleResetPwd() {
  if (!pwdUserId.value) return
  saving.value = true
  try {
    await resetCenterUserPassword(pwdUserId.value, newPassword.value)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: CenterUser) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', { type: 'warning' })
  await deleteCenterUser(row.id)
  ElMessage.success('已删除')
  await loadUsers()
}

onMounted(loadUsers)
</script>

<style scoped>
.users-page {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
