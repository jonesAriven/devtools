<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useAuth } from '@/composables/useAuth'
import { bootstrapLoginPage, startSsoLogin, SSO_CONFIG } from '@/utils/sso'

const router = useRouter()
const { loading, login } = useAuth()

/** 静默免登探测中：先不渲染登录框，避免"闪一下登录页又跳走" */
const probing = ref(true)

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
  ssoConfig: SSO_CONFIG,
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

/** 当前要去的站内目标（登录页带 ?redirect=） */
function currentRedirect(): string {
  return (router.currentRoute.value.query.redirect as string) || '/dashboard'
}

/**
 * 静默免登（Phase 6）：进入登录页先问 auth-center「本浏览器是否已有 IdP 会话」。
 * - 有 → 直接跳授权，IdP 会话在则瞬间 302 回带 code，用户无感进入（不返回）
 * - 无 → 返回 false，正常显示登录框
 *
 * ⚠️ 不能用 `prompt=none`：SAS 3.2.5 不支持，会直接渲染登录页而非返回错误。
 */
onMounted(async () => {
  try {
    const jumped = await bootstrapLoginPage(currentRedirect())
    if (!jumped) probing.value = false
  } catch {
    // 探针失败一律按"无会话"处理，绝不能因为认证中心抖动把登录页打成白屏
    probing.value = false
  }
})

async function handleLogin(credentials: { username: string; password: string }) {
  try {
    await login(credentials.username, credentials.password)
    ElMessage.success('登录成功')
    router.push(currentRedirect())
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '登录失败')
  }
}

async function handleSsoLogin() {
  try {
    await startSsoLogin(currentRedirect())
  } catch (err: any) {
    ElMessage.error(err?.message || 'SSO 登录发起失败')
  }
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}
</script>

<template>
  <LoginPage
    v-if="!probing"
    :config="loginConfig"
    @login="handleLogin"
    @password-reset="handlePasswordReset"
  />
  <div v-else class="sso-probing">
    <span>正在检测登录状态…</span>
  </div>
</template>

<style scoped>
.sso-probing {
  width: 100%;
  height: 100%;
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
}
</style>
