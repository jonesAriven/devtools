import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SystemConfig } from '@/config/systems'
import { getAllSystems } from '@/api/system'

export const useSystemStore = defineStore('system', () => {
  const systems = ref<SystemConfig[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const totalCount = computed(() => systems.value.length)

  function getCountByCategory(category: string): number {
    return systems.value.filter(s => s.category === category).length
  }

  async function fetchSystems() {
    if (loaded.value && systems.value.length > 0) {
      return systems.value
    }
    loading.value = true
    try {
      const data = await getAllSystems()
      systems.value = data
      loaded.value = true
      return data
    } finally {
      loading.value = false
    }
  }

  function refreshSystems() {
    loaded.value = false
    return fetchSystems()
  }

  function addSystem(system: SystemConfig) {
    systems.value.push(system)
  }

  function updateSystem(system: SystemConfig) {
    const idx = systems.value.findIndex(s => s.id === system.id)
    if (idx !== -1) {
      systems.value[idx] = system
    }
  }

  function removeSystem(id: string) {
    systems.value = systems.value.filter(s => s.id !== id)
  }

  return {
    systems,
    loading,
    loaded,
    totalCount,
    getCountByCategory,
    fetchSystems,
    refreshSystems,
    addSystem,
    updateSystem,
    removeSystem,
  }
})
