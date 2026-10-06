import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { getToken, permCode, CONTEXT_PATH as ctx } from '@/config'

const routes: RouteRecordRaw[] = [
  {
    path: `/login`,
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  // SSO 回调路由（无需认证）- 与 kb-web/infra-monitor 保持一致的路径
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
        path: 'services',
        name: 'Services',
        component: () => import('@/views/services/ServicesView.vue'),
        meta: { perm: permCode('menu', 'services') },
      },
      {
        path: 'ports',
        name: 'Ports',
        component: () => import('@/views/ports/PortsView.vue'),
        meta: { perm: permCode('menu', 'ports') },
      },
      {
        path: 'credentials',
        name: 'Credentials',
        component: () => import('@/views/credentials/CredentialsView.vue'),
        meta: { perm: permCode('menu', 'credentials') },
      },
      {
        path: 'domains',
        name: 'Domains',
        component: () => import('@/views/domains/DomainsView.vue'),
        meta: { perm: permCode('menu', 'domains') },
      },
      {
        path: 'dependencies',
        name: 'Dependencies',
        component: () => import('@/views/dependencies/DependenciesView.vue'),
        meta: { perm: permCode('menu', 'dependencies') },
      },
      {
        path: 'deployments',
        name: 'Deployments',
        component: () => import('@/views/deployments/DeploymentsView.vue'),
        meta: { perm: permCode('menu', 'deployments') },
      },
      {
        path: 'conflicts',
        name: 'Conflicts',
        component: () => import('@/views/conflicts/ConflictsView.vue'),
        meta: { perm: permCode('menu', 'conflicts') },
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/KnowledgeView.vue'),
        meta: { perm: permCode('menu', 'knowledge') },
      },
      {
        path: 'import',
        name: 'Import',
        component: () => import('@/views/import/ImportView.vue'),
        meta: { perm: permCode('menu', 'import') },
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/logs/LogsView.vue'),
        meta: { perm: permCode('menu', 'logs') },
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
// 🔴 Phase 13：守卫注册已上移到 `createMarschatApp()`（src/marschat.ts）内部，
//    保证「守卫先于 app.use(router)」这条时序由装配层强制，而不是靠每个应用记得调。
//    本文件不再自行 setupAuthGuard。

export default router
