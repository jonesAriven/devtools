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
    // auth-center SSO 回调页（OIDC authorization_code + PKCE）
    path: `/sso-callback`,
    name: 'SsoCallback',
    component: () => import('@/views/sso/SsoCallbackView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: `/share/:code`,
    name: 'ShareAccess',
    component: () => import('@/views/share/ShareAccessView.vue'),
    meta: { requiresAuth: false },
    props: true,
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
        // 工作台为基础项，不做权限管辖
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
      },
      {
        path: 'space/:spaceId',
        name: 'Space',
        component: () => import('@/views/space/SpaceView.vue'),
        props: true,
      },
      {
        path: 'spaces',
        name: 'SpaceManage',
        component: () => import('@/views/space/SpaceManageView.vue'),
        meta: { perm: permCode('menu', 'spaces') },
      },
      {
        path: 'file/:id',
        name: 'FileDetail',
        component: () => import('@/views/file/FileDetailView.vue'),
        props: true,
      },
      {
        path: 'doc/create',
        name: 'DocCreate',
        component: () => import('@/views/doc/DocCreateView.vue'),
      },
      {
        path: 'doc/:id',
        name: 'DocEdit',
        component: () => import('@/views/doc/DocEditView.vue'),
        props: true,
      },
      {
        path: 'web/:id',
        name: 'WebDetail',
        component: () => import('@/views/web/WebDetailView.vue'),
        props: true,
      },
      {
        path: 'stars',
        name: 'Stars',
        component: () => import('@/views/stars/StarsView.vue'),
        meta: { perm: permCode('menu', 'stars') },
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/search/SearchView.vue'),
        meta: { perm: permCode('menu', 'search') },
      },
      {
        path: 'trash',
        name: 'Trash',
        component: () => import('@/views/trash/TrashView.vue'),
        meta: { perm: permCode('menu', 'trash') },
      },
      {
        path: 'tag',
        name: 'TagManage',
        component: () => import('@/views/tag/TagManageView.vue'),
        meta: { perm: permCode('menu', 'tag') },
      },
      {
        path: 'share',
        name: 'ShareList',
        component: () => import('@/views/share/ShareListView.vue'),
        meta: { perm: permCode('menu', 'share') },
      },
      {
        path: 'file',
        name: 'FileList',
        component: () => import('@/views/file/FileListView.vue'),
        meta: { perm: permCode('menu', 'file') },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { perm: permCode('menu', 'settings') },
      },
      {
        // Phase 6 · 统一用户管理（6 应用共用同一份公共组件；菜单项仅管理员可见，
        // 真正的权限闸门在 auth-center AdminUserController 的 @PreAuthorize("hasRole('ADMIN')")）
        path: 'users',
        name: 'Users',
        component: () => import('@/views/settings/UsersView.vue'),
        meta: { perm: permCode('menu', 'users') },
      },
      {
        path: 'log',
        name: 'OperationLog',
        component: () => import('@/views/log/LogView.vue'),
        meta: { perm: permCode('menu', 'log') },
      },
      {
        path: 'graph',
        name: 'Graph',
        component: () => import('@/views/graph/GraphView.vue'),
        meta: { perm: permCode('menu', 'graph') },
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
