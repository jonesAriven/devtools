<template>
  <LoginPage
    :config="loginConfig"
    @sso-login="handleSsoLogin"
    @password-reset="handlePasswordReset"
  />
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { useRoute, useRouter } from 'vue-router'
import { startSsoLogin } from '@/utils/sso'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const loginConfig = {
  title: '运维管理平台',
  subtitle: '请登录您的账号',
  icon: 'Setting',
  color: '#409eff',
  showSso: true,
  showForgotPassword: true,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'marschat-kbops',
    redirectUri: `${window.location.origin}/login`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
  },
  // 独立登录：LoginPage 成功后自动提示并跳转
  onLogin: async (credentials: { username: string; password: string }) => {
    await userStore.login(credentials.username, credentials.password)
    const redirect = route.query.redirect as string
    router.push(redirect || '/dashboard')
  },
  brand: {
    tagline: '运维一体化平台 · 监控 / 告警 / 自动化',
    highlights: [
      { icon: 'Monitor', title: '服务监控', desc: '实时指标与拓扑可视' },
      { icon: 'Bell', title: '告警通知', desc: '多通道告警即时触达' },
      { icon: 'Switch', title: '运维自动化', desc: '编排任务与批处理' },
    ],
    gradient: ['#0d2b4e', '#1a5bb8'],
  },
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}

async function handleSsoLogin() {
  try {
    await startSsoLogin(route.query.redirect as string || '/dashboard')
  } catch (e: any) {
    ElMessage.error(e?.message || 'SSO 登录发起失败')
  }
}
</script>
