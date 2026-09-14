/**
 * Miqu 前端状态一致性回归（真实 Chrome）。
 *
 * 覆盖本次 Bug Hunt 定位到的「筛选条件变更 / 请求被吞」缺陷族，
 * 以及 BUG-001（取消关注后关注流是否残留）的固定回归。
 *
 * 为什么是浏览器脚本而不是 pytest：
 *   pytest 打的是 HTTP，只能证明后端返回正确；本缺陷族发生在**前端状态层**——
 *   后端每次都对，是前端没有把新条件下发出去。项目未引入前端测试框架
 *   （见 tests/README.md「已知覆盖缺口」），因此与 browser_check.mjs 同属
 *   可选的真实浏览器走查工具，不进 pytest 收集。
 *
 * 约定：进入用例前，请求会被人为放慢，以稳定复现"还在飞就点了下一个筛选条件"的窗口。
 *
 * 用法：
 *   1. 起后端（8081）与前端（5173）
 *   2. NO_PROXY=localhost,127.0.0.1 node tests/browser_regression.mjs
 *
 * 退出码：全部通过 0；有断言失败 1；环境不可用 2。
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
  console.error('无法加载 playwright。安装：npm i -D playwright && npx playwright install chromium')
  console.error('尝试过的位置：\n  ' + errors.join('\n  '))
  process.exit(2)
}

const { chromium } = loadPlaywright()

const API = process.env.MIQU_API_URL || 'http://127.0.0.1:8081'
const WEB = process.env.MIQU_FRONT_URL || 'http://localhost:5173'
const PW = '123456'
const SLOW_MS = Number(process.env.MIQU_SLOW_MS || 3000)

const results = []
function assert(id, title, ok, evidence) {
  results.push({ id, title, ok, evidence })
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  [${id}] ${title}`)
  if (evidence) console.log(`        ${evidence}`)
}

async function api(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.json().catch(() => ({ code: -1, message: 'non-json' }))
}

async function newUser(prefix) {
  const name = `${prefix}${Math.random().toString(16).slice(2, 10)}`
  const reg = await api('POST', '/api/auth/register', {
    body: { username: name, password: PW, nickname: `回归${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  if (reg.code !== 200) throw new Error(`注册失败 ${JSON.stringify(reg)}`)
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  if (login.code !== 200) throw new Error(`登录失败 ${JSON.stringify(login)}`)
  return { name, token: login.data.token, id: String(login.data.user.id), user: login.data.user }
}

/** 建一个已登录的浏览器上下文；同时把 /api 请求记录到 reqs。 */
async function loggedInPage(browser, user, reqs, slowMatch) {
  const ctx = await browser.newContext()
  await ctx.addInitScript(
    ([token, u]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', token)
      localStorage.setItem('miqu_user', u)
    },
    [user.token, JSON.stringify(user.user)],
  )
  ctx.on('request', (r) => {
    const u = r.url()
    if (u.includes('/api/')) reqs.push(decodeURIComponent(u.split('/api/')[1]))
  })
  if (slowMatch) {
    await ctx.route('**/api/**', async (route) => {
      if (slowMatch(route.request().url())) await new Promise((r) => setTimeout(r, SLOW_MS))
      await route.continue()
    })
  }
  const page = await ctx.newPage()
  return { ctx, page }
}

async function main() {
  console.log('='.repeat(72))
  console.log('Miqu 前端状态一致性回归')
  console.log(`后端 ${API}   前端 ${WEB}   人为延迟 ${SLOW_MS}ms`)
  console.log('='.repeat(72))

  // 环境自检：后端与前端都必须活着，否则给出明确提示而不是一堆超时
  const health = await api('GET', '/api/health').catch(() => null)
  if (!health || health.code !== 200) {
    console.error(`\n后端未就绪（${API}/api/health）。先启动 backend 再跑本脚本。`)
    process.exit(2)
  }
  const frontOk = await fetch(WEB, { method: 'GET' }).then((r) => r.ok).catch(() => false)
  if (!frontOk) {
    console.error(`\n前端未就绪（${WEB}）。先 cd frontend && npm run dev 再跑本脚本。`)
    process.exit(2)
  }

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const A = await newUser('rg')
  const B = await newUser('rh')
  const stamp = Date.now()

  /* ---------------------------------------------------------------
     BUG-001 固定回归：取消关注后，对方动态必须立刻从关注流消失
     （本次 Bug Hunt 结论为「未复现」，本条作为长期回归守住该行为）
     --------------------------------------------------------------- */
  console.log('\n[BUG-001] 取消关注后关注流不残留对方动态')
  {
    const marker = `RG001-${stamp}`
    await api('POST', '/api/posts', { token: B.token, body: { content: marker } })
    await api('POST', `/api/users/${B.id}/follow`, { token: A.token })

    const reqs = []
    const { ctx, page } = await loggedInPage(browser, A, reqs)
    await page.goto(`${WEB}/`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: /我的关注/ }).click()
    await page.waitForTimeout(2000)
    const before = (await page.content()).includes(marker)
    assert('BUG-001.a', '关注期间能在「我的关注」看到 B 的动态', before)

    await page.locator('.post__author').first().click()
    await page.waitForURL(`**/users/${B.id}`, { timeout: 10000 })
    await page.waitForTimeout(800)
    await page.locator('.hero__actions button').filter({ hasText: /已关注|互相关注/ }).first().click()
    await page.waitForTimeout(1500)

    const prof = await api('GET', `/api/users/${B.id}`, { token: A.token })
    assert('BUG-001.b', '取消关注已落库（followedByMe=false）', prof.data?.followedByMe === false,
      `followedByMe=${prof.data?.followedByMe}`)

    await page.goto(`${WEB}/`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: /我的关注/ }).click()
    await page.waitForTimeout(2000)
    const after = (await page.content()).includes(marker)
    assert('BUG-001.c', '取消关注后「我的关注」不再出现 B 的动态', !after)
    const askedFollowing = reqs.some((u) => u.startsWith('posts?tab=following'))
    assert('BUG-001.d', '切到「我的关注」确实重新请求了 tab=following', askedFollowing,
      reqs.filter((u) => u.startsWith('posts?')).join(' | ') || '(无)')
    await ctx.close()
  }

  /* ---------------------------------------------------------------
     BUG-002：首页 tab 高亮与实际列表必须一致
     --------------------------------------------------------------- */
  console.log('\n[BUG-002] 首页：首屏在飞时切「我的关注」')
  {
    await api('POST', `/api/users/${B.id}/follow`, { token: A.token }) // 让关注流非空
    const reqs = []
    const { ctx, page } = await loggedInPage(browser, A, reqs, (u) => u.includes('tab=latest'))
    await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(300)
    await page.getByRole('button', { name: /我的关注/ }).click()
    await page.waitForTimeout(SLOW_MS + 3000)

    const state = await page.evaluate(() => ({
      active: document.querySelector('.home__tab.is-active')?.textContent?.trim() || '(无)',
      rendered: document.querySelectorAll('.post').length,
    }))
    const askedFollowing = reqs.some((u) => u.startsWith('posts?tab=following'))
    assert('BUG-002.a', '切 tab 后高亮为「我的关注」', state.active === '我的关注', `高亮=${state.active}`)
    assert('BUG-002.b', '切 tab 后补发了 tab=following 请求', askedFollowing,
      reqs.filter((u) => u.startsWith('posts?')).join(' | ') || '(无)')

    const feed = await api('GET', '/api/posts?tab=following&page=1&size=10', { token: A.token })
    const expected = feed.data.list.length
    assert('BUG-002.c', '渲染条数与关注流一致（未把最新动态当关注流展示）',
      state.rendered === expected, `渲染 ${state.rendered} 条，关注流应为 ${expected} 条`)
    await ctx.close()
  }

  /* ---------------------------------------------------------------
     BUG-003：搜索关键词与结果必须一致
     --------------------------------------------------------------- */
  console.log('\n[BUG-003] 搜索：首次在飞时换关键词再搜')
  {
    const reqs = []
    const { ctx, page } = await loggedInPage(browser, A, reqs, (u) => u.includes('/users/search'))
    await page.goto(`${WEB}/search`, { waitUntil: 'networkidle' })

    const input = page.locator('.search__bar input')
    await input.fill(B.name)               // 第 1 个关键词：能搜到
    await input.press('Enter')
    await page.waitForTimeout(300)
    await input.fill('zzz-no-such-user')   // 第 2 个关键词：必定搜不到
    await input.press('Enter')
    await page.waitForTimeout(SLOW_MS + 3000)

    const state = await page.evaluate(() => ({
      url: location.search,
      input: document.querySelector('.search__bar input')?.value || '',
      body: document.body.innerText.replace(/\s+/g, ' '),
    }))
    const searchReqs = reqs.filter((u) => u.startsWith('users/search'))
    const refetched = searchReqs.length >= 2 && searchReqs[1].includes('zzz-no-such-user')
    assert('BUG-003.a', '换关键词后重新发起了搜索请求', refetched,
      `共 ${searchReqs.length} 次：${searchReqs.join(' | ') || '(无)'}`)
    // 第 2 个关键词必定搜不到，因此正确行为下页面应提示「没有找到」
    const consistent = state.body.includes('没有找到')
    assert('BUG-003.b', '页面结果与当前关键词一致（应提示没有找到）', refetched && consistent,
      `地址栏=${state.url} 输入框=${state.input} 页面含"没有找到"=${state.body.includes('没有找到')}`)

    // BUG-003.c：清空关键词后，仍在飞的旧请求不得把已清空的列表重新填上
    await input.fill(B.name)
    await input.press('Enter')
    await page.waitForTimeout(300)
    await input.fill('')
    await input.press('Enter')
    await page.waitForTimeout(SLOW_MS + 3000)
    const cleared = await page.evaluate(() => ({
      cards: document.querySelectorAll('.user-card').length,
    }))
    assert('BUG-003.c', '清空关键词后旧响应不会把列表重新填上', cleared.cards === 0,
      `渲染 ${cleared.cards} 个用户卡片`)
    await ctx.close()
  }

  /* ---------------------------------------------------------------
     BUG-004：通知类型筛选必须生效
     --------------------------------------------------------------- */
  console.log('\n[BUG-004] 通知：列表在飞时切「点赞」类型')
  {
    // 造两类通知：B 关注 A（type=1）、B 点赞 A 的动态（type=2）
    const p = await api('POST', '/api/posts', { token: A.token, body: { content: `RG004-${stamp}` } })
    await api('POST', `/api/users/${A.id}/follow`, { token: B.token })
    await api('POST', `/api/posts/${p.data.id}/like`, { token: B.token })

    const reqs = []
    const { ctx, page } = await loggedInPage(
      browser, A, reqs, (u) => u.includes('/notifications?'),
    )
    await page.goto(`${WEB}/notifications`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(300)
    await page.getByRole('button', { name: /^点赞$/ }).click()
    await page.waitForTimeout(SLOW_MS + 3000)

    const state = await page.evaluate(() => ({
      active: document.querySelector('.notifications__tab.is-active')?.textContent?.trim() || '(无)',
      items: [...document.querySelectorAll('.notifications__item')].map(
        (n) => n.querySelector('.notifications__text')?.textContent?.replace(/\s+/g, ' ').trim(),
      ),
    }))
    const listReqs = reqs.filter((u) => u.startsWith('notifications?'))
    const filtered = listReqs.some((u) => u.includes('type=2'))
    assert('BUG-004.a', '切类型后补发了带 type=2 的请求', filtered, listReqs.join(' | ') || '(无)')
    const onlyLikes = state.items.length > 0 && state.items.every((t) => t && t.includes('赞了你的动态'))
    assert('BUG-004.b', '高亮「点赞」时列表只含点赞通知', onlyLikes,
      `列表 ${state.items.length} 条：${state.items.join(' / ')}`)
    await ctx.close()
  }

  await browser.close()

  const failed = results.filter((r) => !r.ok)
  console.log('\n' + '='.repeat(72))
  console.log(`共 ${results.length} 项断言，PASS ${results.length - failed.length}，FAIL ${failed.length}`)
  failed.forEach((f) => console.log(`  FAIL [${f.id}] ${f.title}`))
  console.log('='.repeat(72))
  return failed.length ? 1 : 0
}

main().then((c) => process.exit(c)).catch((e) => {
  console.error(e)
  process.exit(3)
})
