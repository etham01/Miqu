/**
 * BUG-001 真实浏览器复现探针（只读分析用，不改任何生产代码）。
 *
 * 目的：验证「取消关注后，对方动态是否仍出现在首页『我的关注』流」。
 * 只通过 HTTP/API 验证不足以判定 UI 层，所以这里用真实 Chrome 走一遍用户路径。
 *
 * 场景 A（报告描述的路径）：
 *   A 关注 B → B 发动态 → A 打开首页切「我的关注」看到 B 动态
 *   → A 进入 B 主页取消关注 → A 回首页切「我的关注」→ B 动态不应再出现
 *
 * 场景 B（loading 竞态探测，只在 A 通过后做）：
 *   首屏 latest 请求被人为延迟时，立刻点「我的关注」，
 *   观察是否发出 tab=following 请求、页面显示的是哪一份数据。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node bug001_browser_probe.mjs
 */
import { createRequire } from 'node:module'

function loadPlaywright() {
  const candidates = ['playwright', 'D:/tools/playwright/', 'C:/tools/playwright/']
  const errors = []
  for (const base of candidates) {
    try {
      return createRequire(base.endsWith('/') ? base : base + '/')('playwright')
    } catch (err) {
      errors.push(`${base}: ${err.code || err.message}`)
    }
  }
  console.error('无法加载 playwright：\n  ' + errors.join('\n  '))
  process.exit(2)
}

const { chromium } = loadPlaywright()

const API = process.env.MIQU_API_URL || 'http://127.0.0.1:8081'
const WEB = process.env.MIQU_FRONT_URL || 'http://localhost:5173'
const PW = '123456'

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? '  ✓' : '  ✗'} ${name}${detail ? '  — ' + detail : ''}`)
}

async function api(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await res.json().catch(() => ({ code: -1, message: 'non-json' }))
  return json
}

async function newUser(prefix) {
  const name = `${prefix}${Math.random().toString(16).slice(2, 10)}`
  const reg = await api('POST', '/api/auth/register', {
    body: { username: name, password: PW, nickname: `探针${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  if (reg.code !== 200) throw new Error(`注册失败: ${JSON.stringify(reg)}`)
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  if (login.code !== 200) throw new Error(`登录失败: ${JSON.stringify(login)}`)
  return { name, token: login.data.token, id: String(login.data.user.id), user: login.data.user }
}

const main = async () => {
  console.log('='.repeat(72))
  console.log('BUG-001 真实浏览器复现探针')
  console.log('='.repeat(72))

  const A = await newUser('ba')
  const B = await newUser('bb')
  const marker = `BUG001-${Date.now()}`
  const post = await api('POST', '/api/posts', { token: B.token, body: { content: `${marker} by B` } })
  if (post.code !== 200) throw new Error(`发动态失败: ${JSON.stringify(post)}`)
  console.log(`A = ${A.name} (id=${A.id})`)
  console.log(`B = ${B.name} (id=${B.id})  动态标记 = ${marker}\n`)

  // 预置：A 关注 B
  const f = await api('POST', `/api/users/${B.id}/follow`, { token: A.token })
  console.log(`预置 A 关注 B -> code=${f.code}\n`)

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext()

  // 在任何页面脚本执行前注入登录态（等价于已登录用户刷新页面）
  await ctx.addInitScript(
    ([token, user]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', token)
      localStorage.setItem('miqu_user', user)
    },
    [A.token, JSON.stringify(A.user)],
  )

  const requests = []
  ctx.on('request', (r) => {
    const u = r.url()
    if (u.includes('/api/posts?')) requests.push(u.replace(WEB, ''))
  })

  const page = await ctx.newPage()
  const jsErrors = []
  page.on('pageerror', (e) => jsErrors.push(String(e)))

  // ---------- 步骤 1：首页 → 我的关注 → 应看到 B 的动态 ----------
  console.log('【步骤 1】打开首页并切到「我的关注」')
  await page.goto(`${WEB}/`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: /我的关注/ }).click()
  await page.waitForFunction(
    (m) => document.body.innerText.includes(m),
    marker,
    { timeout: 10000 },
  ).catch(() => {})
  const visibleBefore = (await page.content()).includes(marker)
  check('关注流中能看到 B 的动态（关注期间）', visibleBefore)
  await page.screenshot({ path: '.workbuddy/screenshots/bug001-1-following-visible.png', fullPage: true })

  // ---------- 步骤 2：进入 B 主页取消关注（真实点击） ----------
  console.log('\n【步骤 2】从动态卡片进入 B 主页并取消关注')
  await page.locator('.post__author').first().click()
  await page.waitForURL(`**/users/${B.id}`, { timeout: 10000 })
  await page.waitForTimeout(800)
  const btn = page.locator('.hero__actions button').filter({ hasText: /已关注|互相关注/ }).first()
  await btn.click()
  await page.waitForFunction(
    () => (document.querySelector('.el-message')?.textContent || '').includes('已取消关注'),
    null,
    { timeout: 10000 },
  ).catch(() => {})
  await page.waitForTimeout(1200)
  const unfollowed = (await page.content()).includes('已取消关注') ||
    (await page.locator('.hero__actions button').filter({ hasText: /^关注$/ }).count()) > 0
  check('主页取消关注已生效', unfollowed)
  await page.screenshot({ path: '.workbuddy/screenshots/bug001-2-unfollowed.png', fullPage: true })

  // API 侧交叉验证
  const prof = await api('GET', `/api/users/${B.id}`, { token: A.token })
  check('API 侧 followedByMe=false', prof.data?.followedByMe === false, `followedByMe=${prof.data?.followedByMe}`)

  // ---------- 步骤 3：回首页 → 我的关注 → B 的动态应消失 ----------
  console.log('\n【步骤 3】回到首页，再次查看「我的关注」')
  requests.length = 0
  await page.goto(`${WEB}/`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: /我的关注/ }).click()
  await page.waitForTimeout(2500)
  const visibleAfter = (await page.content()).includes(marker)
  check('取消关注后聚焦流中不再出现 B 的动态', !visibleAfter)
  check('确实重新请求了关注流', requests.some((u) => u.includes('tab=following')), requests.join(' | ') || '(无请求)')
  await page.screenshot({ path: '.workbuddy/screenshots/bug001-3-after-unfollow.png', fullPage: true })

  // ---------- 步骤 4：刷新页面再查一次 ----------
  console.log('\n【步骤 4】硬刷新后再次切「我的关注」')
  await page.reload({ waitUntil: 'networkidle' })
  await page.getByRole('button', { name: /我的关注/ }).click()
  await page.waitForTimeout(2500)
  const visibleReload = (await page.content()).includes(marker)
  check('硬刷新后仍不出现 B 的动态', !visibleReload)

  // ---------- 场景 B：loading 竞态探测 ----------
  console.log('\n【场景 B】首屏 latest 请求被延迟时立刻点击「我的关注」')
  const ctx2 = await browser.newContext()
  await ctx2.addInitScript(
    ([token, user]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', token)
      localStorage.setItem('miqu_user', user)
    },
    [A.token, JSON.stringify(A.user)],
  )
  const req2 = []
  ctx2.on('request', (r) => {
    const u = r.url()
    if (u.includes('/api/posts?')) req2.push(u.replace(WEB, ''))
  })
  const page2 = await ctx2.newPage()
  // 人为拖慢首屏 latest 请求 3 秒
  await page2.route('**/api/posts?*', async (route) => {
    const url = route.request().url()
    if (url.includes('tab=latest')) await new Promise((r) => setTimeout(r, 3000))
    await route.continue()
  })
  await page2.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  // 首屏请求还在飞的时候点关注 tab
  await page2.waitForTimeout(300)
  const clicked = await page2
    .getByRole('button', { name: /我的关注/ })
    .click({ timeout: 5000 })
    .then(() => true)
    .catch(() => false)
  await page2.waitForTimeout(5000)
  const followingReq = req2.some((u) => u.includes('tab=following'))
  const bodyText = await page2.evaluate(() => document.body.innerText)
  const activeTab = await page2.evaluate(() => {
    const el = document.querySelector('.home__tab.is-active')
    return el ? el.textContent.trim() : '(无)'
  })
  console.log(`  已点击 tab: ${clicked}`)
  console.log(`  发出的 /api/posts 请求: ${req2.join(' | ') || '(无)'}`)
  console.log(`  页面高亮的 tab 文本: ${activeTab}`)
  check('竞态下是否补发了 tab=following 请求', followingReq)
  await page2.screenshot({ path: '.workbuddy/screenshots/bug001-4-race.png', fullPage: true })

  check('页面无未捕获 JS 异常', jsErrors.length === 0, jsErrors.join(' | '))

  await browser.close()

  const failed = results.filter((r) => !r.ok)
  console.log('\n' + '='.repeat(72))
  console.log(`共 ${results.length} 项断言，通过 ${results.length - failed.length}，失败 ${failed.length}`)
  if (failed.length) failed.forEach((r) => console.log(`  ✗ ${r.name}`))
  console.log('='.repeat(72))
  return failed.some((r) => r.name.startsWith('取消关注后') || r.name.startsWith('硬刷新后')) ? 1 : 0
}

main().then((c) => process.exit(c)).catch((e) => {
  console.error(e)
  process.exit(3)
})
