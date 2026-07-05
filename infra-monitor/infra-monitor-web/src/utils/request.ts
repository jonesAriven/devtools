import axios from 'axios'
import { getToken, clearTokens } from '@/utils/token'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { API_BASE_URL } from '@/config'

const WHITE_LIST_PATHS = ['/auth/login']

function isWhiteList(url: string): boolean {
  return WHITE_LIST_PATHS.some(p => url.includes(p))
}

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const data = response.data
    if (data && data.code !== undefined && data.code !== 0 && data.code !== 200) {
      const message = data.message || '请求失败'
      if (!isWhiteList(response.config.url || '')) {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }
    return response
  },
  (error) => {
    const url = error.config?.url || ''

    if (error.response?.status === 401) {
      clearTokens()
      if (!isWhiteList(url)) {
        router.push('/login')
      }
      return Promise.reject(error)
    }

    const message = error.response?.data?.message || error.message || '网络错误'
    if (!isWhiteList(url)) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
