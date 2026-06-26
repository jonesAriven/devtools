import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { storage } from '@/utils/storage'

const Layout = () => import('@/layouts/MainLayout.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noAuth: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'space', component: () => import('@/views/SpaceList.vue'), meta: { title: '知识空间' } },
      { path: 'space/:id', component: () => import('@/views/SpaceDetail.vue'), meta: { title: '空间详情' } },
      { path: 'doc/:id', component: () => import('@/views/DocEditor.vue'), meta: { title: '文档编辑' } },
      { path: 'search', component: () => import('@/views/Search.vue'), meta: { title: '搜索' } },
      { path: 'tag', component: () => import('@/views/TagManage.vue'), meta: { title: '标签' } },
      { path: 'share', component: () => import('@/views/ShareList.vue'), meta: { title: '分享' } },
      { path: 'trash', component: () => import('@/views/Trash.vue'), meta: { title: '回收站' } },
      { path: 'file', component: () => import('@/views/FileManager.vue'), meta: { title: '文件' } },
      { path: 'ops/host', component: () => import('@/views/ops/Host.vue'), meta: { title: '主机' } },
      { path: 'ops/service', component: () => import('@/views/ops/Service.vue'), meta: { title: '服务' } },
      { path: 'ops/dashboard', component: () => import('@/views/ops/Dashboard.vue'), meta: { title: '看板' } },
      { path: 'ops/log', component: () => import('@/views/ops/Log.vue'), meta: { title: '日志' } },
      { path: 'settings', component: () => import('@/views/Settings.vue'), meta: { title: '设置' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  // dev 模式下 Vite base 为 /kb/s/，路由 base 需与之匹配；prod 模式下 Nginx 页面路由在 /kb/
  history: createWebHistory(import.meta.env.DEV ? '/kb/s/' : '/kb/'),
  routes,
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || ''} - mykng 知识库`
  const token = storage.get<string>('accessToken')
  if (!to.meta.noAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
