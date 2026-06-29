import request from './index'
import type { R, Tag, ResourceTag } from '@/types'

/** 获取所有标签 */
export function getTagList() {
  return request.get<R<Tag[]>>('/tag/list')
}

/** 创建标签 */
export function createTag(data: { name: string; color?: string }) {
  return request.post<R<Tag>>('/tag', data)
}

/** 更新标签 */
export function updateTag(id: number, data: { name?: string; color?: string }) {
  return request.put<R<Tag>>(`/tag/${id}`, data)
}

/** 删除标签 */
export function deleteTag(id: number) {
  return request.delete<R<void>>(`/tag/${id}`)
}

/** 为资源添加标签 */
export function addResourceTag(data: { tagId: number; resourceId: number; resourceType: string }) {
  return request.post<R<ResourceTag>>('/tag/resource', data)
}

/** 移除资源标签 */
export function removeResourceTag(data: { tagId: number; resourceId: number; resourceType: string }) {
  return request.delete<R<void>>('/tag/resource', { data })
}

/** 获取资源的标签列表 */
export function getResourceTags(resourceId: number, resourceType: string) {
  return request.get<R<Tag[]>>('/tag/resource', { params: { resourceId, resourceType } })
}
