import request from '@/utils/request'
import type { R, PageResult, Deployment, DeploymentRequest, PageParams } from '@/types'

export function getDeploymentList(params: PageParams & { keyword?: string; hostId?: number }) {
  return request.get<R<PageResult<Deployment>>>('/ops/deployment/list', { params })
}

export function getDeployment(id: number) {
  return request.get<R<Deployment>>(`/ops/deployment/${id}`)
}

export function createDeployment(data: DeploymentRequest) {
  return request.post<R<Deployment>>('/ops/deployment', data)
}

export function updateDeployment(id: number, data: DeploymentRequest) {
  return request.put<R<Deployment>>(`/ops/deployment/${id}`, data)
}

export function deleteDeployment(id: number) {
  return request.delete<R<void>>(`/ops/deployment/${id}`)
}
