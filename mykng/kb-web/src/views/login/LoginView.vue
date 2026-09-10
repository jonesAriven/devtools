<template>
  <LoginPage
    :config="loginConfig"
    @login="handleLogin"
    @password-reset="handlePasswordReset"
  />
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useAuth } from '@/composables/useAuth'
import { startSsoLogin } from '@/utils/sso'

const router = useRouter()
const { loading, login } = useAuth()

const loginConfig = {
  title: '知识库系统',
  subtitle: '请登录您的账号',
  icon: 'Reading',
  color: '#667eea',
  showSso: true,
  showForgotPassword: true,
  // 忘记密码 / 重置密码接口前缀（auth-center 业务 API，经 kb-gateway）
  authApiBase: '/kb/api/auth',
  // SSO 走本应用自己的客户端 PKCE 流（public client，回调 /kb/sso-callback）
  onSsoLogin: handleSsoLogin,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'marschat-kbweb',
    redirectUri: `${window.location.origin}/kb/sso-callback`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
    dividerText: '或',
  },
  brand: {
    tagline: '企业知识资产 · 检索 / 协作 / 智能问答',
    highlights: [
      { icon: 'Search', title: '知识检索', desc: '全文检索，秒级定位文档' },
      { icon: 'Document', title: '文档协同', desc: '多人协作编辑与版本管理' },
      { icon: 'ChatDotRound', title: '智能问答', desc: '基于知识库的 AI 问答' },
    ],
    gradient: ['#15324d', '#1c6e8c'],
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

async function handleSsoLogin() {
  try {
    await startSsoLogin((router.currentRoute.value.query.redirect as string) || '/dashboard')
  } catch (err: any) {
    ElMessage.error(err?.message || 'SSO 登录发起失败')
  }
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}
</script>
