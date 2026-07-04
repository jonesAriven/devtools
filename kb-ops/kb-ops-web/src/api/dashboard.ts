import request from '@/utils/request'
import type { R, PageResult, DashboardVO } from '@/types'

export function getDashboard() {
  return request.get<R<DashboardVO>>('/ops/dashboard')
}
