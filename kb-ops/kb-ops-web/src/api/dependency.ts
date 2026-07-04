import request from '@/utils/request'
import type { R, PageResult, Dependency, DependencyRequest, PageParams } from '@/types'

export function getDependencyList(params: PageParams & { keyword?: string; hostId?: number }) {
  return request.get<R<PageResult<Dependency>>>('/ops/dependency/list', { params })
}

export function getDependency(id: number) {
  return request.get<R<Dependency>>(`/ops/dependency/${id}`)
}

export function createDependency(data: DependencyRequest) {
  return request.post<R<Dependency>>('/ops/dependency', data)
}

export function updateDependency(id: number, data: DependencyRequest) {
  return request.put<R<Dependency>>(`/ops/dependency/${id}`, data)
}

export function deleteDependency(id: number) {
  return request.delete<R<void>>(`/ops/dependency/${id}`)
}
