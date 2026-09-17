/**
 * 破图兜底诊断（只读，不改任何代码）。
 *
 * 假设：UserAvatar.vue 只有 `v-if="src"` 的"有无"判断，**没有加载失败的兜底**，
 * 所以只要 src 非空但取不到内容，就是永久破图，不会退回首字母占位。
 *
 * 手法：
 *   给测试账号设一个"前缀合法但文件不存在"的头像（走正常 API，不碰代码/库结构），
 *   再用真实 Chrome 打开该用户主页，观察 <img> 的解码状态与是否出现占位。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node avatar_broken_fallback_probe.mjs
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
  await api('POST', '/api/auth/register', {
    body: { username: name, password: PW, nickname: `兜底${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  return { name, token: login.data.token, id: String(login.data.user.id), user: login.data.user }
}

const inspect = (page) =>
  page.evaluate(() => {
    const imgs = [...document.querySelectorAll('.miqu-avatar')].map((el) => ({
      tag: el.tagName,
      src: el.getAttribute('src'),
      naturalWidth: el.naturalWidth ?? null,
      complete: el.complete ?? null,
      text: (el.textContent || '').trim().slice(0, 4),
    }))
    return imgs
  })

const main = async () => {
  console.log('='.repeat(74))
  console.log('破图兜底诊断')
  console.log('='.repeat(74))

  const A = await newUser('bf')
  console.log(`\n测试账号 A = ${A.name} (id=${A.id})，初始 avatar=${JSON.stringify(A.user.avatar)}`)

  const missing = '/uploads/image/2026/09/definitely-not-exist.png'
  const put = await api('PUT', '/api/users/me/avatar', { token: A.token, body: { avatar: missing } })
  console.log(`\n把头像设为「前缀合法但文件不存在」的路径：${missing}`)
  console.log(`  PUT -> code=${put.code} ${put.message || ''}`)
  console.log(`  回传 avatar = ${JSON.stringify(put.data?.avatar)}`)
  console.log(`  ⚠ 接口接受了一个磁盘上并不存在的文件路径（未做存在性校验）`)

  const r = await fetch(`${API}${missing}`)
  const body = await r.text()
  console.log(`\n直接请求该 URL：`)
  console.log(`  http=${r.status}  content-type=${r.headers.get('content-type')}`)
  console.log(`  body=${body.slice(0, 120)}`)
  console.log(`  ⚠ 注意：HTTP 状态是 ${r.status}，返回的却是 JSON —— 浏览器 <img> 拿到非图片内容会解码失败`)

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext()

  // 用 A 自己的登录态看自己的主页（会渲染自己的头像）
  await ctx.addInitScript(
    ([t, u]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', t)
      localStorage.setItem('miqu_user', u)
    },
    [A.token, JSON.stringify(A.user)],
  )
  const page = await ctx.newPage()
  const consoleErrors = []
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text())
  })
  const failedImgs = []
  page.on('response', (res) => {
    if (res.request().resourceType() === 'image') {
      failedImgs.push({ url: res.url().slice(0, 70), status: res.status(), type: res.headers()['content-type'] })
    }
  })

  console.log(`\n打开自己的主页 /users/${A.id}（头像已指向不存在的文件）`)
  await page.goto(`${WEB}/users/${A.id}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)

  const imgs = await inspect(page)
  console.log(`  页面上的 .miqu-avatar 元素：`)
  imgs.forEach((i) => {
    const broken = i.tag === 'IMG' && i.complete && i.naturalWidth === 0
    const ok = i.tag === 'IMG' && i.naturalWidth > 0
    const label = broken ? '★破图（无兜底）' : ok ? '正常图片' : '首字母占位'
    console.log(`    <${i.tag.toLowerCase()}> ${String(i.src).slice(0, 58).padEnd(58)} naturalWidth=${i.naturalWidth}  → ${label}`)
  })

  const brokenImgs = imgs.filter((i) => i.tag === 'IMG' && i.complete && i.naturalWidth === 0)
  const fallbackSpans = imgs.filter((i) => i.tag === 'SPAN')

  console.log(`\n判定：`)
  console.log(`  破图 <img> 数量      = ${brokenImgs.length}`)
  console.log(`  首字母占位 span 数量 = ${fallbackSpans.length}  ← 期望至少 1（本应降级到占位）`)
  console.log(`  浏览器 console 报错  = ${consoleErrors.length} 条`)
  consoleErrors.slice(0, 3).forEach((e) => console.log(`      ${e.slice(0, 110)}`))

  console.log(`\n图片响应：`)
  failedImgs.forEach((f) => console.log(`  [${f.status}] ${f.type} ${f.url}`))

  console.log(
    `\n结论：${brokenImgs.length > 0 && fallbackSpans.length === 0
      ? '★ 确实存在「加载失败 → 永久破图、不降级」的问题（复现成功）'
      : '未复现'}`,
  )

  await page.screenshot({ path: '.workbuddy/screenshots/avatar-broken-fallback.png', fullPage: true })
  console.log('\n截图已保存：.workbuddy/screenshots/avatar-broken-fallback.png')

  await browser.close()
  console.log('\n' + '='.repeat(74))
  console.log(`供核对：测试账号 id=${A.id}，将其 avatar 设为不存在文件的路径`)
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(3) })
