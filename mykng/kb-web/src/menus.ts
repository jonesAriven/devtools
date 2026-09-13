/**
 * kb-web 菜单定义（Phase 5 · 菜单定义数据化，同 kb-ops/infra 模式）
 *
 * 与 router meta.perm 的 11 个权限点严格对齐（子代理调研 §25.3）；
 * dashboard 基础项 skipPerm；运行时条件（模块健康度/当前空间/isAdmin）
 * 通过工厂函数注入 store 闭包（visibleFn/disabledFn/pathFn）。
 */
import type { MenuItemDef } from '@marschat/auth-components'

interface KbMenuCtx {
  /** 当前空间是否存在（spaceStore.currentSpace） */
  hasCurrentSpace: () => boolean
  currentSpacePath: () => string
  /** 模块健康度：kb-knowledge / kb-file / auth-center */
  kbKnowledgeAvailable: () => boolean
  kbKnowledgeReason: () => string
  kbFileAvailable: () => boolean
  kbFileReason: () => string
  authCenterAvailable: () => boolean
  authCenterReason: () => string
  isAdmin: () => boolean
}

const kbDisabled = (ctx: KbMenuCtx) => !ctx.kbKnowledgeAvailable()
const kbReason = (ctx: KbMenuCtx) => ctx.kbKnowledgeReason()

export function createKbMenus(ctx: KbMenuCtx): MenuItemDef[] {
  return [
    { key: 'dashboard', title: '工作台', icon: 'Grid', path: '/dashboard', order: 1, skipPerm: true },
    {
      key: 'kb-group',
      title: '知识库',
      icon: 'FolderOpened',
      order: 2,
      hidden: true,
      children: [
        {
          key: 'spaces', title: '知识空间', icon: 'List', path: '/spaces', order: 1,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'current-space', title: '当前空间', icon: 'FolderOpened', order: 2,
          pathFn: ctx.currentSpacePath,
          visibleFn: ctx.hasCurrentSpace,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'stars', title: '我的收藏', icon: 'Star', path: '/stars', order: 3,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'search', title: '搜索', icon: 'Search', path: '/search', order: 4,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'file', title: '文件', icon: 'Document', path: '/file', order: 5,
          disabledFn: () => !ctx.kbFileAvailable(),
          disabledReasonFn: ctx.kbFileReason,
        },
        {
          key: 'tag', title: '标签', icon: 'PriceTag', path: '/tag', order: 6,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'share', title: '分享', icon: 'Share', path: '/share', order: 7,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'trash', title: '回收站', icon: 'Delete', path: '/trash', order: 8,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
        {
          key: 'graph', title: '知识图谱', icon: 'Connection', path: '/graph', order: 9,
          disabledFn: kbDisabled(ctx), disabledReasonFn: kbReason(ctx),
        },
      ],
    },
    {
      key: 'system-group',
      title: '系统',
      icon: 'Setting',
      order: 3,
      hidden: true,
      children: [
        {
          key: 'log', title: '操作日志', icon: 'Tickets', path: '/log', order: 1,
          disabledFn: () => !ctx.authCenterAvailable(),
          disabledReasonFn: ctx.authCenterReason,
        },
        { key: 'users', title: '用户管理', icon: 'UserFilled', path: '/users', order: 2, visibleFn: ctx.isAdmin },
        { key: 'settings', title: '设置', icon: 'Setting', path: '/settings', order: 3, skipPerm: true },
      ],
    },
  ]
}
