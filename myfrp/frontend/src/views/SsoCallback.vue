<template>
  <div class="sso-callback">
    <div class="loading-container">
      <el-icon class="loading-icon" :size="48"><Loading /></el-icon>
      <p>SSO 登录中，请稍候...</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { handleSsoCallback, decodeOidcClaims } from '../utils/sso'

const router = useRouter()
const route = useRoute()

onMounted(async () => {
  try {
    const redirect = await handleSsoCallback(new URLSearchParams(window.location.search))
    
    // 解析用户信息并存储
    const token = localStorage.getItem('token') || ''
    if (token) {
      const claims = decodeOidcClaims(token)
      localStorage.setItem('username', claims.username || claims.preferred_username || claims.sub || 'sso_user')
    }
    
    ElMessage.success('SSO 登录成功')
    router.push(redirect)
  } catch (e) {
    ElMessage.error(e?.message || 'SSO 登录失败')
    router.push('/login')
  }
})
</script>

<style scoped>
.sso-callback {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.loading-container {
  text-align: center;
  color: #fff;
}

.loading-icon {
  animation: spin 1.5s linear infinite;
}

p {
  margin-top: 16px;
  font-size: 16px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
