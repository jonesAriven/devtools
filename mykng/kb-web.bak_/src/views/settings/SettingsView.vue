<template>
  <div class="settings-page">
    <div class="page-title">系统设置</div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="个人信息" name="profile">
        <div class="info-card">
          <el-form :model="profileForm" label-width="100px" class="settings-form">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.profile?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleUpdateProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <div class="info-card">
          <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px" class="settings-form">
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane label="存储桶管理" name="bucket">
        <div class="info-card">
          <div class="card-header">
            <div class="card-title">存储桶列表</div>
            <el-button type="primary" size="small" @click="showBucketDialog = true">新增存储桶</el-button>
          </div>
          <el-table :data="bucketList" stripe style="width: 100%">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="provider" label="提供商" width="120" />
            <el-table-column prop="bucketName" label="桶名" min-width="120" />
            <el-table-column prop="isDefault" label="默认" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isDefault ? 'success' : 'info'" size="small">
                  {{ row.isDefault ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleTestBucket(row.id)">测试连接</el-button>
                <el-button link type="primary" size="small" @click="handleSetDefault(row.id)">设为默认</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteBucket(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-dialog v-model="showBucketDialog" title="新增存储桶" width="90%" style="max-width: 600px">
          <el-form :model="bucketForm" label-width="100px">
            <el-form-item label="名称">
              <el-input v-model="bucketForm.name" />
            </el-form-item>
            <el-form-item label="提供商">
              <el-select v-model="bucketForm.provider" style="width: 100%">
                <el-option label="MinIO" value="minio" />
                <el-option label="阿里云OSS" value="aliyun" />
                <el-option label="腾讯云COS" value="tencent" />
                <el-option label="AWS S3" value="aws" />
              </el-select>
            </el-form-item>
            <el-form-item label="Endpoint">
              <el-input v-model="bucketForm.endpoint" />
            </el-form-item>
            <el-form-item label="桶名">
              <el-input v-model="bucketForm.bucketName" />
            </el-form-item>
            <el-form-item label="AccessKey">
              <el-input v-model="bucketForm.accessKey" />
            </el-form-item>
            <el-form-item label="SecretKey">
              <el-input v-model="bucketForm.secretKey" type="password" show-password />
            </el-form-item>
            <el-form-item label="Region">
              <el-input v-model="bucketForm.region" />
            </el-form-item>
            <el-form-item label="设为默认">
              <el-switch v-model="bucketForm.isDefault" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showBucketDialog = false">取消</el-button>
            <el-button type="primary" @click="handleCreateBucket">创建</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="log">
        <div class="info-card">
          <el-table :data="logList" stripe style="width: 100%">
            <el-table-column prop="username" label="用户" width="100" />
            <el-table-column prop="action" label="操作" width="120" />
            <el-table-column prop="resourceType" label="资源类型" width="100" />
            <el-table-column prop="detail" label="详情" min-width="200" />
            <el-table-column prop="ip" label="IP" width="140" />
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="logPage"
              :page-size="20"
              :total="logTotal"
              layout="prev, pager, next"
              @current-change="loadLogs"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="API Token" name="token">
        <div class="info-card">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
            <span style="font-weight: bold">API Token 管理</span>
            <el-button type="primary" size="small" @click="showTokenDialog = true">新建 Token</el-button>
          </div>
          <el-table :data="tokenList" v-loading="tokenLoading" style="width: 100%">
            <el-table-column prop="name" label="名称" width="150" />
            <el-table-column prop="token" label="Token" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span style="font-family: monospace; font-size: 12px">{{ row.token.substring(0, 16) }}...</span>
              </template>
            </el-table-column>
            <el-table-column prop="scopes" label="权限" width="150">
              <template #default="{ row }">
                <el-tag v-for="scope in row.scopes" :key="scope" size="small" style="margin-right: 4px">{{ scope }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="toggleTokenStatus(row.id)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
                <el-button size="small" type="danger" @click="handleDeleteToken(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { updateUserProfile, changePassword } from '@/api/user'
import { getBucketList, createBucket, deleteBucket, testBucketConnection, setDefaultBucket } from '@/api/bucket'
import { getLogList } from '@/api/log'
import { getTokenList, createToken, deleteToken, toggleTokenStatus } from '@/api/token'
import { formatDate } from '@/utils/format'
import type { Bucket, OperationLog, ApiToken, CreateTokenRequest } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const activeTab = ref('profile')

const profileForm = reactive({
  nickname: userStore.profile?.nickname || '',
  email: userStore.profile?.email || '',
})

const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const bucketList = ref<Bucket[]>([])
const showBucketDialog = ref(false)
const bucketForm = reactive({
  name: '',
  provider: 'minio',
  endpoint: '',
  bucketName: '',
  accessKey: '',
  secretKey: '',
  region: '',
  isDefault: false,
})

const logList = ref<OperationLog[]>([])
const logPage = ref(1)
const logTotal = ref(0)

onMounted(() => {
  loadBuckets()
  loadLogs()
})

async function handleUpdateProfile() {
  await updateUserProfile({
    nickname: profileForm.nickname,
    email: profileForm.email,
  })
  ElMessage.success('已更新')
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  await changePassword({
    oldPassword: passwordForm.oldPassword,
    newPassword: passwordForm.newPassword,
  })
  ElMessage.success('密码已修改')
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

async function loadBuckets() {
  const res = await getBucketList()
  bucketList.value = res.data.data
}

async function handleCreateBucket() {
  await createBucket(bucketForm)
  ElMessage.success('创建成功')
  showBucketDialog.value = false
  loadBuckets()
}

async function handleTestBucket(id: number) {
  const res = await testBucketConnection(id)
  if (res.data.data) {
    ElMessage.success('连接成功')
  } else {
    ElMessage.error('连接失败')
  }
}

async function handleSetDefault(id: number) {
  await setDefaultBucket(id)
  ElMessage.success('已设为默认')
  loadBuckets()
}

async function handleDeleteBucket(id: number) {
  await ElMessageBox.confirm('确定要删除此存储桶吗？', '提示', { type: 'warning' })
  await deleteBucket(id)
  ElMessage.success('已删除')
  loadBuckets()
}

async function loadLogs() {
  const res = await getLogList({ page: logPage.value, size: 20 })
  logList.value = res.data.data.list
  logTotal.value = res.data.data.total
}

// ============================================================
// API Token 管理
// ============================================================
const tokenList = ref<ApiToken[]>([])
const tokenLoading = ref(false)
const showTokenDialog = ref(false)
const tokenForm = reactive<CreateTokenRequest>({
  name: '',
  scopes: ['read'],
  expireAt: undefined,
})

async function loadTokens() {
  tokenLoading.value = true
  try {
    const res = await getTokenList({ page: 1, size: 50 })
    tokenList.value = res.data.data.list || []
  } finally {
    tokenLoading.value = false
  }
}

async function handleCreateToken() {
  await createToken(tokenForm)
  ElMessage.success('Token 创建成功')
  showTokenDialog.value = false
  tokenForm.name = ''
  tokenForm.scopes = ['read']
  loadTokens()
}

async function handleDeleteToken(id: number) {
  await ElMessageBox.confirm('确定要删除此 Token 吗？', '提示', { type: 'warning' })
  await deleteToken(id)
  ElMessage.success('已删除')
  loadTokens()
}

async function toggleTokenStat(id: number) {
  await toggleTokenStatus(id)
  ElMessage.success('状态已切换')
  loadTokens()
}

// 监听 tab 切换，加载对应数据
watch(activeTab, (val) => {
  if (val === 'token') loadTokens()
})
</script>

<style scoped lang="scss">
.settings-page {
  .settings-form {
    max-width: 500px;
  }

  .table-wrapper {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 16px;
  }
}

@media (max-width: 768px) {
  .settings-page {
    .settings-form {
      max-width: 100%;
    }
  }
}
</style>
