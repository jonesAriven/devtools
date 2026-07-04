export interface R<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface PageParams {
  page: number
  size: number
  keyword?: string
}

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

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: User
}

export interface Host {
  id: number
  name: string
  ip: string
  sshPort: number
  username: string
  osType: string
  status: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface HostRequest {
  name: string
  ip: string
  sshPort?: number
  username?: string
  osType?: string
  status?: string
  remark?: string
}

export interface OpsService {
  id: number
  hostId: number
  hostName?: string
  name: string
  serviceType: string
  version: string
  port: number
  status: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface ServiceRequest {
  hostId: number
  name: string
  serviceType?: string
  version?: string
  port?: number
  status?: string
  remark?: string
}

export interface Port {
  id: number
  hostId: number
  hostName?: string
  port: number
  protocol: string
  serviceName: string
  exposed: number
  remark: string
  createdAt: string
  updatedAt: string
}

export interface PortRequest {
  hostId: number
  port: number
  protocol?: string
  serviceName?: string
  exposed?: number
  remark?: string
}

export interface Credential {
  id: number
  hostId: number
  hostName?: string
  credType: string
  username: string
  password: string
  passwordHint: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface CredentialRequest {
  hostId?: number
  credType: string
  username: string
  password: string
  passwordHint?: string
  remark?: string
}

export interface Domain {
  id: number
  domain: string
  subDomain: string
  targetHostId: number
  targetHostName?: string
  targetPort: number
  status: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface DomainRequest {
  domain: string
  subDomain?: string
  targetHostId?: number
  targetPort?: number
  status?: string
  remark?: string
}

export interface Dependency {
  id: number
  name: string
  depType: string
  version: string
  hostId: number
  hostName?: string
  installPath: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface DependencyRequest {
  name: string
  depType?: string
  version?: string
  hostId?: number
  installPath?: string
  remark?: string
}

export interface Deployment {
  id: number
  serviceName: string
  version: string
  hostId: number
  hostName?: string
  status: string
  deployType: string
  deployTime: string
  operator: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface DeploymentRequest {
  serviceName: string
  version?: string
  hostId?: number
  status?: string
  deployType?: string
  remark?: string
}

export interface OpsConflict {
  id: number
  conflictType: string
  level: string
  description: string
  resourceA: string
  resourceB: string
  status: string
  resolvedAt: string
  createdAt: string
  updatedAt: string
}

export interface OpsKnowledge {
  id: number
  title: string
  category: string
  content: string
  tags: string
  author: string
  viewCount: number
  createdAt: string
  updatedAt: string
}

export interface OpsKnowledgeRequest {
  title: string
  category?: string
  content?: string
  tags?: string
}

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

export interface ImportResult {
  successCount: number
  failCount: number
  details: { row: number; message: string }[]
}

export interface DashboardVO {
  hostStats: Record<string, number>
  serviceStats: Record<string, number>
  serviceTypeDistribution: Record<string, number>
  recentDeployCount: number
  unresolvedConflictCount: number
  deployTrend: Array<Record<string, any>>
  recentDeploys: Array<{
    serviceName: string
    version: string
    operator: string
    deployTime: string
    result: number
    rollback: number
  }>
  recentConflicts: Array<{
    ruleCode: string
    ruleName: string
    severity: number
    targetName: string
    detail: string
    detectedAt: string
  }>
}
