<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">FRP 管理平台</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <div class="forgot-line">
          <a class="forgot-link" href="https://auth.marschat.online/forgot-password.html" target="_blank" rel="noopener">忘记密码？</a>
        </div>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <el-divider>或</el-divider>
      <el-button
        type="success"
        class="sso-btn"
        @click="handleSsoLogin"
      >
        <el-icon><Connection /></el-icon>
        统一认证登录（SSO）
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Connection } from '@element-plus/icons-vue'
import { authApi } from '../utils/api'
import { ElMessage } from 'element-plus'
import { startSsoLogin } from '../utils/sso'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await authApi.login(form)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('username', res.data.username)
    localStorage.setItem('role', res.data.role)
    // 标记为 legacy token
    localStorage.setItem('frp_token_kind', 'legacy')
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

/** SSO 统一认证登录：跳转到 auth-center 授权端点 */
async function handleSsoLogin() {
  try {
    await startSsoLogin('/')
  } catch (e) {
    ElMessage.error(e?.message || 'SSO 登录发起失败')
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #303133;
  font-size: 24px;
}
.sso-btn {
  width: 100%;
}
.forgot-line {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 14px;
}
.forgot-link {
  font-size: 13px;
  color: #409eff;
  text-decoration: none;
}
.forgot-link:hover {
  text-decoration: underline;
}
</style>
