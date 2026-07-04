import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/token'
import MainLayout from '@/layouts/MainLayout.vue'
import { CONTEXT_PATH as ctx } from '@/config'

const routes: RouteRecordRaw[] = [
  {
    path: `/login`,
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
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
      },
      {
        path: 'services',
        name: 'Services',
        component: () => import('@/views/services/ServicesView.vue'),
      },
      {
        path: 'ports',
        name: 'Ports',
        component: () => import('@/views/ports/PortsView.vue'),
      },
      {
        path: 'credentials',
        name: 'Credentials',
        component: () => import('@/views/credentials/CredentialsView.vue'),
      },
      {
        path: 'domains',
        name: 'Domains',
        component: () => import('@/views/domains/DomainsView.vue'),
      },
      {
        path: 'dependencies',
        name: 'Dependencies',
        component: () => import('@/views/dependencies/DependenciesView.vue'),
      },
      {
        path: 'deployments',
        name: 'Deployments',
        component: () => import('@/views/deployments/DeploymentsView.vue'),
      },
      {
        path: 'conflicts',
        name: 'Conflicts',
        component: () => import('@/views/conflicts/ConflictsView.vue'),
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/KnowledgeView.vue'),
      },
      {
        path: 'import',
        name: 'Import',
        component: () => import('@/views/import/ImportView.vue'),
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/logs/LogsView.vue'),
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

export default router
