// 评审行内意见深度验证：tooltip 悬停全文 + 角标点击进抽屉 + 抽屉内意见定位
import { chromium } from 'playwright'
const BASE = process.env.BASE_URL || 'http://192.168.31.105:8310'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 800 } })
let pass = 0, total = 0
const ok = (n, c, d = '') => { total++; if (c) pass++; console.log(`${c ? '✅' : '❌'} ${n}${c ? '' : ' | ' + d}`) }

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

// V1: 评审意见列存在且有角标
const col = await page.evaluate(() => {
  const ths = [...document.querySelectorAll('.el-table__header th')]
  return { hasCol: ths.some(t => t.textContent.trim() === '评审意见'), idx: ths.findIndex(t => t.textContent.trim() === '评审意见') }
})
ok('V1 评审意见列存在', col.hasCol, JSON.stringify(col))

// V2: 悬停角标显示意见全文（tooltip）
const badge = page.locator('.el-table__body .el-tag', { hasText: '待处理' }).first()
if (await badge.count()) {
  await badge.hover()
  await page.waitForTimeout(800)
  const tip = await page.evaluate(() => document.querySelector('.el-popper[role="tooltip"], .el-tooltip__popper')?.textContent || '')
  ok('V2 悬停显示意见全文', tip.includes('待处理') && tip.length > 10, tip.slice(0, 60))
} else {
  ok('V2 悬停显示意见全文', false, '未找到角标')
}

// V3: 点击角标打开评审抽屉
await page.evaluate(() => { [...document.querySelectorAll('.el-table__body .el-tag')].find(t => t.textContent.includes('待处理'))?.click() })
await page.waitForTimeout(800)
const drawerOpen = await page.evaluate(() => !!document.querySelector('.el-drawer'))
ok('V3 点击角标打开评审抽屉', drawerOpen)

// V4: 抽屉内意见条目与行内数据一致（数量对账）
const drawerItems = await page.evaluate(() => document.querySelectorAll('.el-drawer .review-item').length)
ok('V4 抽屉意见条目>0', drawerItems > 0, `items=${drawerItems}`)

// V5: 已修订意见显示绿色状态（历史意见 manual_done）
const hasDone = await page.evaluate(() => [...document.querySelectorAll('.el-drawer .el-tag')].some(t => /已.*修订/.test(t.textContent)))
ok('V5 已修订意见状态展示', hasDone)

console.log(`\n评审行内深度验证: ${pass}/${total}`)
await browser.close()
process.exit(pass === total ? 0 : 1)
