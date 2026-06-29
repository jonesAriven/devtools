import request from './index'
import type { R } from '@/types'
import type {
  KnDoc, KnDocPage, KnStats, KnHost, KnService, KnCommand, KnTimeline,
  KnDocEntities, KnDocContent, KnSearchHit, KnImportResult, KnImportStatus,
  KnImportRequest, KnSearchRequest, KnDocQuery,
} from '@/types/intelligence'

// ============================================================
// 导入
// ============================================================

/** 按路径全量/增量导入 */
export function importByPath(data: KnImportRequest) {
  return request.post<R<KnImportResult>>('/intelligence/import/path', data, {
    timeout: 300000, // 5 分钟，导入耗时长
  })
}

/** 获取导入状态（实际是服务运行状态） */
export function getImportStatus() {
  return request.get<R<KnImportStatus>>('/intelligence/import/status')
}

// ============================================================
// 机器可读 - 统计与索引
// ============================================================

/** 统计信息 */
export function getStats() {
  return request.get<R<KnStats>>('/intelligence/machine/stats')
}

/** 文档索引列表（分页字段是 records） */
export function getDocList(params: KnDocQuery) {
  return request.get<R<KnDocPage>>('/intelligence/machine/docs', { params })
}

/** 文档元数据 */
export function getDocMeta(id: number) {
  return request.get<R<KnDoc>>(`/intelligence/machine/docs/${id}/meta`)
}

/** 文档实体（含主机/服务/端口/凭据） */
export function getDocEntities(id: number) {
  return request.get<R<KnDocEntities>>(`/intelligence/machine/docs/${id}/entities`)
}

/** 文档内容（markdown 原文，字段是 plainText） */
export function getDocContent(id: number) {
  return request.get<R<KnDocContent>>(`/intelligence/machine/docs/${id}/content`)
}

// ============================================================
// 跨文档实体查询
// ============================================================

/** 跨文档查主机 */
export function getHostList() {
  return request.get<R<KnHost[]>>('/intelligence/machine/entities/hosts')
}

/** 跨文档查服务 */
export function getServiceList() {
  return request.get<R<KnService[]>>('/intelligence/machine/entities/services')
}

/** 跨文档查命令 */
export function getCommandList() {
  return request.get<R<KnCommand[]>>('/intelligence/machine/entities/commands')
}

/** 跨文档查时间线 */
export function getTimelineList() {
  return request.get<R<KnTimeline[]>>('/intelligence/machine/entities/timelines')
}

/** 关键词搜索（返回数组，命中项含 highlight） */
export function searchDocs(data: KnSearchRequest) {
  return request.post<R<KnSearchHit[]>>('/intelligence/machine/search', data)
}
