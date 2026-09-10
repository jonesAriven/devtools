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
  size: number
}

/** 分页请求参数 */
export interface PageParams {
  page: number
  size: number
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
  userId?: number
  type: string
  size: number
  minioPath?: string
  parseStatus: 'PENDING' | 'PARSING' | 'READY' | 'PARSE_FAILED'
  parseError?: string
  starred: boolean
  deleted?: number
  createdAt: string
  updatedAt: string
}

/** 文件上传请求 */
export interface FileUploadRequest {
  folderId: number
  spaceId: number
}

/** 笔记格式 */
export type DocFormat = 'html' | 'markdown'

/** 笔记 */
export interface Doc {
  id: number
  title: string
  content: string
  format: DocFormat
  folderId: number
  spaceId: number
  starred: boolean
  wordCount: number
  createdAt: string
  updatedAt: string
}

/** 文档版本历史 */
export interface DocVersion {
  id: string
  docId: number
  userId: number
  content: string
  version: number
  isCurrent: boolean
  createdAt: string
}

/** 创建笔记请求 */
export interface CreateDocRequest {
  title: string
  content?: string
  format?: DocFormat
  folderId: number
  spaceId: number
}

/** 更新笔记请求 */
export interface UpdateDocRequest {
  title?: string
  content?: string
  format?: DocFormat
  folderId?: number
}

/** 资源树节点 */
export interface ResourceTreeNode {
  id: number
  name: string
  type: 'folder' | 'doc' | 'file' | 'web'
  format?: DocFormat
  url?: string
  children?: ResourceTreeNode[]
}

/** 文档大纲项 */
export interface OutlineItem {
  id: string
  text: string
  level: number // 1-6
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
  size?: number
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

// ============================================================
// 系统模块状态 (M7-2 动态菜单)
// ============================================================

/** 系统模块状态 */
/** 模块健康四态 + 兜底态（UNKNOWN = Nacos 整体不可达，无法判定） */
export type ModuleStateValue = 'OK' | 'DOWN' | 'MISSING' | 'UNEXPECTED' | 'UNKNOWN'

export interface ModuleStatus {
  /** 模块名称，如 kb-gateway / auth-center / kb-file / kb-knowledge / kb-intelligence */
  name: string
  /**
   * 四态健康状态：
   * - OK：在期望集内且 Nacos 有实例 → 可用
   * - DOWN：在期望集内、曾有实例、当前实例数为 0 → 不可用（服务宕机，运维事件）
   * - MISSING：在期望集内、从未注册过实例 → 不可用（配置/命名漂移，如模块改名未同步注册表）
   * - UNEXPECTED：不在期望集内但 Nacos 有实例 → 服务实际存活，只是未声明（野模块）
   * - UNKNOWN：Nacos 整体不可达，无法判定 → 视作可用（避免一次抖动把所有菜单灰掉）
   */
  state: ModuleStateValue
  /** 是否在注册表期望集内 */
  expected: boolean
  /** Nacos 实际实例数 */
  actual: number
  /** 网关进程启动以来是否见过该模块实例（区分 DOWN 与 MISSING） */
  everSeen: boolean
  /** Nacos 服务列表中是否存在该名字（即使实例数为 0） */
  inNacosServiceList: boolean
  /** 是否可用（等效于 state 为 OK / UNEXPECTED / UNKNOWN），后端算好后给出，兼容字段 */
  available: boolean
  /** 健康状态旧字段，兼容保留：UP / DOWN / UNKNOWN */
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  /** 在线实例数旧字段，兼容保留，等于 actual */
  instances: number
}
