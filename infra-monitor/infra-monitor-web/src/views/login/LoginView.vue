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

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoginPage } from '@marschat/auth-components'
import { useUserStore } from '@/stores/user'
import { startSsoLogin, bootstrapLoginPage, SSO_CONFIG } from '@/utils/sso'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 静默免登探测中：先不渲染登录框，避免"闪一下登录页又跳走" */
const probing = ref(true)

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
  // 引用统一 SSO 配置（client_id=marschat-inframon，回调 /infra/sso-callback）
  ssoConfig: SSO_CONFIG,
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
    await startSsoLogin(currentRedirect())
  } catch (err: any) {
    ElMessage.error(err?.message || 'SSO 登录发起失败')
  }
}

function handlePasswordReset() {
  ElMessage.success('密码重置成功，请使用新密码登录')
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
