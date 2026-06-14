import request from './index'
import type { R, WebPage, CreateWebPageRequest, PageResult, PageParams } from '@/types'

/** 收藏网页 */
export function createWebPage(data: CreateWebPageRequest) {
  return request.post<R<WebPage>>('/web', data)
}

/** 获取网页收藏详情 */
export function getWebPageDetail(id: number) {
  return request.get<R<WebPage>>(`/web/${id}`)
}

/** 删除网页收藏 */
export function deleteWebPage(id: number) {
  return request.delete<R<void>>(`/web/${id}`)
}

/** 获取目录下的网页收藏列表 */
export function getWebPageList(params: PageParams & { folderId: number }) {
  return request.get<R<PageResult<WebPage>>>('/web/list', { params })
}

/** 切换网页收藏星标 */
export function toggleWebPageStar(id: number) {
  return request.put<R<void>>(`/web/${id}/star`)
}

/** 移动网页收藏 */
export function moveWebPage(id: number, folderId: number) {
  return request.put<R<void>>(`/web/${id}/move`, { folderId })
}

/** 重新抓取网页内容 */
export function refetchWebPage(id: number) {
  return request.post<R<WebPage>>(`/web/${id}/refetch`)
}
