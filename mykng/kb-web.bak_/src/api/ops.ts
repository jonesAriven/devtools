import request from './index'
import type {
  R, PageResult, PageParams,
  OpsHost, OpsService, Deployment, OpsConflict, DashboardVO, OpsKnowledge
} from '@/types'

// ============================================================
// 主机管理
// ============================================================

/** 获取主机列表 */
export function getHostList(params?: PageParams & { keyword?: string; status?: string }) {
  return request.get<R<PageResult<OpsHost>>>('/ops/host/list', { params })
}

/** 获取主机详情 */
export function getHostDetail(id: number) {
  return request.get<R<OpsHost>>(`/ops/host/${id}`)
}

/** 创建主机 */
export function createHost(data: Partial<OpsHost>) {
  return request.post<R<OpsHost>>('/ops/host', data)
}

/** 更新主机 */
export function updateHost(id: number, data: Partial<OpsHost>) {
  return request.put<R<OpsHost>>(`/ops/host/${id}`, data)
}

/** 删除主机 */
export function deleteHost(id: number) {
  return request.delete<R<void>>(`/ops/host/${id}`)
}

// ============================================================
// 服务管理
// ============================================================

/** 获取服务列表 */
export function getServiceList(params?: PageParams & { keyword?: string; status?: string; hostId?: number }) {
  return request.get<R<PageResult<OpsService>>>('/ops/service/list', { params })
}

/** 获取服务详情 */
export function getServiceDetail(id: number) {
  return request.get<R<OpsService>>(`/ops/service/${id}`)
}

/** 创建服务 */
export function createService(data: Partial<OpsService>) {
  return request.post<R<OpsService>>('/ops/service', data)
}

/** 更新服务 */
export function updateService(id: number, data: Partial<OpsService>) {
  return request.put<R<OpsService>>(`/ops/service/${id}`, data)
}

/** 删除服务 */
export function deleteService(id: number) {
  return request.delete<R<void>>(`/ops/service/${id}`)
}

// ============================================================
// 部署记录
// ============================================================

/** 获取部署记录列表 */
export function getDeploymentList(params?: PageParams & { serviceName?: string; hostName?: string }) {
  return request.get<R<PageResult<Deployment>>>('/ops/deployment/list', { params })
}

/** 创建部署记录 */
export function createDeployment(data: Partial<Deployment>) {
  return request.post<R<Deployment>>('/ops/deployment', data)
}

/** 获取部署记录详情 */
export function getDeploymentDetail(id: number) {
  return request.get<R<Deployment>>(`/ops/deployment/${id}`)
}

// ============================================================
// 矛盾检测
// ============================================================

/** 获取矛盾检测列表 */
export function getConflictList(params?: PageParams & { resolved?: boolean; type?: string }) {
  return request.get<R<PageResult<OpsConflict>>>('/ops/conflict/list', { params })
}

/** 手动触发矛盾检测 */
export function triggerConflictDetection() {
  return request.post<R<OpsConflict[]>>('/ops/conflict/detect')
}

/** 标记矛盾已解决 */
export function resolveConflict(id: number) {
  return request.put<R<void>>(`/ops/conflict/${id}/resolve`)
}

// ============================================================
// 运维看板
// ============================================================

/** 获取看板数据 */
export function getDashboard() {
  return request.get<R<DashboardVO>>('/ops/dashboard')
}

// ============================================================
// 运维知识
// ============================================================

/** 获取运维知识列表 */
export function getOpsKnowledgeList(params?: PageParams & { keyword?: string; category?: string }) {
  return request.get<R<PageResult<OpsKnowledge>>>('/ops/knowledge/list', { params })
}

/** 获取运维知识详情 */
export function getOpsKnowledgeDetail(id: number) {
  return request.get<R<OpsKnowledge>>(`/ops/knowledge/${id}`)
}

/** 创建运维知识 */
export function createOpsKnowledge(data: Partial<OpsKnowledge>) {
  return request.post<R<OpsKnowledge>>('/ops/knowledge', data)
}

/** 更新运维知识 */
export function updateOpsKnowledge(id: number, data: Partial<OpsKnowledge>) {
  return request.put<R<OpsKnowledge>>(`/ops/knowledge/${id}`, data)
}

/** 删除运维知识 */
export function deleteOpsKnowledge(id: number) {
  return request.delete<R<void>>(`/ops/knowledge/${id}`)
}

// ============================================================
// 导入
// ============================================================

/** CSV/JSON 导入 */
export function importData(formData: FormData) {
  return request.post<R<{ success: number; failed: number; errors: string[] }>>('/ops/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}
