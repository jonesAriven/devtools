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
      // ===== 知识引擎（kb-intelligence）=====
      {
        path: 'intelligence',
        name: 'IntelligenceDashboard',
        component: () => import('@/views/intelligence/IntelligenceDashboardView.vue'),
      },
      {
        path: 'intelligence/docs',
        name: 'IntelligenceDocs',
        component: () => import('@/views/intelligence/IntelligenceDocsView.vue'),
      },
      {
        path: 'intelligence/docs/:id',
        name: 'IntelligenceDocDetail',
        component: () => import('@/views/intelligence/IntelligenceDocDetailView.vue'),
        props: true,
      },
      {
        path: 'intelligence/entities',
        name: 'IntelligenceEntities',
        component: () => import('@/views/intelligence/IntelligenceEntitiesView.vue'),
      },
    ],
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
