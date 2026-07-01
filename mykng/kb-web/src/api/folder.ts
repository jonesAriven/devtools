import request from './index'
import type { R, Folder, CreateFolderRequest, UpdateFolderRequest, ResourceTreeNode } from '@/types'

/** 获取空间下的目录树 */
export function getFolderTree(spaceId: number) {
  return request.get<R<Folder[]>>('/folder/tree', { params: { spaceId } })
}

/** 获取空间下的资源树（含目录+笔记+文件+网页，用于树形展示） */
export function getResourceTree(spaceId: number) {
  return request.get<R<ResourceTreeNode[]>>(`/folder/tree-with-resources/${spaceId}`)
}

/** 获取目录详情 */
export function getFolderDetail(id: number) {
  return request.get<R<Folder>>(`/folder/${id}`)
}

/** 创建目录 */
export function createFolder(data: CreateFolderRequest) {
  return request.post<R<Folder>>('/folder', data)
}

/** 更新目录 */
export function updateFolder(id: number, data: UpdateFolderRequest) {
  return request.put<R<Folder>>(`/folder/${id}`, data)
}

/** 删除目录 */
export function deleteFolder(id: number) {
  return request.delete<R<void>>(`/folder/${id}`)
}

/** 移动目录 */
export function moveFolder(id: number, parentId: number | null, sortOrder?: number) {
  return request.put<R<void>>(`/folder/${id}/move`, { parentId, sortOrder })
}
