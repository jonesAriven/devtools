// Portal 账密配置 浏览器端到端验证（真实 UI 操作；全部真实点击；凭据经接口响应捕获断言）
// 运行： node e2e/portal-creds-ui.mjs
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL || 'http://192.168.31.105'
const results = []
const ok = (n, c, d = '') => { results.push([n, !!c]); console.log(`${c ? '✅' : '❌'} ${n}${c ? '' : ' | ' + d}`) }

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 820 } })
const page = await ctx.newPage()
let lastCredResp = null
ctx.on('response', async r => {
  if (/\/sys\/system\/\d+\/credentials/.test(r.url())) {
    try { lastCredResp = await r.json() } catch { lastCredResp = null }
  }
})

// P1 登录
await page.goto(BASE + '/portal/')
await page.waitForTimeout(2500)
if (page.url().includes('login')) {
  await page.fill('input >> nth=0', 'admin')
  await page.fill('input[type=password]', 'admin123')
  await page.click('button >> nth=0')
  await page.waitForTimeout(2500)
}
ok('P1 登录进入看板', !page.url().includes('login'), page.url())

// P2 管理页新建条目（带账密）；间歇性401弹回登录时自动重登重试
const gotoManage = async () => {
  await page.goto(BASE + '/portal/manage')
  await page.waitForTimeout(2000)
  if (page.url().includes('login')) {
    await page.fill('input >> nth=0', 'admin')
    await page.fill('input[type=password]', 'admin123')
    await page.click('button >> nth=0')
    await page.waitForTimeout(2500)
    await page.goto(BASE + '/portal/manage')
    await page.waitForTimeout(2000)
  }
}
await gotoManage()
await page.click('button:has-text("新增系统")')
await page.waitForTimeout(800)
ok('P2 新建对话框打开', await page.locator('.el-dialog').count() > 0)

const setByLabel = async (label, value) => {
  const item = page.locator('.el-dialog .el-form-item', { has: page.locator(`.el-form-item__label:text-is("${label}")`) })
  await item.locator('input, textarea').first().fill(value)
}
await setByLabel('系统名称', 'QA账密UI测试')
await setByLabel('描述', '浏览器端到端测试条目')
await setByLabel('访问地址', 'http://test.local')
await setByLabel('登录账号', 'qauser')
await setByLabel('登录密码', 'UiPass@123')
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)
const created = await page.locator('.el-table__row', { hasText: 'QA账密UI测试' }).count()
ok('P3 UI 创建成功（列表出现）', created > 0, `rows=${created}`)

// P4 首页卡片 → 账密 → 复制密码 → 捕获接口响应
await page.goto(BASE + '/portal/')
await page.waitForTimeout(2500)
const card = page.locator('.system-card', { hasText: 'QA账密UI测试' }).first()
ok('P4a 首页卡片出现', await card.count() > 0)
lastCredResp = null
await card.locator('button', { hasText: '账密' }).click()
await page.waitForTimeout(600)
await page.click('.el-dropdown-menu__item:has-text("复制密码")')
await page.waitForTimeout(1500)
ok('P4b 写入时查看账密=UiPass@123', lastCredResp?.data?.password === 'UiPass@123', JSON.stringify(lastCredResp))

// P5 编辑：清空密码 → 保存（bug 复现点）
await gotoManage()
await page.locator('.el-table__row', { hasText: 'QA账密UI测试' }).locator('button', { hasText: '编辑' }).click()
await page.waitForTimeout(800)
await page.locator('.el-dialog .el-form-item', { has: page.locator('.el-form-item__label:text-is("登录密码")') }).locator('input').fill('')
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)

// P6 首页再查账密 → bug 复现：旧密码仍在
await page.goto(BASE + '/portal/')
await page.waitForTimeout(2500)
const card2 = page.locator('.system-card', { hasText: 'QA账密UI测试' }).first()
lastCredResp = null
await card2.locator('button', { hasText: '账密' }).click()
await page.waitForTimeout(600)
await page.click('.el-dropdown-menu__item:has-text("复制密码")')
await page.waitForTimeout(1500)
ok('P5 BUG复现：UI清空密码保存后，账密仍是旧密码', lastCredResp?.data?.password === 'UiPass@123', JSON.stringify(lastCredResp))

// P7 清理：UI 删除测试条目
await gotoManage()
await page.locator('.el-table__row', { hasText: 'QA账密UI测试' }).locator('button', { hasText: '删除' }).click()
await page.waitForTimeout(600)
await page.click('.el-message-box button:has-text("确定")')
await page.waitForTimeout(1200)
ok('P7 UI 删除测试条目', (await page.locator('.el-table__row', { hasText: 'QA账密UI测试' }).count()) === 0)

const fails = results.filter(r => !r[1])
console.log(`\nPortal 账密 UI 端到端: ${results.length - fails.length}/${results.length} 通过`)
await browser.close()
process.exit(fails.length ? 1 : 0)
