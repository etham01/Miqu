/**
 * 「loading 守卫吞请求」缺陷族探针（只读分析用，不改任何生产代码）。
 *
 * 三处入口共用同一个写法：`if (loading.value) return`。
 * 该写法本意是防重复提交，但当"用户主动发起了新的查询"时，
 * 它会把新查询整条丢掉，而 UI 上的筛选条件已经变了 —— 于是【筛选项与实际数据不一致】。
 *
 *   场景 1  HomeView      切 tab   → 已在 bug_race_probe.mjs 单独钉死，这里顺带复跑
 *   场景 2  SearchView    换关键词 → 地址栏/输入框是 B，结果却是 A 的
 *   场景 3  NotificationView 切类型 → 高亮「点赞」，列表是「全部」
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node race_family_probe.mjs
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
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.json().catch(() => ({ code: -1 }))
}

async function newUser(prefix) {
  const name = `${prefix}${Math.random().toString(16).slice(2, 10)}`
  const reg = await api('POST', '/api/auth/register', {
    body: { username: name, password: PW, nickname: `族${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  if (reg.code !== 200) throw new Error(JSON.stringify(reg))
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  return { name, token: login.data.token, id: String(login.data.user.id), user: login.data.user }
}

const summary = []
function verdict(id, ok, title, detail) {
  summary.push({ id, ok, title })
  console.log(`  ${ok ? '✔ 缺陷复现' : '○ 未复现'}  [${id}] ${title}`)
  if (detail) console.log(`      ${detail}`)
}

async function makeCtx(browser, user) {
  const ctx = await browser.newContext()
  await ctx.addInitScript(
    ([token, u]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', token)
      localStorage.setItem('miqu_user', u)
    },
    [user.token, JSON.stringify(user.user)],
  )
  return ctx
}

const main = async () => {
  console.log('='.repeat(72))
  console.log('「loading 守卫吞请求」缺陷族探针')
  console.log('='.repeat(72))

  const A = await newUser('fa')
  const B = await newUser('fb')
  const browser = await chromium.launch({ channel: 'chrome', headless: true })

  /* ============ 场景 2：SearchView 换关键词 ============ */
  console.log('\n【场景 2】SearchView：首次搜索在飞时换关键词再搜')
  {
    const ctx = await makeCtx(browser, A)
    const reqs = []
    ctx.on('request', (r) => {
      if (r.url().includes('/api/users/search')) reqs.push(decodeURIComponent(r.url().split('/api/users/search')[1]))
    })
    const page = await ctx.newPage()
    await page.route('**/api/users/search*', async (route) => {
      await new Promise((r) => setTimeout(r, 3000))
      await route.continue()
    })
    await page.goto(`${WEB}/search`, { waitUntil: 'networkidle' })

    const input = page.locator('.search__bar input')
    await input.fill(B.name)              // 关键词 #1：存在且可搜到
    await input.press('Enter')
    await page.waitForTimeout(300)
    await input.fill('zzz-nonexistent-keyword')   // 关键词 #2：必定搜不到
    await input.press('Enter')
    await page.waitForTimeout(6000)

    const state = await page.evaluate(() => ({
      url: location.search,
      inputValue: document.querySelector('.search__bar input')?.value || '',
      activeElement: document.activeElement?.tagName || '',
      body: document.body.innerText.slice(0, 400),
    }))
    const searchReqs = reqs.length
    const firstReq = reqs[0] || ''
    const secondReq = reqs[1] || ''
    console.log(`  发出的 search 请求数 = ${searchReqs}`)
    console.log(`    第 1 个: ${firstReq}`)
    console.log(`    第 2 个: ${secondReq || '(无 —— 第 2 次搜索被丢弃)'}`)
    console.log(`  地址栏: ${state.url}`)
    console.log(`  输入框: ${state.inputValue}`)
    console.log(`  页面文本前 200 字: ${state.body.replace(/\s+/g, ' ').slice(0, 200)}`)

    const mismatch = searchReqs === 1 && state.url.includes('zzz-nonexistent')
    verdict('BUG-003', mismatch, 'SearchView：换了关键词却没重新查，筛选条件与结果不一致',
      `地址栏/输入框=${state.inputValue}，但只发了 1 次请求（关键词为第 1 个）`)
    await page.screenshot({ path: '.workbuddy/screenshots/race-search-mismatch.png', fullPage: true })
    await ctx.close()
  }

  /* ============ 场景 3：NotificationView 切类型 ============ */
  console.log('\n【场景 3】NotificationView：通知列表在飞时切「点赞」类型')
  {
    // 给 A 造两类通知：B 关注 A（type=1）、B 点赞 A 的动态（type=2）
    const p = await api('POST', '/api/posts', { token: A.token, body: { content: `族探针通知动态 ${Date.now()}` } })
    await api('POST', `/api/users/${A.id}/follow`, { token: B.token })
    await api('POST', `/api/posts/${p.data.id}/like`, { token: B.token })
    const unread = await api('GET', '/api/notifications/unread-count', { token: A.token })
    console.log(`  A 的未读通知结构: ${JSON.stringify(unread.data)}`)

    const ctx = await makeCtx(browser, A)
    const reqs = []
    ctx.on('request', (r) => {
      if (r.url().includes('/api/notifications?')) reqs.push(decodeURIComponent(r.url().split('/api/notifications')[1]))
    })
    const page = await ctx.newPage()
    await page.route('**/api/notifications*', async (route) => {
      const u = route.request().url()
      if (u.includes('/api/notifications?')) await new Promise((r) => setTimeout(r, 3000))
      await route.continue()
    })
    await page.goto(`${WEB}/notifications`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(300)
    await page.getByRole('button', { name: /^点赞$/ }).click()
    await page.waitForTimeout(6000)

    const state = await page.evaluate(() => ({
      active: document.querySelector('.notifications__tab.is-active')?.textContent?.trim() || '(无)',
      items: [...document.querySelectorAll('.notifications__item')].map(
        (n) => n.querySelector('.notifications__text')?.textContent?.replace(/\s+/g, ' ').trim(),
      ),
    }))
    const listReqs = reqs.filter((q) => q.startsWith('?'))
    console.log(`  发出的列表请求: ${listReqs.join(' | ') || '(无)'}`)
    console.log(`  高亮 tab = 「${state.active}」`)
    console.log(`  列表渲染 ${state.items.length} 条，前 3 条：`)
    state.items.slice(0, 3).forEach((t, i) => console.log(`    ${i + 1}. ${t}`))

    const hasFollowItem = state.items.some((t) => t && t.includes('关注了你'))
    const hasLikeItem = state.items.some((t) => t && t.includes('赞了你的动态'))
    // 判据：只应有 1 次请求（首屏「全部」），切到「点赞」后必须补发带 type=2 的请求。
    // 若没有补发，而列表里仍混着「关注了你」，则高亮与数据不一致。
    const reloadedWithType = listReqs.some((q) => q.includes('type=2'))
    const mismatch = state.active === '点赞' && !reloadedWithType && hasFollowItem && hasLikeItem
    verdict('BUG-004', mismatch, 'NotificationView：切了类型没重新查，高亮「点赞」却混着「关注」通知',
      `高亮=点赞，列表里同时含 "关注了你" 与 "赞了你的动态"`)
    await page.screenshot({ path: '.workbuddy/screenshots/race-notification-mismatch.png', fullPage: true })
    await ctx.close()
  }

  /* ============ 场景 1：HomeView 复跑 ============ */
  console.log('\n【场景 1】HomeView：首屏 latest 在飞时切「我的关注」（复跑确认）')
  {
    await api('POST', `/api/users/${B.id}/follow`, { token: A.token })
    const ctx = await makeCtx(browser, A)
    const reqs = []
    ctx.on('request', (r) => {
      if (r.url().includes('/api/posts?')) reqs.push(decodeURIComponent(r.url().split('/api/posts')[1]))
    })
    const page = await ctx.newPage()
    await page.route('**/api/posts?*', async (route) => {
      if (route.request().url().includes('tab=latest')) await new Promise((r) => setTimeout(r, 3000))
      await route.continue()
    })
    await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(300)
    await page.getByRole('button', { name: /我的关注/ }).click()
    await page.waitForTimeout(6000)

    const state = await page.evaluate(() => ({
      active: document.querySelector('.home__tab.is-active')?.textContent?.trim() || '(无)',
      count: document.querySelectorAll('.post').length,
    }))
    const followingReq = reqs.some((q) => q.includes('tab=following'))
    console.log(`  发出的 list 请求: ${reqs.join(' | ')}`)
    console.log(`  高亮 tab = 「${state.active}」，渲染 ${state.count} 条动态`)
    verdict('BUG-002', state.active === '我的关注' && !followingReq,
      'HomeView：切了 tab 没重新查，「我的关注」下显示最新动态',
      `高亮=我的关注，请求只有 tab=latest，渲染 ${state.count} 条（关注流应为 1 条）`)
    await ctx.close()
  }

  await browser.close()

  console.log('\n' + '='.repeat(72))
  console.log('汇总')
  summary.forEach((s) => console.log(`  ${s.ok ? '复现' : '未复现'}  [${s.id}] ${s.title}`))
  console.log('='.repeat(72))
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(3) })
