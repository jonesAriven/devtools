import request from './index'
import type { R, Version } from '@/types'

/** 获取资源的版本列表 */
export function getVersionList(resourceId: number, resourceType: string) {
  return request.get<R<Version[]>>('/version/list', { params: { resourceId, resourceType } })
}

/** 获取特定版本详情 */
export function getVersionDetail(id: number) {
  return request.get<R<Version>>(`/version/${id}`)
}

/** 回滚到指定版本 */
export function rollbackVersion(id: number) {
  return request.post<R<void>>(`/version/${id}/rollback`)
}
