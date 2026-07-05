import type { SystemStatus } from '@/config/systems'

/** 健康检查结果 */
export interface HealthResult {
  id: string
  status: SystemStatus
  latency?: number
  error?: string
}

function isHttps(url: string): boolean {
  return url.startsWith('https://')
}

/** 单个系统健康检查 */
async function checkOne(id: string, url: string): Promise<HealthResult> {
  if (!isHttps(url)) {
    return { id, status: 'unknown', error: 'Mixed Content: HTTP address skipped on HTTPS page' }
  }
  const start = Date.now()
  try {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 5000)
    const resp = await fetch(url, {
      method: 'HEAD',
      mode: 'no-cors',
      signal: controller.signal,
    })
    clearTimeout(timeout)
    const latency = Date.now() - start
    return { id, status: 'online', latency }
  } catch (err) {
    return {
      id,
      status: 'offline',
      error: err instanceof Error ? err.message : 'unknown error',
    }
  }
}

/** 批量健康检查 */
export async function checkHealth(
  items: { id: string; healthCheckUrl?: string }[]
): Promise<Map<string, HealthResult>> {
  const results = new Map<string, HealthResult>()
  const tasks = items
    .filter((item) => item.healthCheckUrl)
    .map((item) => checkOne(item.id, item.healthCheckUrl!))

  const settled = await Promise.allSettled(tasks)
  for (const result of settled) {
    if (result.status === 'fulfilled') {
      results.set(result.value.id, result.value)
    }
  }
  return results
}
