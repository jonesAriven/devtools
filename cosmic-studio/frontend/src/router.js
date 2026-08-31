import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  {
    path: '/', component: () => import('./Layout.vue'),
    children: [
      { path: '', component: () => import('./views/Chat.vue') },
      { path: 'projects', component: () => import('./views/Projects.vue') },
      { path: 'projects/:id', component: () => import('./views/ProjectDetail.vue') },
      { path: 'archive', component: () => import('./views/Archive.vue') },
      { path: 'archive/:id', component: () => import('./views/ArchiveDetail.vue') },
      { path: 'lint', component: () => import('./views/Lint.vue') },
      { path: 'versions', component: () => import('./views/Versions.vue') },
      { path: 'specs', component: () => import('./views/Specs.vue') },
      { path: 'vocab', component: () => import('./views/Vocab.vue') },
      { path: 'admin', component: () => import('./views/Admin.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.path !== '/login' && !localStorage.getItem('token')) next('/login')
  else next()
})

export default router
