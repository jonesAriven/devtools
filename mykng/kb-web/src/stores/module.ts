import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ModuleStatus, ModuleStateValue } from '@/types'
// 后端接口函数与下方 store action 同名，这里用别名避免遮蔽
import { getModuleStatus as getModuleStatusApi } from '@/api/system'


/**
 * 系统模块状态 Store（M7-2 动态菜单）
 *
 * 后端返回四态状态：OK（可用）/ DOWN（宕机，运维事件）/ MISSING（未注册，配置漂移）/ UNEXPECTED（野模块但可达）。
 * 菜单显隐改为「灰显 + 状态点 + 原因 tooltip」，不再静默移除菜单项。
 *
 * 降级策略：拉取失败时默认所有模块可用，避免瞬时故障误隐藏菜单。
 * 模块未知（后端未返回该模块记录）时同样默认可用。
 */
export const useModuleStore = defineStore('module', () => {
  /** 已知模块名常量，方便调用方使用 */
  const MODULE_NAMES = {
    GATEWAY: 'kb-gateway',
    AUTH: 'auth-center',
    FILE: 'kb-file',
    KNOWLEDGE: 'kb-knowledge',
    INTELLIGENCE: 'kb-intelligence',
  } as const

  /** 模块状态表：name -> ModuleStatus */
  const modules = ref<Record<string, ModuleStatus>>({})
  /** 是否已完成首次拉取 */
  const loaded = ref(false)
  /** 拉取是否失败（用于触发降级策略） */
  const fetchFailed = ref(false)

  /** 拉取模块状态；失败时不抛错，内部降级为"全部可用" */
  async function fetchModules() {
    try {
      const res = await getModuleStatusApi()
      const list = res.data.data || []
      const map: Record<string, ModuleStatus> = {}
      for (const m of list) {
        map[m.name] = m
      }
      modules.value = map
      loaded.value = true
      fetchFailed.value = false
    } catch {
      // 降级：拉取失败时清空状态表，isModuleAvailable 将对任何模块返回 true
      modules.value = {}
      loaded.value = true
      fetchFailed.value = true
    }
  }

  /**
   * 判断指定模块是否可用（降级时默认可用）
   *
   * 直接采信后端算好的 `available`，不在前端重算一遍。两个原因：
   * 1. 判定规则（OK / UNEXPECTED / UNKNOWN 均算可用）只应该有一份，前端再写一遍
   *    就会变成"两处实现"，正是本次改造要根治的那种漂移；
   * 2. 滚动发布期间前端可能比网关先上新版本，`available` 是旧后端就有的字段，
   *    用它才不会在版本错配的窗口里把菜单全部灰掉。`state` 是新字段，只能用于展示原因。
   */
  function isModuleAvailable(name: string): boolean {
    // 未完成首次拉取或拉取失败 → 降级：默认可用
    if (!loaded.value || fetchFailed.value) {
      return true
    }
    const m = modules.value[name]
    // 未知模块（后端未返回）→ 默认可用
    if (!m) {
      return true
    }
    // 后端缺失该字段（极老的网关）时同样降级为可用
    return m.available !== false
  }

  /** 取模块完整状态记录（拉取失败/未返回时返回 undefined） */
  function getModuleStatus(name: string): ModuleStatus | undefined {
    if (!loaded.value || fetchFailed.value) {
      return undefined
    }
    return modules.value[name]
  }

  /**
   * 取模块四态：OK / DOWN / MISSING / UNEXPECTED。
   * 拉取失败 / 未返回该模块 / 后端未带该字段（老网关）时返回 'UNKNOWN'（视作可用，与降级策略一致）。
   */
  function getModuleState(name: string): ModuleStateValue {
    const m = getModuleStatus(name)
    return m && m.state ? m.state : 'UNKNOWN'
  }

  /**
   * 取模块不可用的中文原因，仅不可用时返回非空：
   * - DOWN：服务曾在线、当前实例数为 0 → 运维事件（宕机）
   * - MISSING：从未注册过实例 → 配置/命名漂移（如模块改名未同步注册表）
   * - 其他（OK/UNEXPECTED/UNKNOWN）→ 空串
   */
  function getModuleUnavailableReason(name: string): string {
    const m = getModuleStatus(name)
    if (!m) return ''
    switch (m.state) {
      case 'DOWN':
        return '服务暂时不可用（已下线）'
      case 'MISSING':
        return '服务未注册（疑似配置或命名不一致，请联系管理员）'
      default:
        return ''
    }
  }

  return {
    MODULE_NAMES,
    modules,
    loaded,
    fetchFailed,
    fetchModules,
    isModuleAvailable,
    getModuleStatus,
    getModuleState,
    getModuleUnavailableReason,
  }
})
