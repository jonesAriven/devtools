<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>cosmic-studio</h2>
      <p class="sub">COSMIC 度量表生产系统</p>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" native-type="submit"
                   :loading="loading">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function doLogin() {
  if (!form.username || !form.password) { ElMessage.warning('请输入用户名和密码'); return }
  loading.value = true
  try {
    const { data } = await api.post('/auth/login', form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    router.push('/')
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1d2535, #2c3e5d); }
.login-card { width: 360px; text-align: center; padding: 10px 8px; }
.login-card h2 { margin: 8px 0 0; }
.sub { color: var(--c-text-3); margin: 6px 0 22px; }
</style>
