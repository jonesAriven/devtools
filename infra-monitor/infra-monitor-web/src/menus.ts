/**
 * infra-monitor 菜单定义（Phase 5 · 菜单定义数据化，同 kb-ops 模式）
 *
 * 与将来上报的 menu-registry.yml 严格同构（key/title/order 一致）。
 * dashboard 为基础项 skipPerm（常显）；应用尚未启用菜单上报（configured=false）
 * 时 useMenus 按 R10 全显，行为与改造前一致。
 */
import type { MenuItemDef } from '@marschat/auth-components'

export const INFRA_MENUS: MenuItemDef[] = [
  { key: 'dashboard', title: '总览看板', icon: 'DataAnalysis', path: '/dashboard', order: 1, skipPerm: true },
  { key: 'hosts', title: '主机管理', icon: 'Cpu', path: '/hosts', order: 2 },
  { key: 'credentials', title: '凭据管理', icon: 'Key', path: '/credentials', order: 3 },
  { key: 'configs', title: '配置信息', icon: 'Setting', path: '/configs', order: 4 },
  { key: 'services', title: '服务监控', icon: 'Connection', path: '/services', order: 5 },
  { key: 'users', title: '用户管理', icon: 'UserFilled', path: '/users', order: 6 },
]
