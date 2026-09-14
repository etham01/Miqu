/**
 * 竞态缺陷定位探针（只读分析用，不改任何生产代码）。
 *
 * 现象：首屏 latest 请求进行中时点击「我的关注」，
 *       高亮的 tab 变成「我的关注」，但 list 请求没有重新发出 ——
 *       页面把「最新动态」的数据显示在「我的关注」下面。
 *
 * 本探针要钉死两件事：
 *   1. tab 高亮 = 我的关注，但没有 tab=following 请求；
 *   2. 页面上渲染出的动态作者里，存在 A 未关注的用户（决定性证据）。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node bug_race_probe.mjs
 */
import { createRequire } from 'node:module'

const { chromium } = createRequire('D:/tools/playwright/')('playwright')

const API = 'http://127.0.0.1:8081'
const WEB = 'http://localhost:5173'
const PW = '123456'

async function api(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.json().catch(() => ({ code: -1 }))
}

async function newUser(prefix) {
  const name = `${prefix}${Math.random().toString(16).slice(2, 10)}`
  const reg = await api('POST', '/api/auth/register', {
    body: { username: name, password: PW, nickname: `竞态${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  if (reg.code !== 200) throw new Error(JSON.stringify(reg))
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  return { name, token: login.data.token, id: String(login.data.user.id), user: login.data.user }
}

const main = async () => {
  console.log('='.repeat(72))
  console.log('竞态缺陷定位探针：tab 高亮 / 请求 / 实际数据 三者是否一致')
  console.log('='.repeat(72))

  const A = await newUser('ra')
  const B = await newUser('rb')
  const marker = `RACE-${Date.now()}`
  await api('POST', '/api/posts', { token: B.token, body: { content: `${marker} only-followed-post` } })
  await api('POST', `/api/users/${B.id}/follow`, { token: A.token })

  // 基线：A 只关注 B，所以「我的关注」应恰好 1 条；「最新动态」是多条
  const followingFeed = await api('GET', '/api/posts?tab=following&page=1&size=10', { token: A.token })
  const latestFeed = await api('GET', '/api/posts?tab=latest&page=1&size=10', { token: A.token })
  console.log(`\n基线（API 直查）：`)
  console.log(`  tab=following  total=${followingFeed.data.total}  返回条数=${followingFeed.data.list.length}`)
  console.log(`  tab=latest     total=${latestFeed.data.total}  返回条数=${latestFeed.data.list.length}`)
  console.log(`  A 关注的用户 = [${B.id}]，最新动态里出现的作者 = [${latestFeed.data.list.map((p) => p.author.id).join(',')}]`)

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext()
  await ctx.addInitScript(
    ([token, user]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', token)
      localStorage.setItem('miqu_user', user)
    },
    [A.token, JSON.stringify(A.user)],
  )

  const reqs = []
  ctx.on('request', (r) => {
    const u = r.url()
    if (u.includes('/api/posts?')) reqs.push(u.replace(WEB, ''))
  })

  const page = await ctx.newPage()
  // 人为把首屏 latest 拖慢，稳定复现竞态窗口
  await page.route('**/api/posts?*', async (route) => {
    if (route.request().url().includes('tab=latest')) await new Promise((r) => setTimeout(r, 3000))
    await route.continue()
  })

  console.log('\n【操作】打开首页 → 300ms 内点击「我的关注」（此时 latest 仍在飞行中）')
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(300)
  await page.getByRole('button', { name: /我的关注/ }).click()
  await page.waitForTimeout(6000)

  const activeTab = await page.evaluate(() => {
    const el = document.querySelector('.home__tab.is-active')
    return el ? el.textContent.trim() : '(无)'
  })
  const rendered = await page.evaluate(() => {
    const cards = [...document.querySelectorAll('.post')]
    return cards.map((c) => ({
      author: c.querySelector('.post__meta')?.textContent?.trim() || '?',
      content: (c.querySelector('.post__content')?.textContent || '').slice(0, 40),
    }))
  })
  const requests = await page.evaluate(() => performance.getEntriesByType('resource')
    .map((e) => e.name).filter((n) => n.includes('/api/posts?')))

  console.log(`\n结果：`)
  console.log(`  高亮的 tab           = 「${activeTab}」`)
  console.log(`  实际发出的 list 请求 = ${requests.map((u) => u.split('/api')[1]).join(' | ') || '(无)'}`)
  console.log(`  页面渲染出的动态条数 = ${rendered.length}`)
  rendered.slice(0, 12).forEach((r, i) => console.log(`    ${i + 1}. ${r.author}  ::  ${r.content}`))

  const hasFollowingReq = requests.some((u) => u.includes('tab=following'))
  const showsMarker = rendered.some((r) => r.content.includes(marker))

  console.log(`\n判定：`)
  console.log(`  ① tab 高亮为「我的关注」?            ${activeTab === '我的关注'}`)
  console.log(`  ② 是否发出 tab=following 请求?       ${hasFollowingReq}   <-- 期望 true`)
  console.log(`  ③ 渲染条数 == 最新动态条数(${latestFeed.data.list.length})?      ${rendered.length === latestFeed.data.list.length}`)
  console.log(`  ④ 渲染条数 == 关注流条数(${followingFeed.data.list.length})?        ${rendered.length === followingFeed.data.list.length}`)
  console.log(`  ⑤ 关注流唯一一条(B 的动态)是否渲染?  ${showsMarker}`)
  console.log(`\n结论：${!hasFollowingReq && activeTab === '我的关注'
    ? '「我的关注」标签下展示的是『最新动态』的数据 —— 缺陷复现'
    : '未复现'}`)

  await page.screenshot({ path: '.workbuddy/screenshots/race-tab-mismatch.png', fullPage: true })
  await browser.close()
  return 0
}

main().then((c) => process.exit(c)).catch((e) => { console.error(e); process.exit(3) })
