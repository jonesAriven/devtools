import { ref, computed } from 'vue'
import type { PageParams } from '@/types'

export function usePagination(defaultPageSize = 20) {
  const page = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)

  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  const pageParams = computed<PageParams>(() => ({
    page: page.value,
    pageSize: pageSize.value,
  }))

  function handleCurrentChange(val: number) {
    page.value = val
  }

  function handleSizeChange(val: number) {
    pageSize.value = val
    page.value = 1
  }

  function reset() {
    page.value = 1
    total.value = 0
  }

  return {
    page,
    pageSize,
    total,
    totalPages,
    pageParams,
    handleCurrentChange,
    handleSizeChange,
    reset,
  }
}
