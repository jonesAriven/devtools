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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { LoginPanel } from '@marschat/auth-components'
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
  showForgotPassword: true,  // 启用集成式忘记密码
  ssoConfig: {
    issuer: 'https://auth.marschat.online',
    clientId: 'kb-ops',
    redirectUri: `${window.location.origin}/login`,
    scope: 'openid profile',
  },
  labels: {
    ssoButtonText: '统一认证登录（SSO）',
    forgotPasswordText: '忘记密码？',
  },
  onLogin: async (credentials: { username: string; password: string }) => {
    await userStore.login(credentials.username, credentials.password)
    const redirect = route.query.redirect as string
    router.push(redirect || '/dashboard')
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

<style scoped lang="scss">
.login-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}
</style>
