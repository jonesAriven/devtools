import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/token'
import MainLayout from '@/layouts/MainLayout.vue'
import { CONTEXT_PATH as ctx } from '@/config'
import { permCode, setupAuthGuard } from '@/utils/permissions'

/**
 * `meta.perm` = RBAC 权限点全码（Phase 2 三层同源之「路由层」）。
 * 未声明 `meta.perm` 的路由不受权限守卫管辖（详情页/回调页/登录页等）。
 */

const routes: RouteRecordRaw[] = [
  {
    path: `/login`,
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: `/sso-callback`,
    name: 'SsoCallback',
    component: () => import('@/views/sso/SsoCallbackView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: ``,
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: `/dashboard`,
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
      },
      {
        path: 'hosts',
        name: 'Hosts',
        component: () => import('@/views/hosts/HostsView.vue'),
        meta: { perm: permCode('menu', 'hosts') },
      },
      {
        path: 'credentials',
        name: 'Credentials',
        component: () => import('@/views/credentials/CredentialsView.vue'),
        meta: { perm: permCode('menu', 'credentials') },
      },
      {
        path: 'configs',
        name: 'Configs',
        component: () => import('@/views/configs/ConfigsView.vue'),
        meta: { perm: permCode('menu', 'configs') },
      },
      {
        path: 'services',
        name: 'Services',
        component: () => import('@/views/services/ServicesView.vue'),
        meta: { perm: permCode('menu', 'services') },
      },
      {
        // Phase 6 · 统一用户管理（6 应用共用同一份公共组件；菜单仅管理员可见，
        // 权限闸门在 auth-center AdminUserController 的 @PreAuthorize("hasRole('ADMIN')")）
        path: 'users',
        name: 'Users',
        component: () => import('@/views/users/UsersView.vue'),
        meta: { perm: permCode('menu', 'users') },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { requiresAuth: false },
  },
]

const router = createRouter({
  history: createWebHistory(ctx),
  routes,
})

router.beforeEach((to, _from, next) => {
  const token = getToken()

  if (to.meta.requiresAuth !== false && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (to.name === 'Login' && token) {
    next({ name: 'Dashboard' })
    return
  }

  next()
})

// Phase 2 · RBAC 路由守卫（三层同源之「路由层」）
// ⚠️ 必须在 `app.use(router)` 之前注册；只有声明了 meta.perm 的路由受管。
// 当前 auth-center 侧 sys_permission 为空（configured=false）→ 全放行，行为与接入前一致。
setupAuthGuard(router)

export default router
