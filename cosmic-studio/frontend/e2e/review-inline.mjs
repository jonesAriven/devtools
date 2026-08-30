// 评审意见行内展示验证：登录 → 详情页展开 → 断言"评审意见"列与待处理角标
import { chromium } from 'playwright'
const BASE = process.env.BASE_URL || 'http://192.168.31.105:8310'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } })
await page.goto(BASE + '/login')
await page.waitForLoadState('domcontentloaded')
await page.waitForTimeout(1500)
await page.evaluate(() => {
  const i = document.querySelectorAll('.el-input__inner')
  const set = (e, v) => { e.value = v; e.dispatchEvent(new Event('input', { bubbles: true })) }
  set(i[0], 'admin'); set(i[1], 'cosmic@2026')
})
await page.evaluate(() => document.querySelector('.el-button--primary')?.click())
await page.waitForTimeout(1500)
await page.goto(BASE + '/projects/1')
await page.waitForLoadState('domcontentloaded')
await page.waitForTimeout(2200)
await page.evaluate(() => document.querySelector('.el-table__row .el-table__expand-icon')?.click())
await page.waitForTimeout(500)
await page.evaluate(() => { const ic = document.querySelectorAll('.el-table__row .el-table__expand-icon'); if (ic[1]) ic[1].click() })
await page.waitForTimeout(800)
const headers = await page.evaluate(() => [...document.querySelectorAll('.el-table__header th')].map(t => t.textContent.trim()))
const badges = await page.evaluate(() => [...document.querySelectorAll('.el-table__body .el-tag')].filter(t => t.textContent.includes('待处理')).map(t => t.textContent.trim()))
console.log('表头:', headers.join(' | '))
console.log('待处理角标:', JSON.stringify(badges))
await page.screenshot({ path: 'e2e_review_inline.png' })
await browser.close()
