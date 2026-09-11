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

/** 一次性免登标记：401 回来时只允许自动重认证一次，防异常情况下无限往返 */
const REAUTH_FLAG = 'portal_reauth_once'

const loginConfig = {
  title: 'MarsChat 工具看板',
  subtitle: '统一入口 · 系统导航 · 状态监控',
  icon: 'Monitor',
  color: '#667eea',
  showSso: true,
  showForgotPassword: true,
  // 忘记密码 / 重置密码接口前缀（auth-center 业务 API，main 域新增 /portal/auth-api/ 路由）
  authApiBase: '/portal/auth-api',
  // SSO 走 portal 自己的服务端流（机密客户端，回调 /portal/auth/callback）
  onSsoLogin: handleSsoLogin,
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
 * ⚠️ 防回环：若本次是 401 之后的"过期重认证"（带 ?reauth=1），只允许自动重认证一次；
 *    再回到登录页就直接显示登录框，避免"401→登录页→免登→401"死循环。
 */
onMounted(async () => {
  try {
    const fromReauth = route.query.reauth === '1'
    if (fromReauth && sessionStorage.getItem(REAUTH_FLAG) === '1') {
      sessionStorage.removeItem(REAUTH_FLAG)
      probing.value = false
      return
    }
    if (fromReauth) sessionStorage.setItem(REAUTH_FLAG, '1')

    const target = (route.query.redirect as string) || window.location.origin
    const jumped = await bootstrapLoginPage(target)
    if (jumped) return
    sessionStorage.removeItem(REAUTH_FLAG)
    probing.value = false
  } catch {
    // 探针失败一律按"无会话"处理，绝不能因为认证中心抖动把登录页打成白屏
    probing.value = false
  }
})

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
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
