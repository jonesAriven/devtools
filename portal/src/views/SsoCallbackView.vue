<template>
  <div class="sso-callback">
    <el-card class="callback-card">
      <el-result
        :icon="status === 'error' ? 'error' : 'info'"
        :title="title"
        :sub-title="detail"
      >
        <template #extra>
          <el-button v-if="status === 'error'" type="primary" @click="backToLogin">返回登录</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const status = ref<'loading' | 'error'>('loading')
const title = ref('统一认证登录中...')
const detail = ref('正在换取会话，请稍候')

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string
  const error = route.query.error as string
  if (error || !code || !state) {
    status.value = 'error'
    title.value = '统一认证登录失败'
    detail.value = error || '授权响应缺少必要参数'
    return
  }
  try {
    await userStore.ssoExchange(code, state)
    router.replace('/')
  } catch (e: any) {
    status.value = 'error'
    title.value = '统一认证登录失败'
    detail.value = e.message || '换取会话失败'
  }
})

function backToLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.sso-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.callback-card {
  width: 420px;
}
</style>
