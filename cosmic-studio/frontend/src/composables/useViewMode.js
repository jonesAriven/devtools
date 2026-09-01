// 视图模式（树形 / 扁平）持久化
//
// 背景：viewMode 原本是组件内 ref，跨路由导航时组件被销毁重建，
// 用户切到扁平模式后跳去别的页面再回来就退回树形。
//
// 取值优先级：URL query (?view=flat) > localStorage > 默认值 tree
//   - query 优先：支持刷新保持、分享带视图状态的链接
//   - localStorage 兜底：跨路由导航（链接本身不带 query）也能记住偏好
//
// ⚠️ 关键修正：此前用全局固定键 `cosmic.viewMode`，导致 ProjectDetail 与
// ArchiveDetail 共享同一份偏好——在一个详情页切扁平，另一个详情页也被带偏。
// 现改为 usePersistentState('viewMode')，命名空间 = route.path，
// 每个详情页（/projects/:id、/archive/:id）各自记忆，互不串台。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePersistentState } from './usePersistentState'

export function useViewMode() {
  const route = useRoute()
  const router = useRouter()
  const stored = usePersistentState('viewMode', 'tree')

  return computed({
    get() {
      const q = route.query.view
      if (q === 'flat' || q === 'tree') return q
      return stored.value
    },
    set(v) {
      stored.value = v
      router.replace({ path: route.path, query: { ...route.query, view: v } })
    },
  })
}
