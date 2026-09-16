<template>
  <LoginPage
    v-if="!probing"
    :config="loginConfig"
    @password-reset="handlePasswordReset"
  />
  <div v-else class="sso-probing">正在检测登录状态…</div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { bootstrapLoginPage, bffAuthorizeUrl, SSO_CONFIG } from '@/utils/sso'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 静默免登探测中：先不渲染登录框，避免"闪一下登录页又跳走" */
const probing = ref(true)

const loginConfig = {
  title: 'MarsChat 工具看板',
  subtitle: '统一入口 · 系统导航 · 状态监控',
  icon: 'Monitor',
  color: '#667eea',
  showSso: true,
  showForgotPassword: true,
  // 统一登录三方式（Phase 7）：账密（onLogin → portal 本地账号）、邮箱验证码（onMailLogin →
  // portal-server BFF 换票 + 账号映射，与 SSO exchange 同一条 sys_user/auth_uid 链路）、
  // 忘记密码（邮箱码找回，经 /portal/auth-api → auth-center）
  showMailLogin: true,
  // 忘记密码 / 重置密码接口前缀（auth-center 业务 API，main 域新增 /portal/auth-api/ 路由）
  authApiBase: '/portal/auth-api',
  // SSO 走 portal 自己的服务端流（机密客户端，回调 /portal/auth/callback）
  onSsoLogin: handleSsoLogin,
  // 邮箱验证码登录：走 portal-server BFF（/portal/api/auth/mail-login），复用 SSO 的账号映射
  onMailLogin: handleMailLogin,
  ssoConfig: SSO_CONFIG,
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

/**
 * 静默免登（Phase 6）：进入登录页先探 auth-center「本浏览器是否已有 IdP 会话」。
 * - 有 → 跳 portal-server 的授权入口，IdP 会话在则瞬间回到回调页换票，用户无感
 * - 无 → 显示登录框
 *
 * ⚠️ 这里**不做** `?reauth=1`「只自动重认证一次」的短路（2026-09-16 移除，免登失效回归修复，
 *    与 cosmic-studio commit 611087d 的同类修复保持口径一致）。
 *
 *   旧实现在**免登成功**的分支里不清 sessionStorage 标记（只有失败分支 removeItem），于是：
 *     401 → /portal/login?reauth=1 → 免登成功（标记残留 '1'）→ 应用内 token 再次过期
 *     → 401 → /portal/login?reauth=1 → 命中标记 → **连 IdP 探针都不发**、直接渲染登录框，
 *     而此时 IdP 会话其实完好。真浏览器实测复现：探针返回 authenticated:true，
 *     页面却停在登录页 —— 用户看到的就是「SSO 单点免登失效」。
 *
 *   该短路只存在于本应用，与 kb-web / kb-ops / infra-monitor / activecode 的实现不一致；
 *   infra-monitor 的参考实现本就没有它。统一为「进登录页必探一次」：
 *   有 IdP 会话即免登，无会话才显示登录框（探针失败按无会话处理，fail-safe）。
 */
onMounted(async () => {
  try {
    // SLO 跨应用联动登出（Phase 6）：他处登出后本应用被联动踢回登录页，URL 带 ?slo=1。
    // 此时 IdP 会话已销毁 → 跳过免登探测并明确提示，避免用户误以为是自己掉线了。
    if (route.query.slo === '1') {
      probing.value = false
      ElMessage.warning('您已在其他应用退出登录，请重新登录')
      return
    }
    const target = (route.query.redirect as string) || window.location.origin
    const jumped = await bootstrapLoginPage(target)
    if (jumped) return
    probing.value = false
  } catch {
    // 探针失败一律按"无会话"处理，绝不能因为认证中心抖动把登录页打成白屏
    probing.value = false
  }
})

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}

/**
 * 邮箱验证码登录（统一登录三方式之一，Phase 7）。
 *
 * 走 portal-server BFF：服务端向 auth-center 换票 → 复用 SSO 的账号映射
 * （auth_uid → username 回填 → JIT 开通 sys_user）→ 发 portal 自有 JWT。
 * 成功后硬跳转（router.resolve 取带 base 的完整路径），让应用以干净状态重新引导。
 */
async function handleMailLogin({ email, code }: { email: string; code: string }) {
  const res = await fetch('/portal/api/auth/mail-login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code }),
  }).then((r) => r.json())
  if (res.code !== 200 || !res.data?.token) {
    throw new Error(res.message || '邮箱验证码登录失败')
  }
  userStore.setSession(res.data, email)
  const target = (route.query.redirect as string) || '/'
  window.location.replace(router.resolve(target).href)
  return res.data
}

function handleSsoLogin() {
  // portal 是机密客户端：授权必须走 portal-server，由它带 client_secret 换票建会话
  window.location.href = bffAuthorizeUrl((route.query.redirect as string) || window.location.origin)
}
</script>

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
