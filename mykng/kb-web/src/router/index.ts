import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/token'
import MainLayout from '@/layouts/MainLayout.vue'
import ShareLayout from '@/layouts/ShareLayout.vue'
import { CONTEXT_PATH as ctx } from '@/config'

const routes: RouteRecordRaw[] = [
  {
    path: `/login`,
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
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
        path: 'tag',
        name: 'TagManage',
        component: () => import('@/views/tag/TagManageView.vue'),
      },
      {
        path: 'share',
        name: 'ShareList',
        component: () => import('@/views/share/ShareListView.vue'),
      },
      {
        path: 'file',
        name: 'FileList',
        component: () => import('@/views/file/FileListView.vue'),
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
      },
      {
        path: 'ops',
        name: 'OpsDashboard',
        component: () => import('@/views/ops/OpsDashboardView.vue'),
      },
      {
        path: 'ops/hosts',
        name: 'OpsHosts',
        component: () => import('@/views/ops/OpsHostsView.vue'),
      },
      {
        path: 'ops/services',
        name: 'OpsServices',
        component: () => import('@/views/ops/OpsServicesView.vue'),
      },
      {
        path: 'ops/conflicts',
        name: 'OpsConflicts',
        component: () => import('@/views/ops/OpsConflictsView.vue'),
      },
      {
        path: 'ops/knowledge',
        name: 'OpsKnowledge',
        component: () => import('@/views/ops/OpsKnowledgeView.vue'),
      },
      {
        path: 'ops/log',
        name: 'OpsLog',
        component: () => import('@/views/ops/OpsLogView.vue'),
      },
      {
        path: 'intel',
        name: 'IntelDashboard',
        component: () => import('@/views/intel/IntelDashboardView.vue'),
      },
      {
        path: 'intel/docs',
        name: 'IntelDocs',
        component: () => import('@/views/intel/IntelDocsView.vue'),
      },
      {
        path: 'intel/hosts',
        name: 'IntelHosts',
        component: () => import('@/views/intel/IntelHostsView.vue'),
      },
      {
        path: 'intel/services',
        name: 'IntelServices',
        component: () => import('@/views/intel/IntelServicesView.vue'),
      },
      {
        path: 'intel/ports',
        name: 'IntelPorts',
        component: () => import('@/views/intel/IntelPortsView.vue'),
      },
      {
        path: 'intel/credentials',
        name: 'IntelCredentials',
        component: () => import('@/views/intel/IntelCredentialsView.vue'),
      },
      {
        path: 'intel/domains',
        name: 'IntelDomains',
        component: () => import('@/views/intel/IntelDomainsView.vue'),
      },
      {
        path: 'intel/commands',
        name: 'IntelCommands',
        component: () => import('@/views/intel/IntelCommandsView.vue'),
      },
      {
        path: 'intel/timelines',
        name: 'IntelTimelines',
        component: () => import('@/views/intel/IntelTimelinesView.vue'),
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
