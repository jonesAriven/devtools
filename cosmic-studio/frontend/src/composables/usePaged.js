import { computed, ref, watch } from 'vue'
import { usePersistentState } from './usePersistentState'

/**
 * 服务端分页状态机。
 *
 * 对齐后端统一契约：
 *   GET ...?page=1&page_size=20&keyword=  →  { list, total, page, page_size }
 *
 * 收敛的重复点：
 *   - 翻页控件 + pageSize 选择器（此前 7 个页面全无，列表一次性全量渲染）
 *   - loading / empty / error 三态
 *   - 筛选条件变化时必须重置回第 1 页（否则会停在一个空页上）
 *
 * @param {(params: object) => Promise<any>} fetcher 接收分页参数，返回 axios 响应
 * @param {object} opts
 * @param {number} opts.pageSize 初始每页条数
 * @param {() => object} opts.extraParams 额外的筛选参数（keyword / status …）
 * @param {boolean} opts.immediate 是否挂载即加载
 * @param {string} opts.key 同页多个分页器时的区分前缀
 *
 * 页码 / 每页条数走持久化：翻到第 5 页切去别的菜单，回来还在第 5 页。
 */
export function usePaged(fetcher, opts = {}) {
  const {
    pageSize: initialSize = 20,
    extraParams = () => ({}),
    immediate = false,
    key = '',
  } = opts
  const prefix = key ? `${key}.` : ''

  const list = ref([])
  const total = ref(0)
  const page = usePersistentState(`${prefix}page`, 1)
  const pageSize = usePersistentState(`${prefix}pageSize`, initialSize)
  const loading = ref(false)
  const error = ref('')
  const loaded = ref(false)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await fetcher({
        page: page.value,
        page_size: pageSize.value,
        ...extraParams(),
      })
      list.value = data?.list ?? []
      total.value = data?.total ?? 0
      // 后端会把越界值夹紧，这里同步回前端，避免页码显示与实际不符
      if (data?.page) page.value = data.page
      if (data?.page_size) pageSize.value = data.page_size
      loaded.value = true
    } catch (e) {
      error.value = e?.response?.data?.detail || e?.message || '加载失败'
      list.value = []
      total.value = 0
      loaded.value = false
    } finally {
      loading.value = false
    }
  }

  function reset() {
    // 已经在第 1 页时不会触发 watch，需手动 reload
    if (page.value === 1) load()
    else page.value = 1
  }

  function onPageChange(p) { page.value = p }
  function onSizeChange(s) { pageSize.value = s; page.value = 1 }

  watch([page, pageSize], load)

  const empty = computed(() => loaded.value && !loading.value && !error.value && !list.value.length)

  if (immediate) load()

  return {
    list, total, page, pageSize, loading, error, loaded, empty,
    load, reset, onPageChange, onSizeChange,
  }
}

/**
 * 客户端分页：用于总量本来就小的列表（规范 24 条、版本、用户 …）。
 * 这些场景为省一次后端改造，在内存里切片即可。
 *
 * @param {import('vue').Ref<Array>} sourceRef 全量数据源
 * @param {number} initialSize
 * @param {object} opts
 * @param {string} opts.key 同页多个分页器时的区分前缀
 */
export function useLocalPaged(sourceRef, initialSize = 20, opts = {}) {
  const prefix = opts.key ? `${opts.key}.` : ''
  const page = usePersistentState(`${prefix}page`, 1)
  const pageSize = usePersistentState(`${prefix}pageSize`, initialSize)
  const total = computed(() => (sourceRef.value || []).length)
  const list = computed(() => {
    const src = sourceRef.value || []
    const start = (page.value - 1) * pageSize.value
    return src.slice(start, start + pageSize.value)
  })
  const empty = computed(() => total.value === 0)
  const loading = ref(false)
  const error = ref('')

  function onPageChange(p) { page.value = p }
  function onSizeChange(s) { pageSize.value = s; page.value = 1 }
  function reset() { page.value = 1 }

  return { list, total, page, pageSize, loading, error, empty, onPageChange, onSizeChange, reset }
}

/** 分页器统一的 layout / 页大小选项，避免每处各写一套 */
export const PAGER_LAYOUT = 'total, sizes, prev, pager, next, jumper'
export const PAGER_SIZES = [10, 20, 50, 100]
