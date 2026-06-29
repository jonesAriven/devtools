import request from './index'
import type { R, Space, CreateSpaceRequest, UpdateSpaceRequest } from '@/types'

/** 获取空间列表 */
export function getSpaceList() {
  return request.get<R<Space[]>>('/space/list')
}

/** 获取空间详情 */
export function getSpaceDetail(id: number) {
  return request.get<R<Space>>(`/space/${id}`)
}

/** 创建空间 */
export function createSpace(data: CreateSpaceRequest) {
  return request.post<R<Space>>('/space', data)
}

/** 更新空间 */
export function updateSpace(id: number, data: UpdateSpaceRequest) {
  return request.put<R<Space>>(`/space/${id}`, data)
}

/** 删除空间 */
export function deleteSpace(id: number) {
  return request.delete<R<void>>(`/space/${id}`)
}
