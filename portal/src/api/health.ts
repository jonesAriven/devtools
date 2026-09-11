import type { SystemStatus } from '@/config/systems'
import portalRequest from './request'

/** 健康检查结果 */
export interface HealthResult {
  id: string
  status: SystemStatus
  latency?: number
  /** 后端读到的真实 HTTP 状态码（前端 no-cors 拿不到，恒为 0） */
  httpStatus?: number
  error?: string
}

/** 后端 /health/check 返回的单项 */
interface HealthProbeItem {
  id: number | string
  url?: string
  status: SystemStatus
  latency?: number
  httpStatus?: number
  error?: string
}

/**
 * 批量健康检查（后端代跑）—— 台账 L026。
 *
 * 原实现在前端直接 fetch 各系统的 healthCheckUrl：面板跑在 main.marschat.online，
 * 目标是 kb/tools/monitor 等异域，跨域且目标无 CORS 头 → 必然失败，31 个系统全部「未检测」。
 * 且 mode:'no-cors' 只能拿到 opaque 响应，无法区分 200 与 503。
 *
 * 现改为 portal-server 同源代跑：前端只传系统 id，URL 由后端从数据库读取（不接收任意 URL，无 SSRF 面）。
 */
export async function checkHealth(
  items: { id: string; healthCheckUrl?: string }[]
): Promise<Map<string, HealthResult>> {
  const results = new Map<string, HealthResult>()

  // 只对「配置了健康检查地址」的系统发起检测，与后端按 id 查库的口径保持一致
  const ids = items
    .filter((item) => item.healthCheckUrl)
    .map((item) => Number(item.id))
    .filter((n) => Number.isFinite(n))

  if (ids.length === 0) return results

  try {
    const data = await portalRequest.post<unknown, HealthProbeItem[]>('/health/check', { ids })
    for (const item of data || []) {
      results.set(String(item.id), {
        id: String(item.id),
        status: item.status,
        latency: item.latency,
        httpStatus: item.httpStatus,
        error: item.error,
      })
    }
  } catch {
    // 响应拦截器已统一提示；此处返回空 Map，卡片回落为「未检测」而不是误报在线
  }
  return results
}
