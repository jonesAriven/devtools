/**
 * 运行时配置（Phase 3 · R6 app-config.json 试点）
 *
 * 优先级：运行时 public/app-config.json（apps-registry.yml 派生，同产物多环境）
 *       > 编译期 import.meta.env > 内置默认。
 *
 * ⚠️ 试点实现用同步 XHR（模块求值时保证填充，消费方同步常量引用零改动）；
 *    浏览器对主线程同步 XHR 有弃用告警，main.ts bootstrap 化为后续项。
 */
let runtime: Record<string, string> = {}
try {
  const xhr = new XMLHttpRequest()
  xhr.open('GET', `${import.meta.env.BASE_URL}app-config.json`, false)
  xhr.send(null)
  if (xhr.status === 200) {
    runtime = JSON.parse(xhr.responseText)
  }
} catch {
  // 拉取失败回落编译期 env/默认值——本地开发无产物也能跑
}

export const CONTEXT_PATH = runtime.contextPath ?? import.meta.env.VITE_CONTEXT_PATH ?? '/ops'
export const API_BASE_URL = runtime.apiBase ?? import.meta.env.VITE_API_BASE_URL ?? '/ops/ops-api'
export const AUTH_BASE_URL = runtime.authApiBase ?? import.meta.env.VITE_AUTH_BASE_URL ?? '/ops-api'
