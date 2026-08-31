// 视图模式（树形 / 扁平）持久化
//
// 背景：viewMode 原本是组件内 ref，跨路由导航时组件被销毁重建，
// 用户切到扁平模式后跳去别的页面再回来就退回树形。
//
// 取值优先级：URL query (?view=flat) > localStorage > 默认值 tree
//   - query 优先：支持刷新保持、分享带视图状态的链接
//   - localStorage 兜底：跨路由导航（链接本身不带 query）也能记住偏好
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const LS_KEY = 'cosmic.viewMode'

export function useViewMode() {
  const route = useRoute()
  const router = useRouter()

  return computed({
    get() {
      const q = route.query.view
      if (q === 'flat' || q === 'tree') return q
      try {
        return localStorage.getItem(LS_KEY) === 'flat' ? 'flat' : 'tree'
      } catch {
        return 'tree' // 隐私模式 / localStorage 不可用
      }
    },
    set(v) {
      try {
        localStorage.setItem(LS_KEY, v)
      } catch {
        /* localStorage 不可用时静默降级，仅靠 URL query */
      }
      router.replace({ path: route.path, query: { ...route.query, view: v } })
    },
  })
}
