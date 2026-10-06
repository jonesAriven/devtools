// 扫描源码里的「裸包名 import」，逐个验证能否从 node_modules 解析。
// 用途：补足 `pnpm install --frozen-lockfile` 的盲区——
// frozen-lockfile 只校验「lockfile 与 package.json 一致」，
// 不校验「代码 import 的包是否真的装了」。
//
// 它守的是一条**已经出过事**的不变量：三个 app-kit 副本漂移
// （kb-ops / infra-monitor / kb-web 各装一个版本，登录态与权限行为分叉）。
//
// 用法：
//   node depcheck.mjs                 # 扫描全部应用（自动发现 devtools/*/ 与 devtools/*/*/）
//   node depcheck.mjs --app portal    # 只扫名字含 "portal" 的应用
//   node depcheck.mjs --json          # 机器可读输出（供 CI 门禁消费）
// 退出码：0 = 全部可解析；1 = 有缺失；2 = 参数/环境错误
//
// 🔴 跨平台约定（入库时踩过的坑，改动前先读）：
//   ① 仓根定位用 `import.meta.url` + `fileURLToPath`，**不用 `process.cwd()`**
//      —— 后者在「cd 到别处再 node <abs-path>/depcheck.mjs」时会扫错目录；
//   ② `createRequire` 的基准必须是**绝对路径**（传相对路径会 ERR_INVALID_ARG_VALUE 崩栈）；
//   ③ 读版本**不依赖** `import pkg from './package.json'`（pnpm workspace 包常在
//      `exports` 里屏蔽该子路径 ⇒ ERR_PACKAGE_PATH_NOT_EXPORTED），
//      改用 `createRequire` + 回退上溯定位 manifest。

import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import { join, dirname, resolve as pathResolve, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

/** 仓根 = 本文件所在目录（devtools/）。不依赖 cwd。 */
const REPO_ROOT = dirname(fileURLToPath(import.meta.url))

const argv = process.argv.slice(2)
const useJson = argv.includes('--json')
const appFilter = (() => {
  const i = argv.indexOf('--app')
  return i >= 0 && argv[i + 1] ? argv[i + 1].toLowerCase() : null
})()
const explicitRoots = argv.filter((a) => !a.startsWith('--') && a !== appFilter)

/** 应用根目录 = 含 package.json 且有 src/ 的子目录。 */
function discoverAppRoots() {
  if (explicitRoots.length > 0) {
    return explicitRoots.map((p) => pathResolve(process.cwd(), p))
  }
  const roots = []
  const scan = (dir, depth) => {
    if (depth > 2) return
    let entries
    try {
      entries = readdirSync(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const e of entries) {
      if (!e.isDirectory() || e.name === 'node_modules' || e.name.startsWith('.')) continue
      const p = join(dir, e.name)
      if (existsSync(join(p, 'package.json')) && existsSync(join(p, 'src'))) {
        roots.push(p)
        continue // 应用根不再下钻（避免把 src/ 里的子包当应用）
      }
      scan(p, depth + 1)
    }
  }
  scan(REPO_ROOT, 0)
  return roots.sort()
}

const APP_ROOTS = discoverAppRoots().filter((r) => {
  if (!appFilter) return true
  return relative(REPO_ROOT, r).toLowerCase().includes(appFilter)
})

if (APP_ROOTS.length === 0) {
  const msg = appFilter
    ? `未找到名字含 "${appFilter}" 的应用（已扫描 ${REPO_ROOT}）`
    : `未发现任何应用目录（需含 package.json + src/）`
  console.error(useJson ? JSON.stringify({ error: msg }) : `错误：${msg}`)
  process.exit(2)
}

// 🔴 提前校验每个根目录可读 + 基准路径合法，**不要**让 createRequire 在中途崩栈
for (const r of APP_ROOTS) {
  if (!existsSync(r)) {
    const msg = `应用目录不存在: ${r}`
    console.error(useJson ? JSON.stringify({ error: msg }) : `错误：${msg}`)
    process.exit(2)
  }
}

/** 递归收集 src 下所有源码文件。 */
function collect(dir) {
  const out = []
  let entries
  try {
    entries = readdirSync(dir)
  } catch {
    return out
  }
  for (const entry of entries) {
    const p = join(dir, entry)
    let st
    try {
      st = statSync(p)
    } catch {
      continue
    }
    if (st.isDirectory()) out.push(...collect(p))
    else if (/\.(ts|tsx|js|vue)$/.test(entry)) out.push(p)
  }
  return out
}

/** 去掉注释，避免把注释里的示例代码（如 `export { x } from 'mod'`）当成真实 import。 */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '') // 块注释
    .replace(/(^|[^:])\/\/.*$/gm, '$1') // 行注释（保留 http:// 这类 URL）
}

/** 从源码里抽出所有裸包名（相对路径 / @别名 / node: 内置 都跳过）。 */
function bareSpecifiers(files) {
  const found = new Map()
  // 🔴 正则必须锚定「import/from 关键字」且要求引号紧跟其后的**单个**说明符：
  //    早期版本用 `/(?:from|import)\s*['"]([^'"]+)['"]/g`，会把
  //    `request.post('/io/import', formData, {` 这类**调用参数**误判成 import
  //    （实测 infra-monitor 报出包名 `, formData, {`）。
  //    改为：① 关键字前必须是行首/空白/分号/花括号等“非标识符字符”；② 引号内不含空白与逗号。
  const re = /(?<![\w$.])(?:from|import)\s*['"]([^'"\s,{}]+)['"]/g
  for (const file of files) {
    const src = stripComments(readFileSync(file, 'utf8'))
    let m
    while ((m = re.exec(src)) !== null) {
      const spec = m[1]
      if (spec.startsWith('.') || spec.startsWith('@/') || spec.startsWith('/')) continue
      if (spec.startsWith('node:')) continue
      // 合法包名：@scope/name 或 name，允许连字符/点/下划线
      if (!/^(@[a-z0-9-~][a-z0-9-._~]*\/)?[a-z0-9-~][a-z0-9-._~]*$/i.test(spec)) continue
      const parts = spec.split('/')
      const pkg = spec.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0]
      if (!found.has(pkg)) found.set(pkg, new Set())
      found.get(pkg).add(file)
    }
  }
  return found
}

/**
 * 读某个包的实装版本。
 *
 * 注意：现代包常在 `exports` 里屏蔽 `./package.json` 子路径（pnpm workspace 包尤其如此，
 * `@marschat/app-kit` 就是这样），此时 `require.resolve(pkg + '/package.json')` 会抛
 * ERR_PACKAGE_PATH_NOT_EXPORTED。⇒ 不能只依赖 resolve，还要回退到「上溯找 manifest」。
 *
 * 🔴 用 `req.resolve(pkg)`（经 createRequire）而**不是** `import(pkg + '/package.json')`：
 *    后者在 Node ≥12.16 需 `assert { type: 'json' }`，换机器/换 Node 版本就 ERR_REQUIRE_ESM。
 */
function readInstalledVersion(req, pkg) {
  const attempts = [
    () => req(pkg + '/package.json').version,
    () => {
      // 绕开 exports：从入口文件上溯找到包根的 manifest
      const manifest = req.resolve(pkg)
      let dir = dirname(manifest)
      for (let i = 0; i < 5; i += 1) {
        const candidate = join(dir, 'package.json')
        if (existsSync(candidate)) {
          const parsed = JSON.parse(readFileSync(candidate, 'utf8'))
          if (parsed.name === pkg) return parsed.version
        }
        dir = dirname(dir)
      }
      throw new Error('未找到 manifest')
    },
  ]
  for (const attempt of attempts) {
    try {
      const v = attempt()
      if (v) return v
    } catch {
      /* 试下一种方式 */
    }
  }
  return null
}


// ── 主流程：逐应用扫描 ──────────────────────────────────────────────
const report = { apps: [], totalMissing: 0 }

if (!useJson) {
  console.log(`仓根: ${REPO_ROOT}`)
  console.log(`应用: ${APP_ROOTS.length} 个${appFilter ? `（过滤: ${appFilter}）` : ''}\n`)
}

for (const appRoot of APP_ROOTS) {
  const appName = relative(REPO_ROOT, appRoot).split(sep).join('/')
  // 🔴 基准必须是绝对路径 —— 传相对路径会 ERR_INVALID_ARG_VALUE 崩栈
  const req = createRequire(join(appRoot, 'noop.js'))
  const files = collect(join(appRoot, 'src'))
  const bare = bareSpecifiers(files)
  const missingPkgs = []

  const rows = []
  for (const [pkg, users] of [...bare].sort()) {
    // 先确认能否解析（真正决定构建成败的是这一步）
    let resolvable = true
    try {
      req.resolve(pkg)
    } catch {
      resolvable = false
    }
    if (!resolvable) {
      missingPkgs.push(pkg)
      rows.push({ pkg, ok: false, version: null, files: users.size })
      continue
    }
    rows.push({ pkg, ok: true, version: readInstalledVersion(req, pkg), files: users.size })
  }

  report.apps.push({ app: appName, files: files.length, bare: bare.size, missing: missingPkgs, rows })
  report.totalMissing += missingPkgs.length

  if (!useJson) {
    console.log(`── ${appName} ──  扫描 ${files.length} 个源码文件，${bare.size} 个裸包名`)
    for (const r of rows) {
      if (r.ok) {
        console.log(`  ok    ${r.pkg.padEnd(30)} ${r.version ? '@' + r.version : '(版本未知，但可解析)'}`)
      } else {
        console.log(`  MISS  ${r.pkg.padEnd(30)} 被 ${r.files} 个文件引用 —— 无法从 node_modules 解析`)
      }
    }
    console.log('')
  }
}

if (useJson) {
  console.log(JSON.stringify(report, null, 2))
} else {
  const totalBare = report.apps.reduce((n, a) => n + a.bare, 0)
  console.log(`合计: ${report.apps.length} 个应用，${totalBare} 个裸包名，缺失 ${report.totalMissing} 个`)
}

process.exit(report.totalMissing > 0 ? 1 : 0)
