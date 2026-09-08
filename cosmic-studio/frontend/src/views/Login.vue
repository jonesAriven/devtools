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

const router = useRouter()
const route = useRoute()

const loginConfig = {
  title: 'COSMIC 度量表',
  subtitle: 'COSMIC 度量表生产系统',
  icon: 'DataAnalysis',
  color: '#1d2535',
  showSso: true,
  showForgotPassword: true,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'cosmic-studio',
    redirectUri: `${window.location.origin}/login`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
    dividerText: '或',
  },
}

function handleLogin(credentials: { username: string; password: string }) {
  // cosmic-studio 使用自己的 API
  // 这里需要根据实际 API 调整
  console.log('Login:', credentials)
  ElMessage.success('登录成功')
  const redirect = (route.query.redirect as string) || '/'
  router.push(redirect)
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
  background: linear-gradient(135deg, #1d2535 0%, #313d6b 100%);
}
</style>
