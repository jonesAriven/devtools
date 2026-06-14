import request from './index'
import type { R, Share, CreateShareRequest } from '@/types'

/** 创建分享 */
export function createShare(data: CreateShareRequest) {
  return request.post<R<Share>>('/share', data)
}

/** 获取分享详情（通过分享码） */
export function getShareByCode(code: string) {
  return request.get<R<Share>>(`/share/${code}`)
}

/** 验证提取码 */
export function verifyExtractCode(code: string, extractCode: string) {
  return request.post<R<boolean>>('/share/verify', { code, extractCode })
}

/** 获取分享内容 */
export function getShareContent(code: string, extractCode?: string) {
  return request.get<R<any>>('/share/content', { params: { code, extractCode } })
}

/** 获取我创建的分享列表 */
export function getMyShares() {
  return request.get<R<Share[]>>('/share/my')
}

/** 取消分享 */
export function cancelShare(id: number) {
  return request.delete<R<void>>(`/share/${id}`)
}
