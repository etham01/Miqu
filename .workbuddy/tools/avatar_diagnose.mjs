/**
 * 头像加载诊断探针（只读，不改任何代码）。
 *
 * 目标：搞清楚"头像图片无法加载"到底发生在哪一层。
 *   层 1 Browser → 外链图床 i.pravatar.cc（21 个种子用户）
 *   层 2 Browser → /uploads/...（本地上传头像）
 *   层 3 空头像用户应显示首字母占位（不是破图）
 *
 * 手法：真实 Chrome 打开页面，遍历所有 <img.miqu-avatar>，
 * 读取 naturalWidth（0 = 解码失败/未加载）+ 记录失败请求与失败原因。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node avatar_diagnose.mjs
 */
import { createRequire } from 'node:module'

const { chromium } = createRequire('D:/tools/playwright/')('playwright')

const API = 'http://127.0.0.1:8081'
const WEB = 'http://localhost:5173'

async function api(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.json().catch(() => ({ code: -1 }))
}

const main = async () => {
  console.log('='.repeat(74))
  console.log('头像加载诊断')
  console.log('='.repeat(74))

  const login = await api('POST', '/api/auth/login', {
    body: { username: 'test001', password: '123456' },
  })
  console.log(`\ntest001 登录 -> code=${login.code}`)
  console.log(`  DB 里的 avatar = ${JSON.stringify(login.data?.user?.avatar)}`)

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext()
  await ctx.addInitScript(
    ([token, user]) => {
      localStorage.setItem('miqu_version', '1')
      localStorage.setItem('miqu_token', token)
      localStorage.setItem('miqu_user', user)
    },
    [login.data.token, JSON.stringify(login.data.user)],
  )

  const failed = []
  const imageReqs = []
  ctx.on('requestfailed', (r) => {
    failed.push({ url: r.url(), reason: r.failure()?.errorText || '?' })
  })
  ctx.on('response', (r) => {
    const u = r.url()
    if (r.request().resourceType() === 'image' || /pravatar|\/uploads\//.test(u)) {
      imageReqs.push({
        url: u,
        status: r.status(),
        type: r.headers()['content-type'] || '-',
      })
    }
  })

  const page = await ctx.newPage()
  page.on('console', (m) => {
    if (m.type() === 'error') console.log(`    [browser console error] ${m.text().slice(0, 160)}`)
  })

  const dump = async (label) => {
    const info = await page.evaluate(() =>
      [...document.querySelectorAll('img.miqu-avatar')].map((el) => ({
        src: el.getAttribute('src'),
        w: el.naturalWidth,
        h: el.naturalHeight,
        complete: el.complete,
        rect: { w: Math.round(el.getBoundingClientRect().width), h: Math.round(el.getBoundingClientRect().height) },
      })),
    )
    const fallbacks = await page.evaluate(
      () => document.querySelectorAll('span.miqu-avatar--fallback').length,
    )
    console.log(`\n【${label}】`)
    console.log(`  <img> 头像数量 = ${info.length}；占位（首字母 span）数量 = ${fallbacks}`)
    const broken = info.filter((i) => i.complete && i.w === 0)
    const ok = info.filter((i) => i.w > 0)
    const pending = info.filter((i) => !i.complete)
    console.log(`  成功解码 = ${ok.length}；失败(破图) = ${broken.length}；仍在加载 = ${pending.length}`)
    info.slice(0, 6).forEach((i) => {
      const state = i.w > 0 ? 'OK  ' : i.complete ? '破图' : '加载中'
      console.log(`    [${state}] w=${i.w} h=${i.h}  显示尺寸=${i.rect.w}x${i.rect.h}  ${String(i.src).slice(0, 70)}`)
    })
    if (info.length > 6) console.log(`    … 其余 ${info.length - 6} 个省略`)
    return { total: info.length, ok: ok.length, broken: broken.length }
  }

  console.log('\n--- 1) 打开首页（登录态）---')
  await page.goto(`${WEB}/`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  await dump('首页')

  console.log('\n--- 2) 打开个人中心（自己的头像）---')
  await page.goto(`${WEB}/profile`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  await dump('个人中心')

  console.log('\n--- 3) 打开用户主页 /users/2（另一位种子用户）---')
  await page.goto(`${WEB}/users/2`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  await dump('用户主页')

  console.log('\n--- 4) 直接在后端同源路径下请求同一批图片（对比链路）---')
  const cases = [
    `${WEB}/uploads/image/2026/09/06f374ae6d5641bb8c6778062157a214.png`, // 真实存在于磁盘
    `${WEB}/uploads/image/2026/09/qa-avatar.jpg`,                          // DB 指向但磁盘不存在
  ]
  for (const u of cases) {
    const r = await page.evaluate(async (url) => {
      const res = await fetch(url)
      const buf = await res.arrayBuffer()
      return { status: res.status, type: res.headers.get('content-type'), bytes: buf.byteLength,
               head: Array.from(new Uint8Array(buf).slice(0, 12)).map((b) => b.toString(16).padStart(2, '0')).join(' ') }
    }, u)
    console.log(`  ${u.replace(WEB, '')}`)
    console.log(`      -> http=${r.status} type=${r.type} bytes=${r.bytes} head=[${r.head}]`)
  }

  console.log('\n--- 5) 图片相关网络请求汇总 ---')
  if (!imageReqs.length) console.log('  (无)')
  imageReqs.forEach((r) => console.log(`  [${r.status}] ${r.type.padEnd(26)} ${r.url.slice(0, 80)}`))

  console.log('\n--- 6) 失败的请求 ---')
  if (!failed.length) console.log('  (无)')
  failed.forEach((f) => console.log(`  ${f.reason}  ${f.url.slice(0, 90)}`))

  await browser.close()

  console.log('\n' + '='.repeat(74))
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(3) })
