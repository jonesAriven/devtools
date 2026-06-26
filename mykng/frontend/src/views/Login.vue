<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { BookOpen, LogIn } from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)

// 默认填充 admin/admin123 方便测试
const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login(loginForm.username, loginForm.password)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } catch (e: any) {
      ElMessage.error(e?.message || '登录失败，请检查用户名密码')
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 左侧品牌插画区 -->
      <div class="brand-side">
        <div class="brand-content">
          <BookOpen :size="56" class="brand-icon" />
          <h1 class="brand-title">mykng</h1>
          <h2 class="brand-subtitle">知识库</h2>
          <p class="brand-desc">企业级知识管理平台<br />高效协作 · 安全可控 · 私有部署</p>
        </div>
        <div class="brand-footer">私有化部署 · 数据自主可控</div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="form-side">
        <div class="form-header">
          <h3 class="form-title">欢迎登录</h3>
          <p class="form-tip">请输入您的账号信息</p>
        </div>
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="rules"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              <LogIn v-if="!loading" :size="16" style="margin-right: 6px" />
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>
        <div class="form-footer">私有化部署 · 数据自主可控</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f1620 0%, #1a2332 100%);
}

.login-card {
  display: flex;
  width: 880px;
  height: 480px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

/* 左侧品牌区 */
.brand-side {
  flex: 1;
  background: linear-gradient(135deg, #1a2332 0%, #2c3e50 60%, #d4a574 220%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px;
  color: #fff;
  position: relative;
}

.brand-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-icon {
  color: #d4a574;
  margin-bottom: 24px;
}

.brand-title {
  font-size: 48px;
  font-weight: 800;
  color: #d4a574;
  margin: 0;
  letter-spacing: 2px;
}

.brand-subtitle {
  font-size: 28px;
  font-weight: 600;
  margin: 8px 0 24px;
  color: #fff;
}

.brand-desc {
  font-size: 14px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
}

.brand-footer {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

/* 右侧表单区 */
.form-side {
  width: 400px;
  background: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px;
}

.form-header {
  margin-bottom: 32px;
}

.form-title {
  font-size: 24px;
  font-weight: 700;
  color: #1a2332;
  margin: 0 0 8px;
}

.form-tip {
  font-size: 13px;
  color: #95a5a6;
  margin: 0;
}

.login-btn {
  width: 100%;
  background: #1a2332;
  border-color: #1a2332;
  font-weight: 600;
  letter-spacing: 4px;

  &:hover,
  &:focus {
    background: #d4a574;
    border-color: #d4a574;
  }
}

.form-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  color: #bdc3c7;
}
</style>
