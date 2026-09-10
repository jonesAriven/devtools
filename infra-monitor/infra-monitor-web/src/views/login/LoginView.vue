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
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPanel } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginConfig = {
  title: '基础设施监控平台',
  subtitle: '请登录您的账号',
  icon: 'Monitor',
  color: '#409eff',
  showSso: true,
  showForgotPassword: true,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'marschat-inframon',
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
    await userStore.login(credentials.username, credentials.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
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
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}
</style>
