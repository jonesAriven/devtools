// 各顶层菜单「上次停留的子路由」记忆。
// 例：编写库上次打开的项目详情 /projects/1、归档库上次打开的 /archive/123。
// 模块级单例：Layout 在路由变化时写入，菜单点击时读取；Layout 不卸载故常驻内存。
export const lastSubRoute = {}

// 记录某菜单路径当前停留的子路由（菜单根路径或详情子路由都记）
export function rememberSubRoute(menuPath, subPath) {
  lastSubRoute[menuPath] = subPath
}

// 取某菜单点击时应回到的路径：有记忆回记忆，否则回菜单根
export function getSubRoute(menuPath) {
  return lastSubRoute[menuPath] || menuPath
}
