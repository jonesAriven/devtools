/** 后端统一响应格式 */
export interface R<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** 分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 分页请求参数 */
export interface PageParams {
  page: number
  pageSize: number
}

/** 用户 */
export interface User {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  role: string
  createdAt: string
  updatedAt: string
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: User
}

/** 刷新Token请求 */
export interface RefreshTokenRequest {
  refreshToken: string
}

/** 刷新Token响应 */
export interface RefreshTokenResponse {
  accessToken: string
  refreshToken: string
}

/** 空间 */
export interface Space {
  id: number
  name: string
  description: string
  icon: string
  ownerId: number
  createdAt: string
  updatedAt: string
}

/** 创建空间请求 */
export interface CreateSpaceRequest {
  name: string
  description?: string
  icon?: string
}

/** 更新空间请求 */
export interface UpdateSpaceRequest {
  name?: string
  description?: string
  icon?: string
}

/** 目录 */
export interface Folder {
  id: number
  name: string
  parentId: number | null
  spaceId: number
  sortOrder: number
  createdAt: string
  updatedAt: string
  children?: Folder[]
}

/** 创建目录请求 */
export interface CreateFolderRequest {
  name: string
  parentId: number | null
  spaceId: number
  sortOrder?: number
}

/** 更新目录请求 */
export interface UpdateFolderRequest {
  name?: string
  parentId?: number | null
  sortOrder?: number
}

/** 文件 */
export interface KbFile {
  id: number
  name: string
  folderId: number
  spaceId: number
  fileSize: number
  fileType: string
  mimeType: string
  storageKey: string
  storageBucket: string
  parseStatus: 'pending' | 'processing' | 'completed' | 'failed'
  parsedContent: string
  starred: boolean
  createdAt: string
  updatedAt: string
}

/** 文件上传请求 */
export interface FileUploadRequest {
  folderId: number
  spaceId: number
}

/** 笔记 */
export interface Doc {
  id: number
  title: string
  content: string
  folderId: number
  spaceId: number
  starred: boolean
  wordCount: number
  createdAt: string
  updatedAt: string
}

/** 创建笔记请求 */
export interface CreateDocRequest {
  title: string
  content?: string
  folderId: number
  spaceId: number
}

/** 更新笔记请求 */
export interface UpdateDocRequest {
  title?: string
  content?: string
  folderId?: number
}

/** 网页收藏 */
export interface WebPage {
  id: number
  title: string
  url: string
  content: string
  rawHtml: string
  folderId: number
  spaceId: number
  starred: boolean
  favicon: string
  createdAt: string
  updatedAt: string
}

/** 创建网页收藏请求 */
export interface CreateWebPageRequest {
  url: string
  folderId: number
  spaceId: number
}

/** 标签 */
export interface Tag {
  id: number
  name: string
  color: string
  createdAt: string
}

/** 资源标签关联 */
export interface ResourceTag {
  id: number
  tagId: number
  resourceId: number
  resourceType: 'file' | 'doc' | 'web'
}

/** 分享 */
export interface Share {
  id: number
  code: string
  resourceId: number
  resourceType: 'file' | 'doc' | 'web'
  extractCode: string
  expireAt: string | null
  viewCount: number
  title?: string
  createdBy: number
  createdAt: string
}

/** 创建分享请求 */
export interface CreateShareRequest {
  resourceId: number
  resourceType: 'file' | 'doc' | 'web'
  extractCode?: string
  expireAt?: string
}

/** 版本 */
export interface Version {
  id: number
  resourceId: number
  resourceType: 'file' | 'doc' | 'web'
  versionNumber: number
  content: string
  createdBy: number
  createdAt: string
}

/** 操作日志 */
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

/** 存储桶 */
export interface Bucket {
  id: number
  name: string
  provider: string
  endpoint: string
  bucketName: string
  accessKey: string
  secretKey: string
  region: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

/** 创建存储桶请求 */
export interface CreateBucketRequest {
  name: string
  provider: string
  endpoint: string
  bucketName: string
  accessKey: string
  secretKey: string
  region?: string
  isDefault?: boolean
}

/** 更新存储桶请求 */
export interface UpdateBucketRequest {
  name?: string
  provider?: string
  endpoint?: string
  bucketName?: string
  accessKey?: string
  secretKey?: string
  region?: string
  isDefault?: boolean
}

/** 搜索结果 */
export interface SearchResult {
  id: number
  title: string
  type: 'file' | 'doc' | 'web'
  content: string
  highlight: string
  folderId: number
  spaceId: number
  starred: boolean
  createdAt: string
  updatedAt: string
}

/** 搜索请求参数 */
export interface SearchParams {
  keyword: string
  type?: 'file' | 'doc' | 'web' | 'all'
  folderId?: number
  tagId?: number
  page?: number
  pageSize?: number
}

/** 回收站项 */
export interface TrashItem {
  id: number
  name: string
  type: 'file' | 'doc' | 'web'
  folderId: number
  spaceId: number
  deletedAt: string
  expireAt: string
}

/** 资源类型联合 */
export type ResourceType = 'file' | 'doc' | 'web'

/** 资源通用接口 */
export interface ResourceItem {
  id: number
  name: string
  title: string
  type: ResourceType
  folderId: number
  spaceId: number
  starred: boolean
  createdAt: string
  updatedAt: string
}

// ============================================================
// 微服务新增类型 (v6)
// ============================================================

/** API Token */
export interface ApiToken {
  id: number
  name: string
  token: string
  scopes: string[]
  status: number
  expireAt: string | null
  lastUsedAt: string | null
  createdAt: string
}

/** 创建 API Token 请求 */
export interface CreateTokenRequest {
  name: string
  scopes: string[]
  expireAt?: string
}

/** 运维 - 主机 */
export interface OpsHost {
  id: number
  name: string
  ip: string
  sshPort: number
  os: string
  cpuCores: number
  memoryMb: number
  diskGb: number
  status: string
  tags: string
  remark: string
  createdAt: string
  updatedAt: string
}

/** 运维 - 服务 */
export interface OpsService {
  id: number
  name: string
  hostId: number
  hostName: string
  port: number
  version: string
  type: string
  status: string
  healthCheckUrl: string
  remark: string
  createdAt: string
  updatedAt: string
}

/** 运维 - 部署记录 */
export interface Deployment {
  id: number
  serviceName: string
  hostName: string
  version: string
  status: string
  deployTime: string
  deployer: string
  remark: string
  createdAt: string
}

/** 运维 - 矛盾检测结果 */
export interface OpsConflict {
  id: number
  type: string
  severity: string
  description: string
  serviceName: string
  hostName: string
  resolved: boolean
  createdAt: string
}

/** 运维 - 看板数据 */
export interface DashboardVO {
  hostStats: { total: number; online: number; offline: number }
  serviceStats: { total: number; running: number; stopped: number }
  typeDistribution: Record<string, number>
  deployTrend: { date: string; count: number }[]
  recentDeploys: Deployment[]
  recentConflicts: OpsConflict[]
}

/** 运维 - 知识 */
export interface OpsKnowledge {
  id: number
  title: string
  category: string
  content: string
  tags: string
  createdAt: string
  updatedAt: string
}
