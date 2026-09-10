<template>
  <LoginPage
    :config="loginConfig"
    @sso-login="handleSsoLogin"
    @password-reset="handlePasswordReset"
  />
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginConfig = {
  title: 'MarsChat 工具看板',
  subtitle: '统一入口 · 系统导航 · 状态监控',
  icon: 'Monitor',
  color: '#667eea',
  showSso: true,
  showForgotPassword: true,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'marschat-portal',
    redirectUri: `${window.location.origin}/login`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
    dividerText: '或',
  },
  // 独立登录：LoginPage 会在成功后自动提示并跳转
  onLogin: async (credentials: { username: string; password: string }) => {
    await userStore.login(credentials)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  },
  brand: {
    tagline: '一个入口，掌控所有内部系统',
    highlights: [
      { icon: 'Menu', title: '统一导航', desc: '一个入口直达所有内部系统' },
      { icon: 'Monitor', title: '状态监控', desc: '实时掌握服务运行状态' },
      { icon: 'Setting', title: '系统管理', desc: '集中管理工具与配置' },
    ],
    gradient: ['#27245e', '#4b3fa8'],
  },
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}

function handleSsoLogin() {
  window.location.href = `/portal/api/auth/sso/authorize?redirect=${encodeURIComponent(window.location.origin)}`
}
</script>
