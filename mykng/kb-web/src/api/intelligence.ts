import request from './index'
import type { R } from '@/types'
import type {
  IntelDoc,
  IntelDocContent,
  IntelDocEntities,
  IntelHost,
  IntelService,
  IntelPort,
  IntelCredential,
  IntelDomain,
  IntelCommand,
  IntelTimeline,
  IntelStats,
  IntelSearchResult,
  IntelPageResult,
} from '@/types'

const BASE = '/intelligence/machine'

/** 文档列表 */
export function getDocList(params: {
  docType?: string
  category?: string
  tag?: string
  page?: number
  size?: number
}) {
  return request.get<R<IntelPageResult<IntelDoc>>>(`${BASE}/docs`, { params })
}

/** 文档元数据 */
export function getDocMeta(docId: number) {
  return request.get<R<IntelDoc>>(`${BASE}/docs/${docId}/meta`)
}

/** 文档关联实体 */
export function getDocEntities(docId: number) {
  return request.get<R<IntelDocEntities>>(`${BASE}/docs/${docId}/entities`)
}

/** 文档内容 */
export function getDocContent(docId: number) {
  return request.get<R<IntelDocContent>>(`${BASE}/docs/${docId}/content`)
}

/** 主机列表 */
export function getHostList(params?: { ip?: string; name?: string; role?: string }) {
  return request.get<R<IntelHost[]>>(`${BASE}/entities/hosts`, { params })
}

/** 服务列表 */
export function getServiceList(params?: { hostId?: number; name?: string }) {
  return request.get<R<IntelService[]>>(`${BASE}/entities/services`, { params })
}

/** 命令列表 */
export function getCommandList(params?: { docId?: number; category?: string; riskLevel?: string }) {
  return request.get<R<IntelCommand[]>>(`${BASE}/entities/commands`, { params })
}

/** 时间线列表 */
export function getTimelineList(params?: { docId?: number; severity?: string; eventType?: string }) {
  return request.get<R<IntelTimeline[]>>(`${BASE}/entities/timelines`, { params })
}

/** 搜索 */
export function searchKnowledge(data: {
  query: string
  docTypes?: string[]
  tags?: string[]
  page?: number
  size?: number
}) {
  return request.post<R<IntelSearchResult[]>>(`${BASE}/search`, data)
}

/** 端口列表 */
export function getPortList(params?: { hostId?: number; exposed?: number }) {
  return request.get<R<IntelPort[]>>(`${BASE}/entities/ports`, { params })
}

/** 凭据列表 */
export function getCredentialList(params?: { hostId?: number; credType?: string }) {
  return request.get<R<IntelCredential[]>>(`${BASE}/entities/credentials`, { params })
}

/** 域名列表 */
export function getDomainList(params?: { status?: string }) {
  return request.get<R<IntelDomain[]>>(`${BASE}/entities/domains`, { params })
}

/** 统计 */
export function getStats() {
  return request.get<R<IntelStats>>(`${BASE}/stats`)
}
