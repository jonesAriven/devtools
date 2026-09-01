import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

api.interceptors.response.use(
  r => r,
  err => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.pathname !== '/login') location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// 把后端任意形态的错误体归一为可读字符串：string / array / object（含 detail/message 嵌套）
export function humanize(detail) {
  if (detail === null || detail === undefined) return ''
  if (typeof detail === 'string') return detail
  if (Array.isArray(detail)) return detail.map(humanize).join('；')
  if (typeof detail === 'object') {
    if (detail.detail) return humanize(detail.detail)
    if (detail.message) return humanize(detail.message)
    if (detail.msg) return humanize(detail.msg)
    const parts = []
    for (const [k, v] of Object.entries(detail)) parts.push(`${k}：${humanize(v)}`)
    return parts.join('；')
  }
  return String(detail)
}

// 带 Authorization 的授权下载：绕开 window.open 不带 token 导致 401 的问题（P0-1）
// path 为含 /api 的完整路径，filename 为下载文件名
export async function downloadBlob(path, filename) {
  const token = localStorage.getItem('token')
  const resp = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!resp.ok) {
    let msg = `下载失败（HTTP ${resp.status}）`
    try {
      const j = await resp.json()
      msg = humanize(j) || msg
    } catch { /* 非 JSON 错误体，用默认文案 */ }
    throw new Error(msg)
  }
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || ''
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export const user = () => JSON.parse(localStorage.getItem('user') || 'null')
export const role = () => (user() || {}).role || 'viewer'
export const isAdmin = () => role() === 'admin'

export const batchImport = (payload) => api.post('/studio/vocab/batch-import', payload)
export const batchDelete = (ids) => api.post('/studio/vocab/batch-delete', { ids })
export const batchDeleteByFilter = (payload) => api.post('/studio/vocab/batch-delete-by-filter', payload)
export const batchConfirmByFilter = (payload) => api.post('/studio/vocab/batch-confirm-by-filter', payload)
export const batchRejectByFilter = (payload) => api.post('/studio/vocab/batch-reject-by-filter', payload)

export default api
