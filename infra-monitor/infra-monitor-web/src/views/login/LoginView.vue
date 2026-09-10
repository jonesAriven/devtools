<template>
  <LoginPage
    :config="loginConfig"
    @login="handleLogin"
    @password-reset="handlePasswordReset"
  />
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { startSsoLogin } from '@/utils/sso'

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
  // 忘记密码 / 重置密码接口前缀（auth-center 业务 API，monitor 域经 catch-all 反代到 mykng）
  authApiBase: '/kb/api/auth',
  // SSO 走本应用自己的客户端 PKCE 流（public client，回调 /infra/sso-callback）
  onSsoLogin: handleSsoLogin,
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'marschat-inframon',
    redirectUri: `${window.location.origin}/infra/sso-callback`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
    dividerText: '或',
  },
  brand: {
    tagline: '基础设施全景监控 · 主机 / 链路 / 容量',
    highlights: [
      { icon: 'Cpu', title: '主机监控', desc: 'CPU / 内存 / 磁盘实时视图' },
      { icon: 'Connection', title: '链路追踪', desc: '请求链路与依赖拓扑' },
      { icon: 'Histogram', title: '容量规划', desc: '趋势预测与扩容建议' },
    ],
    gradient: ['#0a2e3a', '#15707a'],
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

async function handleSsoLogin() {
  try {
    await startSsoLogin((route.query.redirect as string) || '/dashboard')
  } catch (err: any) {
    ElMessage.error(err?.message || 'SSO 登录发起失败')
  }
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}
</script>
