// 抓 UI 编辑清空密码时实际发出的 PUT body
import { chromium } from 'playwright'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 820 } })
let putBody = null
page.on('request', r => {
  if (r.method() === 'PUT' && r.url().includes('/sys/system/')) putBody = r.postData()
})
await page.goto('http://192.168.31.105/portal/manage')
await page.waitForTimeout(1500)
if (page.url().includes('login')) {
  await page.fill('input >> nth=0', 'admin')
  await page.fill('input[type=password]', 'admin123')
  await page.click('button >> nth=0')
  await page.waitForTimeout(2500)
  await page.goto('http://192.168.31.105/portal/manage')
  await page.waitForTimeout(2000)
}
// 建行
await page.click('button:has-text("新增系统")')
await page.waitForTimeout(800)
const setByLabel = async (label, value) => {
  const item = page.locator('.el-dialog .el-form-item', { has: page.locator(`.el-form-item__label:text-is("${label}")`) })
  await item.locator('input, textarea').first().fill(value)
}
await setByLabel('系统名称', 'QA抓包测试')
await setByLabel('描述', 'd')
await setByLabel('访问地址', 'http://t.local')
await setByLabel('登录账号', 'u1')
await setByLabel('登录密码', 'Pass@123')
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)
console.log('行出现:', await page.evaluate(() => document.body.textContent.includes('QA抓包测试')))

// 编辑清空密码
await page.locator('.el-table__row', { hasText: 'QA抓包测试' }).locator('button', { hasText: '编辑' }).click()
await page.waitForTimeout(800)
const pw = page.locator('.el-dialog .el-form-item', { has: page.locator('.el-form-item__label:text-is("登录密码")') }).locator('input')
console.log('清空前输入框值长度:', (await pw.inputValue()).length)
await pw.fill('')
console.log('清空后输入框值:', JSON.stringify(await pw.inputValue()))
await page.click('.el-dialog button:has-text("确定")')
await page.waitForTimeout(1500)
console.log('PUT body:', putBody)
await browser.close()
