import request from './index'
import type { R, Doc, CreateDocRequest, UpdateDocRequest, PageResult, PageParams } from '@/types'

/** 创建笔记 */
export function createDoc(data: CreateDocRequest) {
  return request.post<R<Doc>>('/doc', data)
}

/** 获取笔记详情 */
export function getDocDetail(id: number) {
  return request.get<R<Doc>>(`/doc/${id}`)
}

/** 更新笔记 */
export function updateDoc(id: number, data: UpdateDocRequest) {
  return request.put<R<Doc>>(`/doc/${id}`, data)
}

/** 删除笔记 */
export function deleteDoc(id: number) {
  return request.delete<R<void>>(`/doc/${id}`)
}

/** 获取目录下的笔记列表 */
export function getDocList(params: { page?: number; size?: number; folderId?: number }) {
  return request.get<R<PageResult<Doc>>>('/doc/list', { params })
}

/** 切换笔记星标 */
export function toggleDocStar(id: number) {
  return request.put<R<void>>(`/doc/${id}/star`)
}

/** 移动笔记 */
export function moveDoc(id: number, folderId: number) {
  return request.put<R<void>>(`/doc/${id}/move`, { folderId })
}
