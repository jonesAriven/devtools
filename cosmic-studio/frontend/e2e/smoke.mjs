/**
 * cosmic-studio UI 冒烟测试（Playwright，无头 Chromium）
 * 三条主干路径：登录流 / 建项目+清理 / 项目详情门禁弹窗 + 全菜单渲染
 * 用法： BASE_URL=http://192.168.31.105:8310 ADMIN_PASS=xxx node e2e/smoke.mjs
 * 退出码 0=全过 1=有失败，可入 CI。
 */
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL || 'http://192.168.31.105:8310'
const USER = process.env.ADMIN_USER || 'admin'
const PASS = process.env.ADMIN_PASS || 'cosmic@2026'
const results = []
const ok = (name, cond, detail = '') => {
  results.push({ name, pass: !!cond, detail: String(detail).slice(0, 120) })
  console.log(`${cond ? '✅' : '❌'} ${name}${cond ? '' : ' | ' + detail}`)
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const api = async (path, opts = {}) => {
  const token = await page.evaluate(() => localStorage.getItem('token'))
  const r = await fetch(BASE + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...(opts.headers || {}) }
  })
  return { status: r.status, data: await r.json().catch(() => ({})) }
}

try {
  // ── 用例1：登录流 ──
  await page.goto(BASE + '/login')
  await page.waitForLoadState('domcontentloaded')
  await page.locator('input').first().fill(USER)
  await page.locator('input[type=password]').fill('wrong-pass')
  await page.evaluate(() => document.querySelector('.el-button--primary')?.click())
  await page.waitForTimeout(1200)
  const errToast = await page.evaluate(() => document.querySelector('.el-message')?.textContent || '')
  ok('U1.1 错误密码有明确提示', errToast.includes('用户名或密码错误'), errToast)

  await page.locator('input[type=password]').fill(PASS)
  await page.evaluate(() => document.querySelector('.el-button--primary')?.click())
  await page.waitForURL('**/', { timeout: 8000 }).catch(() => {})
  await page.waitForTimeout(1200)
  ok('U1.2 正确登录进入工作台', !page.url().includes('/login'), page.url())
  const menuCount = await page.locator('.el-menu-item').count()
  ok('U1.3 侧边菜单渲染', menuCount >= 8, `menuItems=${menuCount}`)

  // ── 用例2：新建项目 → 列表出现 → API 清理 ──
  const reqId = 'SMOKE-' + Date.now()
  await page.goto(BASE + '/projects')
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.includes('新建项目'))?.click() })
  await page.waitForTimeout(600)
  const dialogOpen = await page.locator('.el-dialog').count()
  ok('U2.1 新建项目对话框打开', dialogOpen === 1, `dialogs=${dialogOpen}`)
  const inputs = page.locator('.el-dialog input')
  await inputs.nth(0).fill(reqId)
  await inputs.nth(1).fill('冒烟测试项目（自动清理）')
  await page.evaluate(() => { [...document.querySelectorAll('.el-dialog button')].find(b => b.textContent.includes('创建'))?.click() })
  await page.waitForTimeout(1500)
  const listHasNew = await page.evaluate((rid) => document.body.textContent.includes(rid), reqId)
  ok('U2.2 项目创建并出现在列表', listHasNew, reqId)

  // 空表单校验：对话框内必填拦截
  await page.waitForTimeout(2500)  // 等上一条 toast 消失，避免堆叠误读
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.includes('新建项目'))?.click() })
  await page.waitForTimeout(600)
  await page.evaluate(() => {  // 清空全部输入，构造真·空表单
    document.querySelectorAll('.el-dialog input').forEach(i => {
      i.value = ''
      i.dispatchEvent(new Event('input', { bubbles: true }))
    })
  })
  await page.evaluate(() => { [...document.querySelectorAll('.el-dialog button')].find(b => b.textContent.includes('创建'))?.click() })
  await page.waitForTimeout(1200)
  const toasts = await page.evaluate(() => [...document.querySelectorAll('.el-message')].map(m => m.textContent).join('|'))
  const dlgStill = await page.locator('.el-dialog').count()
  ok('U2.3 空表单被拦截且对话框不关', toasts.includes('必填') && dlgStill === 1, `toast=${toasts} dlg=${dlgStill}`)
  await page.evaluate(() => [...document.querySelectorAll('.el-dialog button')].find(b => b.textContent.includes('取消'))?.click())

  // 清理
  const projects = await api('/api/active/projects')
  const target = (projects.data || []).find(p => p.requirement_id === reqId)
  if (target) {
    const del = await api(`/api/active/projects/${target.id}?confirm=active`, { method: 'DELETE' })
    ok('U2.4 测试项目清理', del.status === 200, del.status)
  } else {
    ok('U2.4 测试项目清理', false, '未找到冒烟项目')
  }

  // ── 用例3：项目详情 + 门禁弹窗 ──
  await page.goto(BASE + '/projects/1')
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1800)
  const treeRows = await page.locator('.el-table__row').count()
  ok('U3.1 项目详情结构树渲染', treeRows > 0, `rows=${treeRows}`)
  const hasBreadcrumb = await page.evaluate(() => !!document.querySelector('.el-breadcrumb'))
  ok('U3.2 面包屑（你在哪）', hasBreadcrumb)
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.includes('质量门禁'))?.click() })
  // lint 全量计算需数秒，轮询等待弹窗出现（最多 15 秒）
  let dlgTitle = '', alertText = ''
  for (let i = 0; i < 15; i++) {
    await page.waitForTimeout(1000)
    const s = await page.evaluate(() => ({
      title: document.querySelector('.el-dialog__title')?.textContent || '',
      alert: document.querySelector('.el-dialog .el-alert__title')?.textContent || ''
    }))
    if (s.title.includes('质量门禁')) { dlgTitle = s.title; alertText = s.alert; break }
  }
  ok('U3.3 门禁报告弹窗带统计', dlgTitle.includes('质量门禁') && /错误 \d+/.test(alertText), `${dlgTitle} | ${alertText}`)

  // ── 用例4：全菜单路由渲染（app 内长度>1000 视为渲染成功）──
  const routes = [['/archive', '归档'], ['/lint', '门禁'], ['/versions', '版本'], ['/specs', '规范'], ['/vocab', '词库'], ['/admin', '管理']]
  for (const [path] of routes) {
    await page.goto(BASE + path)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(1100)
    const len = await page.evaluate(() => document.getElementById('app')?.innerHTML.length || 0)
    ok(`U4 ${path} 渲染`, len > 1000, `len=${len}`)
  }
} catch (e) {
  ok('执行异常中断', false, e.message)
} finally {
  await browser.close()
}

const fails = results.filter(r => !r.pass)
console.log(`\nUI 冒烟汇总: ${results.length - fails.length}/${results.length} 通过`)
process.exit(fails.length ? 1 : 0)
