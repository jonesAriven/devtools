<template>
  <div class="sso-callback-page">
    <div class="sso-callback-card">
      <template v-if="error">
        <el-icon :size="44" color="#f56c6c"><CircleCloseFilled /></el-icon>
        <h3>统一认证登录失败</h3>
        <p class="err">{{ error }}</p>
        <el-button type="primary" @click="backToLogin">返回登录页</el-button>
      </template>
      <template v-else>
        <el-icon :size="44" color="#409eff" class="spin"><Loading /></el-icon>
        <h3>统一认证登录中…</h3>
        <p class="hint">正在换取访问令牌并进入运维管理平台</p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCloseFilled, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { handleSsoCallback, decodeOidcClaims } from '@/utils/sso'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const error = ref('')

onMounted(async () => {
  try {
    const query = new URLSearchParams(window.location.search)
    const target = await handleSsoCallback(query)
    // 优先取授权服务器 token 里的业务 claims 构建会话
    const claims = decodeOidcClaims(getToken() || '')
    userStore.isLoggedIn = true
    userStore.username = claims.username || 'unknown'
    userStore.profile = {
      id: Number(claims.uid || 0),
      username: claims.username || 'unknown',
      nickname: claims.username || '用户',
      email: '',
      avatar: '',
      role: claims.role || 'user',
      createdAt: '',
      updatedAt: '',
    }
    ElMessage.success('统一认证登录成功')
    router.replace(target)
  } catch (e: any) {
    error.value = e?.message || '登录过程出现未知错误'
  }
})

function backToLogin() {
  router.replace('/login')
}
</script>

<style scoped lang="scss">
.sso-callback-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}

.sso-callback-card {
  width: 90%;
  max-width: 420px;
  padding: 40px 28px;
  text-align: center;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);

  h3 {
    margin: 16px 0 8px;
    color: #303133;
  }

  .err {
    color: #f56c6c;
    margin-bottom: 20px;
    word-break: break-all;
  }

  .hint {
    color: #909399;
    margin-bottom: 12px;
  }

  .spin {
    animation: rotate 1.2s linear infinite;
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
