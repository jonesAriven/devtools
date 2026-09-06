import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

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
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue')
      },
      {
        path: 'manage',
        name: 'Manage',
        component: () => import('@/views/ManageView.vue')
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/UsersView.vue'),
        meta: { requiresAdmin: true }
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

export default router
