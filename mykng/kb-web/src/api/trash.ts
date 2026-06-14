import request from './index'
import type { R, TrashItem, PageResult, PageParams } from '@/types'

/** 获取回收站列表 */
export function getTrashList(params: PageParams & { spaceId?: number }) {
  return request.get<R<PageResult<TrashItem>>>('/trash/list', { params })
}

/** 恢复资源 */
export function restoreResource(id: number, type: string) {
  return request.put<R<void>>(`/trash/${id}/restore`, null, { params: { type } })
}

/** 永久删除资源 */
export function permanentDelete(id: number, type: string) {
  return request.delete<R<void>>(`/trash/${id}`, { params: { type } })
}

/** 清空回收站 */
export function emptyTrash(spaceId?: number) {
  return request.delete<R<void>>('/trash/empty', { params: { spaceId } })
}
