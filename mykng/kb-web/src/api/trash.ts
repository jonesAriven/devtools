import request from './index'
import type { R, TrashItem, PageResult, PageParams } from '@/types'

/** 获取回收站列表 */
export function getTrashList(params: PageParams & { type?: string }) {
  return request.get<R<PageResult<TrashItem>>>('/trash/list', { params })
}

/** 恢复资源 */
export function restoreResource(id: number, type: string) {
  return request.post<R<void>>(`/trash/restore/${type}/${id}`)
}

/** 永久删除资源 */
export function permanentDelete(id: number, type: string) {
  return request.delete<R<void>>(`/trash/${type}/${id}`)
}

/** 清空回收站 */
export function emptyTrash() {
  return request.delete<R<void>>('/trash/empty')
}
