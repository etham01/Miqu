/**
 * 头像功能端到端诊断（只读，不改任何代码）。
 *
 * 走完整链路，逐段报告哪一步出了问题：
 *   1 注册新用户
 *   2 上传一张**真实**图片 -> POST /api/files/image
 *   3 把返回的 URL 设为头像 -> PUT /api/users/me/avatar
 *   4 直接请求该 URL（后端 8081 与前端代理 5173 各一次）
 *   5 真实浏览器打开个人中心，看 <img> 能否解码
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node avatar_e2e_probe.mjs
 */
import { createRequire } from 'node:module'

const { chromium } = createRequire('D:/tools/playwright/')('playwright')

const API = 'http://127.0.0.1:8081'
const WEB = 'http://localhost:5173'
const PW = '123456'
// 一张真实的 1x1 PNG（68 字节，含完整 IHDR/IDAT/IEND）
const PNG_B64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=='

async function api(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = { raw: text.slice(0, 200) } }
  return { http: res.status, type: res.headers.get('content-type'), ...json }
}

const step = (n, msg) => console.log(`\n[${n}] ${msg}`)

const main = async () => {
  console.log('='.repeat(74))
  console.log('头像功能端到端诊断')
  console.log('='.repeat(74))

  /* ---------- 1) 注册 ---------- */
  step(1, '注册一个全新用户')
  const name = `av${Math.random().toString(16).slice(2, 10)}`
  const reg = await api('POST', '/api/auth/register', {
    body: { username: name, password: PW, nickname: `头像诊断${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  console.log(`  register -> code=${reg.code} ${reg.message || ''}`)
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  console.log(`  login    -> code=${login.code}`)
  const token = login.data.token
  console.log(`  初始 avatar = ${JSON.stringify(login.data.user.avatar)}`)

  /* ---------- 2) 上传真实图片 ---------- */
  step(2, '上传一张真实 PNG（68 字节）到 /api/files/image')
  const pngBytes = Buffer.from(PNG_B64, 'base64')
  console.log(`  待上传字节数 = ${pngBytes.length}`)
  const form = new FormData()
  form.append('file', new Blob([pngBytes], { type: 'image/png' }), 'avatar.png')
  const upRes = await fetch(`${API}/api/files/image`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  })
  const upText = await upRes.text()
  console.log(`  http=${upRes.status}  body=${upText.slice(0, 200)}`)
  let uploadedUrl = null
  try { uploadedUrl = JSON.parse(upText).data?.url ?? null } catch { /* ignore */ }
  console.log(`  返回 url = ${JSON.stringify(uploadedUrl)}`)

  if (!uploadedUrl) {
    console.log('\n  ✗ 上传没拿到 url，后续步骤无法继续')
    process.exit(1)
  }

  /* ---------- 3) 设为头像 ---------- */
  step(3, 'PUT /api/users/me/avatar')
  const put = await api('PUT', '/api/users/me/avatar', { token, body: { avatar: uploadedUrl } })
  console.log(`  code=${put.code} message=${put.message || ''}`)
  console.log(`  回传 avatar = ${JSON.stringify(put.data?.avatar)}`)
  const me = await api('GET', '/api/users/me', { token })
  console.log(`  GET /users/me avatar = ${JSON.stringify(me.data?.avatar)}`)

  /* ---------- 4) 直接请求该图片 ---------- */
  step(4, '分别经后端 8081 与前端 5173 请求这张图')
  for (const [label, base] of [['后端直连', API], ['前端代理', WEB]]) {
    const r = await fetch(`${base}${uploadedUrl}`)
    const buf = Buffer.from(await r.arrayBuffer())
    const head = buf.slice(0, 8).toString('hex')
    console.log(
      `  ${label}: http=${r.status} type=${r.headers.get('content-type')} bytes=${buf.length} head=[${head}]`,
    )
    if (buf.length < 100) console.log(`      ⚠ 体积异常小；内容=${buf.toString('utf8').slice(0, 120)}`)
  }

  /* ---------- 5) 浏览器渲染 ---------- */
  step(5, '真实浏览器打开个人中心，检查头像 <img> 是否解码成功')
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext()
  await ctx.addInitScript(
    ([t, u]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', t)
      localStorage.setItem('miqu_user', u)
    },
    [token, JSON.stringify(login.data.user)],
  )
  const page = await ctx.newPage()
  await page.goto(`${WEB}/profile`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)

  const info = await page.evaluate(() =>
    [...document.querySelectorAll('img.miqu-avatar')].map((el) => ({
      src: el.getAttribute('src'),
      w: el.naturalWidth,
      h: el.naturalHeight,
      complete: el.complete,
    })),
  )
  console.log(`  页面上 <img> 头像数量 = ${info.length}`)
  info.forEach((i) => {
    const state = i.w > 0 ? 'OK  ' : i.complete ? '破图' : '加载中'
    console.log(`    [${state}] naturalWidth=${i.w} naturalHeight=${i.h}  ${String(i.src).slice(0, 80)}`)
  })
  const fallbackCount = await page.evaluate(
    () => document.querySelectorAll('span.miqu-avatar--fallback').length,
  )
  console.log(`  首字母占位 span 数量 = ${fallbackCount}`)

  await page.screenshot({ path: '.workbuddy/screenshots/avatar-diagnose-profile.png', fullPage: true })
  await browser.close()

  console.log('\n' + '='.repeat(74))
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(3) })
