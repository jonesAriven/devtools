<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <el-icon :size="40" color="#409eff"><Reading /></el-icon>
        <h2 class="login-title">知识库系统</h2>
        <p class="login-subtitle">请登录您的账号</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
        <el-divider class="login-divider">
          <span class="divider-text">或</span>
        </el-divider>
        <el-form-item>
          <el-button class="login-btn sso-btn" @click="handleSsoLogin">
            统一认证登录（SSO）
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuth } from '@/composables/useAuth'
import { useRouter } from 'vue-router'
import { startSsoLogin } from '@/utils/sso'

const router = useRouter()
const loginFormRef = ref<FormInstance>()
const { loading, login } = useAuth()

const loginForm = reactive({
  username: '',
  password: '',
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  await login(loginForm.username, loginForm.password)
}

/** auth-center SSO：记录回跳目标后跳授权端点 */
async function handleSsoLogin() {
  const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
  await startSsoLogin(redirect)
}
</script>

<style scoped lang="scss">
.login-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 90%;
  max-width: 400px;
  padding: 32px 24px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 12px 0 4px;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
}

.login-btn {
  width: 100%;
}

.login-divider {
  margin: 4px 0 12px;

  .divider-text {
    font-size: 12px;
    color: #c0c4cc;
  }
}

.sso-btn {
  color: #409eff;
  border-color: #b3d8ff;
}

@media (max-width: 768px) {
  .login-card {
    padding: 24px 16px;
  }

  .login-title {
    font-size: 20px;
  }
}
</style>
