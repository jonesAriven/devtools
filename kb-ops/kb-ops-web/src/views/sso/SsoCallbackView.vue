<template>
  <div class="sso-callback">
    <div class="loading-container">
      <el-icon class="loading-icon" :size="48"><Loading /></el-icon>
      <p>SSO 登录中，请稍候...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { handleSsoCallback, decodeOidcClaims } from '@/utils/sso'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/token'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

onMounted(async () => {
  try {
    const redirect = await handleSsoCallback(new URLSearchParams(window.location.search))
    
    // 解析用户信息（使用正确的 token key：kb_ops_access_token）
    const token = getToken() || ''
    if (token) {
      const claims = decodeOidcClaims(token)
      userStore.setOidcSession(claims.username || claims.sub || 'sso_user', claims)
    }
    
    ElMessage.success('SSO 登录成功')
    router.push(redirect)
  } catch (e: any) {
    ElMessage.error(e?.message || 'SSO 登录失败')
    router.push('/login')
  }
})
</script>

<style scoped lang="scss">
.sso-callback {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}

.loading-container {
  text-align: center;
  color: #fff;
  
  .loading-icon {
    animation: spin 1.5s linear infinite;
  }
  
  p {
    margin-top: 16px;
    font-size: 16px;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
