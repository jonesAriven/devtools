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
import api, { humanize } from '../api'

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
    redirectUri: `${window.location.origin}/sso-callback`,
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
    const { data } = await api.post('/auth/login', credentials)
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (err: any) {
    const detail = err?.response?.data
    ElMessage.error(humanize(detail) || '登录失败，请检查用户名和密码')
  }
}

function handleSsoLogin() {
  // 跳转到 auth-center OIDC 授权端点
  const redirect = (route.query.redirect as string) || window.location.origin
  window.location.href = `/api/auth/sso/authorize?redirect=${encodeURIComponent(redirect)}`
}

function handlePasswordReset() {
  // cosmic-studio 后端暂无邮件验证码接口，提示联系管理员
  ElMessage.info('如需重置密码，请联系系统管理员')
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
