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

export const user = () => JSON.parse(localStorage.getItem('user') || 'null')
export const role = () => (user() || {}).role || 'viewer'
export const isAdmin = () => role() === 'admin'

export const batchImport = (payload) => api.post('/studio/vocab/batch-import', payload)
export const batchDelete = (ids) => api.post('/studio/vocab/batch-delete', { ids })
export const batchDeleteByFilter = (payload) => api.post('/studio/vocab/batch-delete-by-filter', payload)

export default api
