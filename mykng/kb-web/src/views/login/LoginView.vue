<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage, setToken, setRefreshToken } from '@marschat/auth-components'
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
  // 统一登录三方式（Phase 7）：账密（onLogin → auth-center /auth/login）、
  // 邮箱验证码（onMailLogin → auth-center /auth/mail-login，网关白名单已放开）、
  // 忘记密码（邮箱码找回）三者齐备。
  showMailLogin: true,
  // 忘记密码 / 重置密码 / 邮箱验证码登录的接口前缀（auth-center 业务 API，经 kb-gateway）
  authApiBase: '/kb/api/auth',
  // SSO 走本应用自己的客户端 PKCE 流（public client，回调 /kb/sso-callback）
  onSsoLogin: handleSsoLogin,
  // 邮箱验证码登录：应用负责请求换票 + 落 token + 硬跳转（见 handleMailLogin）
  onMailLogin: handleMailLogin,
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
  // SLO 跨应用联动登出（Phase 6）：他处登出后本应用被联动踢回登录页，URL 带 ?slo=1。
  // 此时 IdP 会话已被销毁 → 跳过免登探测并明确提示，避免用户误以为是自己掉线了。
  if (router.currentRoute.value.query.slo === '1') {
    probing.value = false
    ElMessage.warning('您已在其他应用退出登录，请重新登录')
    return
  }
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

/**
 * 邮箱验证码登录（统一登录三方式之一）。
 *
 * 请求 auth-center 换票 → 落 token → **硬跳转**（router.resolve 取带 base 的完整路径），
 * 让应用以「localStorage 已有 token」的干净状态重新引导，避免内存态 Pinia 陈旧。
 */
async function handleMailLogin({ email, code }: { email: string; code: string }) {
  const res = await fetch('/kb/api/auth/mail-login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code }),
  }).then((r) => r.json())
  if (res.code !== 200 || !res.data?.accessToken) {
    throw new Error(res.message || '邮箱验证码登录失败')
  }
  setToken(res.data.accessToken)
  if (res.data.refreshToken) setRefreshToken(res.data.refreshToken)
  window.location.replace(router.resolve(currentRedirect()).href)
  return res.data
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
