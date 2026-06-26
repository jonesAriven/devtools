// 统一响应格式
export interface Result<T = any> {
  code: number
  message: string
  data: T
  traceId?: string
}

// 分页结果
export interface PageResult<T = any> {
  list: T[]
  total: number
  page: number
  size: number
}

// 用户
export interface User {
  id: number
  username: string
  nickname: string
  avatar: string
  email: string
  phone: string
  status: number
}

// 登录响应
export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

// 知识空间
export interface Space {
  id: number
  name: string
  type: string
  description: string
  docCount: number
  createdAt: string
  updatedAt: string
}

// 目录
export interface Folder {
  id: number
  spaceId: number
  parentId: number
  name: string
  sort: number
  children?: Folder[]
  docCount?: number
}

// 文档
export interface Doc {
  id: number
  spaceId: number
  folderId: number
  title: string
  content: string
  starred: number
  status: number
  createdAt: string
  updatedAt: string
}

// 标签
export interface Tag {
  id: number
  name: string
  color: string
  count?: number
}

// 分享
export interface Share {
  id: number
  code: string
  extractCode: string
  resourceType: string
  resourceId: number
  resourceTitle?: string
  expireAt: string | null
  viewCount: number
  status: number
  createdAt: string
}

// 版本
export interface Version {
  id: number
  resourceType: string
  resourceId: number
  versionNumber: number
  title: string
  content: string
  operatorId: number
  createdAt: string
}

// 回收站项
export interface TrashItem {
  id: number
  resourceType: string
  resourceId: number
  title: string
  deletedAt: string
}

// 存储桶
export interface Bucket {
  id: number
  name: string
  type: string
  capacity: number
  used: number
  status: number
}

// 文件
export interface KbFile {
  id: number
  bucketId: number
  name: string
  size: number
  type: string
  status: number
  createdAt: string
}

// 运维主机
export interface Host {
  id: number
  name: string
  ip: string
  sshPort: number
  status: number
  cpuUsage?: number
  memUsage?: number
  diskUsage?: number
  createdAt: string
}

// 运维服务
export interface OpsService {
  id: number
  name: string
  hostId: number
  hostName?: string
  port: number
  status: number
  version: string
  createdAt: string
}

// 操作日志
export interface OperationLog {
  id: number
  userId: number
  username: string
  action: string
  resourceType: string
  resourceId: number
  detail: string
  ip: string
  createdAt: string
}

// 看板统计
export interface DashboardStats {
  totalDocs: number
  totalSpaces: number
  totalShares: number
  totalTags: number
  todayViews: number
  activeShares: number
}

// 运维看板数据
export interface OpsDashboardData {
  hostStats: { running: number; stopped: number; maintenance: number; total: number }
  serviceStats: { running: number; stopped: number; abnormal: number; total: number }
  serviceTypeDistribution: Record<string, number>
  recentDeployCount: number
  unresolvedConflictCount: number
  deployTrend: { date: string; count: number }[]
  recentDeploys: any[]
  recentConflicts: any[]
}
