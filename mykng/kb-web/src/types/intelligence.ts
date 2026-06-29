// ============================================================
// kb-intelligence 模块类型定义
// 对应后端 12 个接口，字段名严格按实际返回格式
// ============================================================

import type { R, PageParams } from './index'

/** 文档类型枚举 */
export type KnDocType = 'TABLE' | 'PLAN' | 'TIMELINE' | 'GRAPH' | 'RULE' | 'GENERAL'

/** 文档元数据/索引项（meta 接口返回单个，docs 接口返回数组） */
export interface KnDoc {
  id: number
  title: string
  sourceId: string
  filePath: string
  docType: KnDocType
  category: string
  tags: string
  summary: string
  entityCount: number
  commandCount: number
  sectionCount: number
  wordCount: number
  createdAt: string
  updatedAt: string
}

/** docs 分页响应（字段名是 records，不是 list） */
export interface KnDocPage {
  records: KnDoc[]
  total: number
  current?: number
  size?: number
}

/** 统计信息 */
export interface KnStats {
  docCount: number
  hostCount: number
  serviceCount: number
  portCount: number
  credentialCount: number
  commandCount: number
  domainCount: number
  timelineCount: number
  byType: { docType: KnDocType; count: number }[]
}

/** 主机 */
export interface KnHost {
  id: number
  name: string | null
  ip: string | null
  tailscaleIp: string | null
  sshPort: number
  username: string | null
  role: string | null
  osType: string | null
  status: string
}

/** 服务 */
export interface KnService {
  id: number
  name: string
  hostId?: number
  hostName?: string
  port?: number
  type?: string
  status?: string
  [k: string]: unknown
}

/** 命令 */
export interface KnCommand {
  id: number
  command: string
  description: string | null
  category: string
  riskLevel: string
  osType: string | null
}

/** 时间线事件 */
export interface KnTimeline {
  id: number
  docId: number
  eventTime: string
  eventType: string
  title: string
  description: string | null
  severity: string
  status: string
  solution: string | null
}

/** 凭据 */
export interface KnCredential {
  id: number
  hostId: number
  credType: string
  username: string
  passwordHint: string
}

/** 端口 */
export interface KnPort {
  id: number
  [k: string]: unknown
}

/** 单文档实体聚合（doc/{id}/entities 接口） */
export interface KnDocEntities {
  docId: number
  title: string
  hosts: KnHost[]
  services: KnService[] | null
  ports: KnPort[]
  credentials: KnCredential[]
}

/** 单文档内容（doc/{id}/content 接口，字段是 plainText，不是 content） */
export interface KnDocContent {
  docId: number
  title: string
  plainText: string
}

/** 搜索命中项 */
export interface KnSearchHit {
  docId: number
  docTitle: string
  docType: KnDocType
  category: string
  highlight: string
  score: number
  matchedSections: string[] | null
}

/** 导入结果 */
export interface KnImportResult {
  docCount: number
  hostCount: number
  commandCount: number
  timelineCount: number
  [k: string]: unknown
}

/** 导入/服务状态 */
export interface KnImportStatus {
  service: string
  status: string
  [k: string]: unknown
}

/** 导入请求 */
export interface KnImportRequest {
  path: string
  incremental?: boolean
}

/** 搜索请求 */
export interface KnSearchRequest {
  keyword: string
  docType?: string
  limit?: number
}

/** 文档列表查询参数 */
export type KnDocQuery = PageParams & { keyword?: string; docType?: string }

// 复用 R 类型，方便 API 层
export type { R, PageParams }
