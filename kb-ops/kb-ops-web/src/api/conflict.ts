import request from '@/utils/request'
import type { R, PageResult, OpsConflict, PageParams } from '@/types'

export function getConflictList(params: PageParams & { keyword?: string; level?: string; status?: string }) {
  return request.get<R<PageResult<OpsConflict>>>('/ops/conflict/list', { params })
}

export function getConflict(id: number) {
  return request.get<R<OpsConflict>>(`/ops/conflict/${id}`)
}

export function resolveConflict(id: number, remark: string) {
  return request.put<R<void>>(`/ops/conflict/${id}/resolve`, { remark })
}

export function deleteConflict(id: number) {
  return request.delete<R<void>>(`/ops/conflict/${id}`)
}

export function scanConflicts() {
  return request.post<R<void>>('/ops/conflict/scan')
}
