import request from '@/utils/request'
import type { R, PageResult, OpsService, ServiceRequest, PageParams } from '@/types'

export function getServiceList(params: PageParams & { keyword?: string; hostId?: number }) {
  return request.get<R<PageResult<OpsService>>>('/ops/service/list', { params })
}

export function getService(id: number) {
  return request.get<R<OpsService>>(`/ops/service/${id}`)
}

export function createService(data: ServiceRequest) {
  return request.post<R<OpsService>>('/ops/service', data)
}

export function updateService(id: number, data: ServiceRequest) {
  return request.put<R<OpsService>>(`/ops/service/${id}`, data)
}

export function deleteService(id: number) {
  return request.delete<R<void>>(`/ops/service/${id}`)
}
