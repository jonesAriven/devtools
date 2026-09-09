<template>
  <div class="sso-callback-page">
    <div class="sso-callback-card">
      <div v-if="loading" class="spinner"></div>
      <h2>{{ title }}</h2>
      <p>{{ subtitle }}</p>
      <div v-if="error" class="error-box">{{ error }}</div>
      <button v-if="error" class="btn-back" @click="goLogin">返回登录页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api, { humanize } from '../api'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const title = ref('正在完成统一认证登录…')
const subtitle = ref('请稍候，正在与认证中心交换凭据')
const error = ref('')

function goLogin() {
  router.push('/login')
}

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string

  if (!code) {
    loading.value = false
    title.value = '统一认证登录失败'
    subtitle.value = '回调参数缺少授权码'
    error.value = '未收到授权码，请重新发起登录'
    return
  }

  try {
    // 后端用 code+verifier 换 token，然后建立本地会话
    const { data } = await api.post('/auth/sso/callback', {
      code,
      state,
      redirect_uri: `${window.location.origin}/sso-callback`,
    })

    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))

    title.value = '登录成功'
    subtitle.value = '正在进入系统…'

    setTimeout(() => {
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    }, 500)
  } catch (err: any) {
    loading.value = false
    title.value = '统一认证登录失败'
    subtitle.value = '无法通过统一认证登录'
    const detail = err?.response?.data
    error.value = humanize(detail) || (err?.message || '未知错误')
  }
})
</script>

<style scoped lang="scss">
.sso-callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d2535 0%, #313d6b 100%);
}

.sso-callback-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.35);
  width: 420px;
  padding: 40px 32px;
  text-align: center;
}

.spinner {
  width: 42px;
  height: 42px;
  margin: 0 auto 18px;
  border: 4px solid #eef2ff;
  border-top-color: #1d2535;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

h2 {
  font-size: 17px;
  color: #111827;
  font-weight: 700;
}

p {
  font-size: 13px;
  color: #9ca3af;
  margin-top: 8px;
}

.error-box {
  margin-top: 14px;
  padding: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 9px;
  color: #dc2626;
  font-size: 13px;
  text-align: left;
  word-break: break-all;
}

.btn-back {
  margin-top: 16px;
  padding: 10px 22px;
  background: #1d2535;
  color: #fff;
  border: none;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
</style>
