import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { permCode, setupAuthGuard } from '@/utils/permissions'

/**
 * `meta.perm` = RBAC 权限点全码（Phase 2 三层同源之「路由层」）。
 * 未声明 `meta.perm` 的路由不受权限守卫管辖。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    // 统一认证回调（浏览器从 auth-center 带 ?code&state 回来）
    path: '/auth/callback',
    name: 'SsoCallback',
    component: () => import('@/views/SsoCallbackView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        // 工作台为基础项，不做权限管辖
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue')
      },
      {
        path: 'manage',
        name: 'Manage',
        component: () => import('@/views/ManageView.vue'),
        meta: { perm: permCode('menu', 'manage') }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/UsersView.vue'),
        meta: { requiresAdmin: true, perm: permCode('menu', 'users') }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory('/portal/'),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth !== false)
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin === true)

  if (requiresAuth && !userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (requiresAdmin && !userStore.isAdmin) {
    next({ path: '/' })
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next({ path: '/' })
  } else {
    next()
  }
})

// Phase 2 · RBAC 路由守卫（三层同源之「路由层」）
// ⚠️ 必须在 `app.use(router)` 之前注册；只有声明了 meta.perm 的路由受管。
// 当前 auth-center 侧 sys_permission 为空（configured=false）→ 全放行，行为与接入前一致。
setupAuthGuard(router)

export default router
