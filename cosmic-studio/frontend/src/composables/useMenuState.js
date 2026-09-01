// 各顶层菜单「上次停留的子路由」记忆。
// 例：编写库上次打开的项目详情 /projects/1、归档库上次打开的 /archive/123。
// 模块级单例：Layout 在路由变化时写入，菜单/页签点击时读取；Layout 不卸载故常驻内存。
import { navLog } from '../utils/navLog'

export const lastSubRoute = {}

// 记录某菜单路径当前停留的子路由（菜单根路径或详情子路由都记）
export function rememberSubRoute(menuPath, subPath) {
  const prev = lastSubRoute[menuPath]
  lastSubRoute[menuPath] = subPath
  if (prev !== subPath) {
    navLog('memory-set', { menu: menuPath, from: prev || '(none)', to: subPath })
  }
}

// 取某菜单点击时应回到的路径：有记忆回记忆，否则回菜单根
export function getSubRoute(menuPath) {
  const target = lastSubRoute[menuPath] || menuPath
  navLog('memory-get', { menu: menuPath, target, hit: !!lastSubRoute[menuPath] })
  return target
}

/** 记忆快照，供日志打印（浅拷贝，避免日志里看到的是后续被改过的值） */
export function memorySnapshot() {
  return { ...lastSubRoute }
}
