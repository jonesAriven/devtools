import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 页面级 UI 状态持久化（跨路由导航 / 刷新保持）。
 *
 * 背景：各页面的筛选、搜索词、页码、选中项原本都是组件内 ref，
 * 路由一跳组件就销毁重建，用户切走再切回全部归零。
 * 逐个页面补 localStorage 太散，这里收敛成一处。
 *
 * 命名空间 = 路由路径，因此 /projects/1 与 /projects/2 各自记忆互不干扰。
 * 只用于「视图状态」——弹窗开关、表单草稿、loading 一类不该持久化的不要传进来。
 *
 * @param {string} key 页内唯一键
 * @param {*} defaultValue 默认值（同时用作类型校验基准：存储值类型不符则回退默认）
 */
const NS = 'cosmic:viewstate:'
const TTL_MS = 30 * 24 * 60 * 60 * 1000 // 30 天未访问即过期，避免键无限堆积

let swept = false

/** 清理过期键：命名空间可能随访问过的项目数增长，每次会话启动时扫一遍 */
function sweep() {
  if (swept) return
  swept = true
  try {
    const now = Date.now()
    const dead = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (!k || !k.startsWith(NS)) continue
      try {
        const { t } = JSON.parse(localStorage.getItem(k) || '{}')
        if (!t || now - t > TTL_MS) dead.push(k)
      } catch {
        dead.push(k) // 解析不了的旧格式，一并清掉
      }
    }
    dead.forEach(k => localStorage.removeItem(k))
  } catch {
    /* localStorage 不可用时静默降级为普通 ref */
  }
}

function read(storageKey, defaultValue) {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return defaultValue
    const { v, t } = JSON.parse(raw)
    if (!t || Date.now() - t > TTL_MS) return defaultValue
    // 类型校验：防止升级后旧格式的脏数据导致渲染异常。
    // 特例：defaultValue 为 null 时（表示"未选中/未设置"），允许任意类型的存储值通过，
    // 因为 null 本身就是"无值"占位符，不应拿它做类型锚点。
    if (defaultValue !== null && typeof v !== typeof defaultValue) return defaultValue
    if (Array.isArray(v) !== Array.isArray(defaultValue)) return defaultValue
    return v
  } catch {
    return defaultValue
  }
}

function write(storageKey, value) {
  try {
    localStorage.setItem(storageKey, JSON.stringify({ v: value, t: Date.now() }))
  } catch {
    /* 配额满 / 隐私模式：降级为不持久化，页面仍能正常使用 */
  }
}

export function usePersistentState(key, defaultValue) {
  sweep()

  let path = ''
  try {
    path = useRoute().path
  } catch {
    /* setup 外调用时无路由上下文，退化为全局键 */
  }
  const storageKey = `${NS}${path}:${key}`

  const state = ref(read(storageKey, defaultValue))
  watch(state, v => write(storageKey, v), { deep: true })
  return state
}
