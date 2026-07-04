import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ModuleStatus } from '@/types'
import { getModuleStatus } from '@/api/system'

/**
 * 系统模块状态 Store（M7-2 动态菜单）
 *
 * 降级策略：拉取失败时默认所有模块可用，避免阻塞用户使用。
 * 模块未知（后端未返回该模块记录）时同样默认可用。
 */
export const useModuleStore = defineStore('module', () => {
  /** 已知模块名常量，方便调用方使用 */
  const MODULE_NAMES = {
    GATEWAY: 'kb-gateway',
    AUTH: 'kb-auth',
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
      const res = await getModuleStatus()
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

  /** 判断指定模块是否可用（降级时默认可用） */
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
    return m.available
  }

  return {
    MODULE_NAMES,
    modules,
    loaded,
    fetchFailed,
    fetchModules,
    isModuleAvailable,
  }
})
