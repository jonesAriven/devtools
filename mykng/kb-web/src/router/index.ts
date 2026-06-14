import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/token'
import MainLayout from '@/layouts/MainLayout.vue'
import ShareLayout from '@/layouts/ShareLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/kb/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/kb/share/:code',
    name: 'ShareAccess',
    component: () => import('@/views/share/ShareAccessView.vue'),
    meta: { requiresAuth: false },
    props: true,
  },
  {
    path: '/kb',
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/kb/dashboard',
      },
      {
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
        path: 'search',
        name: 'Search',
        component: () => import('@/views/search/SearchView.vue'),
      },
      {
        path: 'trash',
        name: 'Trash',
        component: () => import('@/views/trash/TrashView.vue'),
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
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

export default router
