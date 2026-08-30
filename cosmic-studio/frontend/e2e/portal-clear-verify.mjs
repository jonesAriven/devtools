// 最小闭环验证「清空已存密码」：单页流（登录落在 manage），避开间歇401弹窗
// 建行(带密码) → 编辑 → 勾选清空 → 保存 → 捕获 PUT body 与 credentials 响应断言
import { chromium } from 'playwright'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 820 } })
let putBody = null
let credResp = null
page.on('request', r => { if (r.method() === 'PUT' && r.url().includes('/sys/system/')) putBody = r.postData() })
page.on('response', async r => {
  if (/\/sys\/system\/\d+\/credentials/.test(r.url())) { try { credResp = await r.json() } catch {} }
})

await page.goto('http://192.168.31.105/portal/manage')
await page.waitForTimeout(1500)
if (page.url().includes('login')) {
  await page.fill('input >> nth=0', 'admin')
  await page.fill('input[type=password]', 'admin123')
  await page.click('button >> nth=0')
  await page.waitForTimeout(2500)
}
let ok = (n, c, d = '') => console.log(`${c ? '✅' : '❌'} ${n}${c ? '' : ' | ' + d}`)

// 建行（带密码）
await page.click('button:has-text("新增系统")')
await page.waitForTimeout(800)
const setByLabel = async (label, value) => {
  const item = page.locator('.el-dialog .el-form-item', { has: page.locator(`.el-form-item__label:text-is("${label}")`) })
  await item.locator('input, textarea').first().fill(value)
}
await setByLabel('系统名称', 'QA清空UI验证')
await setByLabel('描述', '清空闭环')
await setByLabel('访问地址', 'http://t.local')
await setByLabel('登录账号', 'u1')
await setByLabel('登录密码', 'ClearMe@123')
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)
ok('V1 创建（带密码）', await page.evaluate(() => document.body.textContent.includes('QA清空UI验证')))

// 编辑 → 勾选「清空已存密码」→ 保存
await page.locator('.el-table__row', { hasText: 'QA清空UI验证' }).first().locator('button', { hasText: '编辑' }).click()
await page.waitForTimeout(800)
await page.click('.el-dialog .el-checkbox:has-text("清空已存密码")')
await page.screenshot({ path: 'e2e_portal_clear_checkbox.png' })
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)
ok('V2 PUT body loginPassword=""（显式清空意图）', putBody?.includes('"loginPassword":""'), putBody?.slice(-120))

// 页面内直接调查看账密接口（同 UI 按钮的数据源）断言已清空
const cred = await page.evaluate(async () => {
  const id = [...document.querySelectorAll('.el-table__row')].find(r => r.textContent.includes('QA清空UI验证'))
    ?.querySelector('.el-button')?.getAttribute('') // 占位
  return null
})
// 从行内按钮不可靠，改为通过 UI 卡片页验证——但避免导航，直接读列表行数据不可行；
// 用页面上下文 fetch（带 localStorage token，等价 UI 数据源）
const out = await page.evaluate(async () => {
  const t = localStorage.getItem('portal_token')
  const list = await fetch('/portal/api/sys/system/list?page=1&size=50', { headers: { Authorization: 'Bearer ' + t } }).then(r => r.json())
  const row = (list.data?.list || []).find(x => x.name === 'QA清空UI验证')
  const cred = await fetch(`/portal/api/sys/system/${row.id}/credentials`, { headers: { Authorization: 'Bearer ' + t } }).then(r => r.json())
  return { dbPassword: row.loginPassword, credPassword: cred.data?.password }
})
ok('V3 清空保存后：列表密码字段=null 且 查看账密=空', out.dbPassword === null && !out.credPassword, JSON.stringify(out))
await page.screenshot({ path: 'e2e_portal_clear_done.png' })

// 清理（软删）
await page.locator('.el-table__row', { hasText: 'QA清空UI验证' }).first().locator('button', { hasText: '删除' }).click()
await page.waitForTimeout(500)
await page.click('.el-message-box button:has-text("确定")')
await page.waitForTimeout(1000)
console.log('已清理测试行')
await browser.close()
