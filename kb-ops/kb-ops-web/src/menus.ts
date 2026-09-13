/**
 * kb-ops 菜单定义（Phase 5 · 菜单定义数据化，2026-09-10 拍板「菜单定义留应用侧」）
 *
 * 与 classpath menu-registry.yml 严格同构（key/title/order/children 一致）——
 * 上报给 auth-center 的权限点与本文件渲染同源，改这里必须同步改 yml（反之亦然）。
 * icon 为 Element Plus 图标组件名，MainLayout 的 ICONS 表解析。
 */
import type { MenuItemDef } from '@marschat/auth-components'

export const KB_OPS_MENUS: MenuItemDef[] = [
  { key: 'dashboard', title: '看板', icon: 'DataAnalysis', path: '/dashboard', order: 1 },
  {
    key: 'resource-group',
    title: '资源管理',
    icon: 'Cpu',
    order: 2,
    children: [
      { key: 'hosts', title: '主机管理', icon: 'Monitor', path: '/hosts', order: 1 },
      { key: 'services', title: '服务管理', icon: 'Connection', path: '/services', order: 2 },
      { key: 'ports', title: '端口管理', icon: 'Position', path: '/ports', order: 3 },
      { key: 'credentials', title: '凭据管理', icon: 'Key', path: '/credentials', order: 4 },
      { key: 'domains', title: '域名管理', icon: 'Link', path: '/domains', order: 5 },
      { key: 'dependencies', title: '依赖管理', icon: 'Box', path: '/dependencies', order: 6 },
    ],
  },
  {
    key: 'deploy-group',
    title: '部署运维',
    icon: 'Upload',
    order: 3,
    children: [
      { key: 'deployments', title: '部署记录', icon: 'List', path: '/deployments', order: 1 },
      { key: 'conflicts', title: '矛盾检测', icon: 'Warning', path: '/conflicts', order: 2 },
    ],
  },
  {
    key: 'system-group',
    title: '系统工具',
    icon: 'Tools',
    order: 4,
    children: [
      { key: 'knowledge', title: '运维知识库', icon: 'Reading', path: '/knowledge', order: 1 },
      { key: 'import', title: '数据导入', icon: 'Download', path: '/import', order: 2 },
      { key: 'logs', title: '操作日志', icon: 'Tickets', path: '/logs', order: 3 },
      { key: 'users', title: '用户管理', icon: 'UserFilled', path: '/users', order: 4 },
    ],
  },
]
