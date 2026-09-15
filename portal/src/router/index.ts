import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { permCode, setupAuthGuard } from '@/utils/permissions'
// T7 组件收敛（2026-09-15）：router base 与 main.ts 的 __MARSCHAT_APP_BASE__ 同源于运行时配置，
// 避免「导航 base 不一致」这类只在二级路由才暴露的空页问题（check-spa-config.sh 有静态门禁）
import { CONTEXT_PATH } from '@/config/runtime'

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
        // 「门户用户」= 应用作用域：只看与本门户有关的用户（Phase 8）
        path: 'users',
        name: 'Users',
        component: () => import('@/views/UsersView.vue'),
        meta: { requiresAdmin: true, perm: permCode('menu', 'users') }
      },
      {
        // 「统一认证中心」= 平台作用域：全平台统一身份 / 跨应用授权 / 账号映射 / 角色菜单授权。
        //
        // ⚠️ 刻意**不声明 `meta.perm`**：它是平台管理入口，不应作为「可授给普通用户的应用菜单」
        // 出现在中心授权树里。默认授权种子会把新注册的 menu 权限点绑到平台 `user` 角色，
        // 一旦注册就等于"发给所有普通用户"。故这里只用 `requiresAdmin` 做本地强闸
        // （本文件 beforeEach 先于 createAuthGuard 执行，普通用户必被挡回工作台）。
        path: 'admin',
        name: 'AdminConsole',
        component: () => import('@/views/AdminConsoleView.vue'),
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(`${CONTEXT_PATH}/`),
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
