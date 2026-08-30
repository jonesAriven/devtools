// Portal 账密复制功能 浏览器端到端验证（真实 UI 点击；hook execCommand 捕获复制内容；抓 toast）
// C1 已配置密码：复制账号/复制密码 → execCommand 收到正确文本 + 成功 toast
// C2 未配置密码（cosmic-studio 条目，密码 NULL）：复制账号 → 'admin' ✅；复制密码 → 静默无反应（bug 复现点）
// 运行： node e2e/portal-copy-ui.mjs
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL || 'http://192.168.31.105'
const results = []
const ok = (n, c, d = '') => { results.push([n, !!c]); console.log(`${c ? '✅' : '❌'} ${n}${c ? '' : ' | ' + d}`) }

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 820 } })

// 登录
await page.goto(BASE + '/portal/')
await page.waitForTimeout(2500)
if (page.url().includes('login')) {
  await page.fill('input >> nth=0', 'admin')
  await page.fill('input[type=password]', 'admin123')
  await page.click('button >> nth=0')
  await page.waitForTimeout(2500)
}
ok('C0 登录看板', !page.url().includes('login'), page.url())

// hook execCommand：捕获每次复制调用的文本（textarea 选中的值）；导航后必须重挂
const hookCopy = () => page.evaluate(() => {
  window.__copyLog = []
  const orig = document.execCommand.bind(document)
  document.execCommand = function (cmd) {
    const v = document.activeElement?.tagName === 'TEXTAREA' ? document.activeElement.value : null
    window.__copyLog.push({ cmd, text: v })
    return orig.apply(document, arguments)
  }
})
const clearToasts = () => page.evaluate(() => [...document.querySelectorAll('.el-message')].forEach(m => m.remove()))
const hookAndClear = async () => { await hookCopy(); await clearToasts() }
const getToasts = () => page.evaluate(() => [...document.querySelectorAll('.el-message')].map(m => m.textContent.trim()))
await hookAndClear()

const openCredMenu = async (cardName, item) => {
  const card = page.locator('.system-card', { hasText: cardName }).first()
  await card.locator('button', { hasText: '账密' }).click()
  await page.waitForTimeout(600)
  await page.click(`.el-dropdown-menu__item:has-text("${item}") >> visible=true`)
  await page.waitForTimeout(1000)
}

// C1a cosmic-studio（账号 admin，密码未配置）：复制账号
await page.evaluate(() => { window.__copyLog = [] })
await hookAndClear()
await openCredMenu('cosmic-studio', '复制账号')
let log = await page.evaluate(() => window.__copyLog)
let toasts = await getToasts()
ok('C1a cosmic-studio 复制账号 → 复制了 admin + 成功提示',
   log.some(x => x.text === 'admin') && toasts.some(t => t.includes('账号已复制')), JSON.stringify({ log, toasts }))

// C1b cosmic-studio 复制密码（密码 NULL）→ 预期 bug：静默无反应
await page.evaluate(() => { window.__copyLog = [] })
await clearToasts()
await openCredMenu('cosmic-studio', '复制密码')
log = await page.evaluate(() => window.__copyLog)
toasts = await getToasts()
const silent = log.length === 0 && toasts.length === 0
ok('C1b BUG复现：未配置密码时复制密码 → 静默无反应（无提示无复制）', silent, JSON.stringify({ log, toasts }))
await page.screenshot({ path: 'e2e_portal_copy_silent.png' })

// C2 已配置密码的条目：复制账号/复制密码都应正常
// 走管理页建一条带密码的
await page.goto(BASE + '/portal/manage')
await page.waitForTimeout(2000)
await page.click('button:has-text("新增系统")')
await page.waitForTimeout(800)
const setByLabel = async (label, value) => {
  const item = page.locator('.el-dialog .el-form-item', { has: page.locator(`.el-form-item__label:text-is("${label}")`) })
  await item.locator('input, textarea').first().fill(value)
}
await setByLabel('系统名称', 'QA复制功能测试')
await setByLabel('描述', '复制功能E2E')
await setByLabel('访问地址', 'http://test.local')
await setByLabel('登录账号', 'copyuser')
await setByLabel('登录密码', 'CopyPass@456')
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)

await page.goto(BASE + '/portal/')
await page.waitForTimeout(2500)
await hookAndClear()
await openCredMenu('QA复制功能测试', '复制账号')
log = await page.evaluate(() => window.__copyLog)
toasts = await getToasts()
ok('C2a 配置后复制账号 → copyuser + 提示', log.some(x => x.text === 'copyuser') && toasts.some(t => t.includes('账号已复制')), JSON.stringify({ log, toasts }))

await clearToasts()
await openCredMenu('QA复制功能测试', '复制密码')
log = await page.evaluate(() => window.__copyLog)
toasts = await getToasts()
ok('C2b 配置后复制密码 → CopyPass@456 + 提示', log.some(x => x.text === 'CopyPass@456') && toasts.some(t => t.includes('密码已复制')), JSON.stringify({ log, toasts }))

// C3 快速登录（复制 user+pass 组合并打开新页）
await clearToasts()
await openCredMenu('QA复制功能测试', '快速登录')
log = await page.evaluate(() => window.__copyLog)
ok('C3 快速登录 → 复制 "user\\tpassword" 组合', log.some(x => x.text === 'copyuser\tCopyPass@456'), JSON.stringify(log))
await page.evaluate(() => { [...document.querySelectorAll('.el-message')].forEach(m => m.remove()) })

// 清理
await page.goto(BASE + '/portal/manage')
await page.waitForTimeout(2000)
await page.locator('.el-table__row', { hasText: 'QA复制功能测试' }).locator('button', { hasText: '删除' }).click()
await page.waitForTimeout(600)
await page.click('.el-message-box button:has-text("确定")')
await page.waitForTimeout(1000)

const fails = results.filter(r => !r[1])
console.log(`\nPortal 复制功能 UI E2E: ${results.length - fails.length}/${results.length} 通过`)
await browser.close()
process.exit(fails.length ? 1 : 0)
