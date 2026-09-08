<template>
  <div class="login-page">
    <LoginPanel 
      :config="loginConfig" 
      @login="handleLogin" 
      @sso-login="handleSsoLogin"
      @password-reset="handlePasswordReset"
    />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPanel } from '@marschat/auth-components'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { loading, login } = useAuth()

const loginConfig = {
  title: '知识库系统',
  subtitle: '请登录您的账号',
  icon: 'Reading',
  color: '#667eea',
  showSso: true,
  showForgotPassword: true,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'kb-web',
    redirectUri: `${window.location.origin}/login`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
    dividerText: '或',
  },
}

async function handleLogin(credentials: { username: string; password: string }) {
  try {
    await login(credentials.username, credentials.password)
    ElMessage.success('登录成功')
    const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '登录失败')
  }
}

function handleSsoLogin() {
  window.location.href = `/api/auth/sso/authorize?redirect=${encodeURIComponent(window.location.origin)}`
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
