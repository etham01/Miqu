/**
 * Miqu 前端真实浏览器验证。
 *
 * 用本机已安装的 Chrome（playwright channel: 'chrome'）无头打开前端 dev server，
 * 真实走一遍「登录 → 首页 → 私聊 → 通知 → 搜索 → 个人中心」，并收集
 * 控制台报错与未捕获异常 —— 这两样正是"白屏 / 按钮点了没反应"的根因信号。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node browser_check.mjs
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'

// 解析 playwright：优先用项目里装的，其次是本机已有的独立安装。
// 这是一个**可选**的前端走查工具，不参与 pytest，也不进 requirements。
function loadPlaywright() {
  const candidates = [
    'playwright',                 // 项目 node_modules / 全局
    'D:/tools/playwright/',       // 本机既有安装（CI 上通常没有）
    'C:/tools/playwright/',
  ]
  const errors = []
  for (const base of candidates) {
    try {
      return createRequire(base.endsWith('/') ? base : base + '/')('playwright')
    } catch (err) {
      errors.push(`${base}: ${err.code || err.message}`)
    }
  }
  console.error('无法加载 playwright。安装方式：npm i -D playwright && npx playwright install chromium')
  console.error('尝试过的位置：\n  ' + errors.join('\n  '))
  process.exit(2)
}

const { chromium } = loadPlaywright()

const BASE = process.env.MIQU_FRONT_URL || 'http://127.0.0.1:5173'
const OUT = '.workbuddy/screenshots'
mkdirSync(OUT, { recursive: true })

const results = []
const consoleErrors = []
const pageErrors = []

function check(step, ok, detail = '') {
  results.push({ step, ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${step}${detail ? '  ->  ' + detail : ''}`)
}

const browser = await chromium.launch({
  channel: 'chrome',
  headless: true,
  args: ['--no-proxy-server', '--disable-dev-shm-usage'],
})

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const loc = msg.location()?.url || ''
      consoleErrors.push(loc ? `${msg.text()} @ ${loc}` : msg.text())
    }
  })
  page.on('pageerror', (err) => pageErrors.push(String(err)))

  // ---------- 1. 登录页 ----------
  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 15000 })
  await page.screenshot({ path: `${OUT}/01-login.png` })
  check('登录页渲染（无白屏）', true, await page.title())

  const hasDemo = await page.locator('text=演示账号').count()
  check('登录页存在「演示账号」快捷入口', hasDemo > 0)

  // ---------- 2. 登录 ----------
  await page.fill('input[placeholder="请输入用户名"]', 'test001')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.click('button.auth__submit')
  await page.waitForURL(/localhost:5173\/(search|notifications|messages|profile)?$|127\.0\.0\.1:5173\/?$/, { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(2500)
  await page.screenshot({ path: `${OUT}/02-home.png` })

  const url = page.url()
  check('登录后跳转离开 /login', !url.includes('/login'), url)

  const bodyText = await page.locator('body').innerText()
  check('首页有实际内容（不是空壳）', bodyText.trim().length > 100, `${bodyText.trim().length} chars`)
  check('首页出现动态流文本', /动态|发布|关注/.test(bodyText), bodyText.replace(/\s+/g, ' ').slice(0, 80))

  // ---------- 3. 私聊页 ----------
  await page.goto(`${BASE}/messages`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(2000)
  await page.screenshot({ path: `${OUT}/03-messages.png` })
  const msgText = await page.locator('body').innerText()
  check('私聊页渲染', msgText.trim().length > 50, msgText.replace(/\s+/g, ' ').slice(0, 80))

  // ---------- 4. 通知页 ----------
  await page.goto(`${BASE}/notifications`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(2000)
  await page.screenshot({ path: `${OUT}/04-notifications.png` })
  const notifyText = await page.locator('body').innerText()
  check('通知页渲染', notifyText.trim().length > 50, notifyText.replace(/\s+/g, ' ').slice(0, 80))

  // ---------- 5. 搜索页 ----------
  await page.goto(`${BASE}/search?keyword=test00`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(2500)
  await page.screenshot({ path: `${OUT}/05-search.png` })
  const searchText = await page.locator('body').innerText()
  check('搜索页返回结果', /test00/.test(searchText), searchText.replace(/\s+/g, ' ').slice(0, 80))

  // ---------- 6. 个人中心 ----------
  await page.goto(`${BASE}/profile`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(2000)
  await page.screenshot({ path: `${OUT}/06-profile.png` })
  const profileText = await page.locator('body').innerText()
  check('个人中心渲染', /test001|张三|资料/.test(profileText), profileText.replace(/\s+/g, ' ').slice(0, 80))

  // ---------- 7. 跳出前端：注册页（未登录可见） ----------
  const guest = await browser.newPage()
  await guest.goto(`${BASE}/register`, { waitUntil: 'load', timeout: 30000 })
  await guest.waitForTimeout(1500)
  const guestText = await guest.locator('body').innerText()
  check('注册页渲染', /注册|用户名|密码/.test(guestText))
  await guest.close()

  // ---------- 8. 控制台 ----------
  check('无未捕获的 JS 异常', pageErrors.length === 0, pageErrors.join(' | ').slice(0, 200))
  const significant = consoleErrors.filter(
    (t) => !/favicon|Download the Vue Devtools|Networking|ResizeObserver/i.test(t),
  )
  check('无显著控制台报错', significant.length === 0, significant.join(' | ').slice(0, 300))
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log('='.repeat(72))
console.log(`前端浏览器验证：${results.length - failed.length}/${results.length} 通过`)
if (failed.length) {
  console.log('失败项：')
  failed.forEach((f) => console.log(`  - ${f.step} ${f.detail}`))
}
console.log(`截图目录：${OUT}`)
console.log('='.repeat(72))
process.exit(failed.length ? 1 : 0)
