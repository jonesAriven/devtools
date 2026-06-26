import request from './request'
import type { Host, OpsService, OperationLog, PageResult, OpsDashboardData } from '@/types/api'

export const hostApi = {
  list: (params: { page: number; size: number }) =>
    request.get<PageResult<Host>>('/ops/host/list', { params }),
  create: (data: { name: string; ip: string; sshPort: number }) => request.post<Host>('/ops/host', data),
  delete: (id: number) => request.delete(`/ops/host/${id}`),
}

export const serviceApi = {
  list: (params: { page: number; size: number }) =>
    request.get<PageResult<OpsService>>('/ops/service/list', { params }),
}

export const opsDashboardApi = {
  summary: () => request.get<OpsDashboardData>('/ops/dashboard'),
}

export const logApi = {
  list: (params: { page: number; size: number; action?: string }) =>
    request.get<PageResult<OperationLog>>('/log/list', { params }),
}
