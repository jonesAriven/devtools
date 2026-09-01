// 导航调试日志：排查「切菜单/切页签后详情页丢失、状态被重置」这类问题。
//
// 为什么需要：SPA 里路由跳转由多处触发（侧栏菜单、顶部页签、页签关闭、页面内按钮、
// 浏览器前进后退），只靠肉眼看页面很难判断到底是哪条路径把路由改了。
// 这里统一收口，输出两条线索：
//   1. Console 打印，前缀 [COSMIC-NAV]，浏览器 DevTools 里搜这个前缀即可看到完整链路
//   2. 环形缓冲 window.__COSMIC_NAV_LOG__（最近 200 条），便于自动化测试直接读取数组，
//      不必去挂 CDP 的 console 事件
//
// 生产环境保留：日志量极小（只在导航时打印），排查线上问题时价值远大于开销。

const RING_SIZE = 200
const PREFIX = '%c[COSMIC-NAV]'
const STYLE = 'color:#409eff;font-weight:bold'

// 构建信息由 vite define 注入（见 vite.config.js）。
// 一眼判断浏览器里跑的到底是哪一次构建的产物 —— 这是排查「改了没生效」的第一道闸。
export const BUILD_INFO =
  typeof __BUILD_INFO__ !== 'undefined'
    ? __BUILD_INFO__
    : { commit: 'dev', time: 'dev' }

if (typeof window !== 'undefined') {
  if (!window.__COSMIC_NAV_LOG__) window.__COSMIC_NAV_LOG__ = []
}

function stamp() {
  const d = new Date()
  const p = (n, w = 2) => String(n).padStart(w, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`
}

/**
 * 记录一条导航事件。
 * @param {string} action 事件名，如 'menu-click' / 'tab-click' / 'route-change'
 * @param {object} data   结构化上下文（路径、来源、记忆快照…）
 */
export function navLog(action, data) {
  const entry = { ts: stamp(), action, ...(data || {}) }
  try {
    if (typeof window !== 'undefined') {
      const ring = window.__COSMIC_NAV_LOG__
      ring.push(entry)
      if (ring.length > RING_SIZE) ring.shift()
    }
  } catch { /* 无 window 环境（SSR/单测）直接跳过缓冲 */ }
  // eslint-disable-next-line no-console
  console.log(PREFIX, STYLE, action, entry)
  return entry
}

/** 应用启动横幅：把构建信息打到 Console，确认当前跑的是哪个包 */
export function logBoot() {
  // eslint-disable-next-line no-console
  console.log(
    '%c[COSMIC]%c build %s @ %s',
    'background:#409eff;color:#fff;padding:2px 6px;border-radius:3px 0 0 3px',
    'background:#303133;color:#fff;padding:2px 6px;border-radius:0 3px 3px 0',
    BUILD_INFO.commit,
    BUILD_INFO.time,
  )
  navLog('boot', { ...BUILD_INFO })
}
