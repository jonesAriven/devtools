<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const activeTab = ref('profile')

const user = computed(() => authStore.user)
const avatarText = computed(
  () => user.value?.nickname?.charAt(0) || user.value?.username?.charAt(0) || 'A',
)

// 修改密码表单
const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleChangePwd() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    pwdLoading.value = true
    try {
      // TODO: 调用修改密码 API
      ElMessage.success('密码修改成功（演示）')
      pwdFormRef.value?.resetFields()
    } catch (e: any) {
      ElMessage.error(e?.message || '修改失败')
    } finally {
      pwdLoading.value = false
    }
  })
}
</script>

<template>
  <div class="settings-page">
    <el-tabs v-model="activeTab" tab-position="left" class="settings-tabs">
      <!-- 个人资料 -->
      <el-tab-pane label="个人资料" name="profile">
        <div class="pane-content">
          <h3 class="pane-title">个人资料</h3>
          <div class="profile-box">
            <el-avatar :size="80" class="profile-avatar">{{ avatarText }}</el-avatar>
            <div class="profile-fields">
              <div class="field-row">
                <span class="field-label">昵称</span>
                <span class="field-value">{{ user?.nickname || '-' }}</span>
              </div>
              <div class="field-row">
                <span class="field-label">用户名</span>
                <span class="field-value">{{ user?.username || '-' }}</span>
              </div>
              <div class="field-row">
                <span class="field-label">邮箱</span>
                <span class="field-value">{{ user?.email || '-' }}</span>
              </div>
              <div class="field-row">
                <span class="field-label">手机</span>
                <span class="field-value">{{ user?.phone || '-' }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 安全设置 -->
      <el-tab-pane label="安全设置" name="security">
        <div class="pane-content">
          <h3 class="pane-title">修改密码</h3>
          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-width="100px"
            class="pwd-form"
          >
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pwdLoading" @click="handleChangePwd">
                确认修改
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- API Token -->
      <el-tab-pane label="API Token" name="token">
        <div class="pane-content">
          <h3 class="pane-title">API Token</h3>
          <el-empty description="API Token 管理功能开发中" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.settings-page {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  min-height: 600px;
}

.settings-tabs {
  min-height: 560px;

  :deep(.el-tabs__header) {
    width: 160px;
  }

  :deep(.el-tabs__item) {
    text-align: left;
  }
}

.pane-content {
  padding: 8px 24px;
}

.pane-title {
  font-size: 18px;
  font-weight: 700;
  color: #1a2332;
  margin: 0 0 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef0;
}

/* 个人资料 */
.profile-box {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.profile-avatar {
  background: #1a2332;
  color: #d4a574;
  font-size: 32px;
  font-weight: 700;
  flex-shrink: 0;
}

.profile-fields {
  flex: 1;
}

.field-row {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f5f3f0;

  & + .field-row {
    border-top: 1px solid #f5f3f0;
  }
}

.field-label {
  width: 80px;
  color: #95a5a6;
  font-size: 14px;
  flex-shrink: 0;
}

.field-value {
  color: #2c3e50;
  font-size: 14px;
  font-weight: 500;
}

/* 修改密码 */
.pwd-form {
  max-width: 480px;
}
</style>
