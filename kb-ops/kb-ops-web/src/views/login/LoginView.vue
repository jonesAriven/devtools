<template>
  <LoginPage
    v-if="!probing"
    :config="loginConfig"
    @password-reset="handlePasswordReset"
  />
  <div v-else class="sso-probing">
    <span>正在检测登录状态…</span>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { useRoute, useRouter } from 'vue-router'
import { startSsoLogin, bootstrapLoginPage, SSO_CONFIG } from '@/utils/sso'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

/** 静默免登探测中：先不渲染登录框，避免"闪一下登录页又跳走" */
const probing = ref(true)

const loginConfig = {
  title: '运维管理平台',
  subtitle: '请登录您的账号',
  icon: 'Setting',
  color: '#409eff',
  showSso: true,
  // kb-ops 后端无本地登录端点（/kb-ops/login 必 403）——纯 SSO 应用隐藏死表单
  showLocalLogin: false,
  showForgotPassword: true,
  // 忘记密码 / 重置密码接口前缀（auth-center 业务 API，本域 nginx /ops/auth-api/ 已直连网关）
  authApiBase: '/ops/auth-api',
  // SSO 走本应用自己的客户端 PKCE 流（public client，回调 /ops/sso-callback）
  onSsoLogin: handleSsoLogin,
  // 引用统一 SSO 配置（client_id=marschat-kbops，回调 /ops/sso-callback）
  ssoConfig: SSO_CONFIG,
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

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
}

async function handleSsoLogin() {
  try {
    await startSsoLogin(currentRedirect())
  } catch (e: any) {
    ElMessage.error(e?.message || 'SSO 登录发起失败')
  }
}
</script>

<style scoped lang="scss">
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
