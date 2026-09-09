import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  // SSO 回调路由（无需认证）
  {
    path: '/sso-callback',
    name: 'SsoCallback',
    component: () => import('../views/SsoCallback.vue')
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/servers',
    children: [
      {
        path: 'servers',
        name: 'Servers',
        component: () => import('../views/ServerList.vue'),
        meta: { title: '服务端管理' }
      },
      {
        path: 'clients',
        name: 'Clients',
        component: () => import('../views/ClientList.vue'),
        meta: { title: '客户端管理' }
      },
      {
        path: 'tunnels',
        name: 'Tunnels',
        component: () => import('../views/TunnelList.vue'),
        meta: { title: '隧道管理' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/UserList.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        path: 'preview/:type/:id',
        name: 'Preview',
        component: () => import('../views/ConfigPreview.vue'),
        meta: { title: '配置预览' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  // 登录页和 SSO 回调页不需要认证
  if (to.name !== 'Login' && to.name !== 'SsoCallback' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
