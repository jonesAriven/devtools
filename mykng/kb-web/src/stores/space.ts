import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Space } from '@/types'
import { getSpaceList } from '@/api/space'

export const useSpaceStore = defineStore('space', () => {
  const currentSpace = ref<Space | null>(null)
  const spaceList = ref<Space[]>([])

  async function fetchSpaceList() {
    const res = await getSpaceList()
    spaceList.value = res.data.data
    if (!currentSpace.value && spaceList.value.length > 0) {
      currentSpace.value = spaceList.value[0]
    }
  }

  function setCurrentSpace(space: Space) {
    currentSpace.value = space
  }

  function clearCurrentSpace() {
    currentSpace.value = null
  }

  return {
    currentSpace,
    spaceList,
    fetchSpaceList,
    setCurrentSpace,
    clearCurrentSpace,
  }
})
