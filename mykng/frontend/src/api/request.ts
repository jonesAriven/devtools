import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { storage } from '@/utils/storage'
import router from '@/router'
import type { Result } from '@/types/api'

const instance = axios.create({
  baseURL: '/kb/api',
  timeout: 20000,
  // 用 arraybuffer 接收原始字节，避免浏览器对 application/json(无 charset)
  // 按 ISO-8859-1 解析导致中文乱码。在 transformResponse 中强制 UTF-8 解码。
  responseType: 'arraybuffer',
  transformResponse: [
    (data) => {
      if (data instanceof ArrayBuffer) {
        const text = new TextDecoder('utf-8').decode(new Uint8Array(data))
        try {
          return JSON.parse(text)
        } catch {
          return text
        }
      }
      return data
    },
  ],
})

// 请求拦截器：注入 Authorization
instance.interceptors.request.use(
  (config) => {
    const token = storage.get<string>('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

let isRefreshing = false
let pendingQueue: Array<(token: string | null) => void> = []

// 响应拦截器：提取 data，处理 401 自动刷新
instance.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const result = response.data
    if (result.code !== 200) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message || 'Error'))
    }
    return result.data as any
  },
  async (error) => {
    const status = error.response?.status
    const originalConfig = error.config

    // arraybuffer 错误响应需先解码为对象，方便后续读取 message 字段
    if (error.response?.data instanceof ArrayBuffer) {
      try {
        const text = new TextDecoder('utf-8').decode(new Uint8Array(error.response.data))
        error.response.data = JSON.parse(text)
      } catch {
        error.response.data = {}
      }
    }

    if (status === 401 && !originalConfig._retry) {
      originalConfig._retry = true
      const refreshToken = storage.get<string>('refreshToken')

      if (refreshToken && !isRefreshing) {
        isRefreshing = true
        try {
          const res = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
            '/kb/api/auth/refresh',
            { refreshToken },
          )
          if (res.data.code === 200) {
            const { accessToken, refreshToken: newRefresh } = res.data.data
            storage.set('accessToken', accessToken)
            storage.set('refreshToken', newRefresh)
            pendingQueue.forEach((cb) => cb(accessToken))
            pendingQueue = []
            originalConfig.headers.Authorization = `Bearer ${accessToken}`
            return instance(originalConfig)
          }
        } catch {
          // refresh failed
        } finally {
          isRefreshing = false
        }
      }

      storage.clear()
      router.push('/login')
      ElMessage.warning('登录已过期，请重新登录')
      return Promise.reject(error)
    }

    const msg = error.response?.data?.message || error.message || '网络异常'
    if (status !== 401) {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

// 由于响应拦截器已解包返回 result.data，这里声明方法返回值直接为业务数据 T
interface HttpRequest {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
}

const request = instance as unknown as HttpRequest

export default request
export type { AxiosRequestConfig }
