/**
 * API 边界与一致性探针（只读分析用，不改任何生产代码）。
 *
 * 针对文档里已登记为「缺口」的头像校验做实证：
 *   - 动态图片有 `/uploads/` 前缀强校验（已在 tests/api/test_post.py 覆盖）
 *   - 头像接口按 DTO 注释与 design.md 第 199 行，同样应只接受上传接口返回的 URL
 * 这里验证该规则是否真的被后端强制。
 *
 * 顺带复核分页 / tab / 关注自己 等边界，确认与文档一致（避免把正常行为误判为 Bug）。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node api_boundary_probe.mjs
 */
const API = 'http://127.0.0.1:8081'
const PW = '123456'

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
    body: { username: name, password: PW, nickname: `边界${name.slice(-4)}`, email: `${name}@miqu.test` },
  })
  if (reg.code !== 200) throw new Error(JSON.stringify(reg))
  const login = await api('POST', '/api/auth/login', { body: { username: name, password: PW } })
  return { name, token: login.data.token, id: String(login.data.user.id), user: login.data.user }
}

const rows = []
function row(name, expected, actual, pass) {
  rows.push({ name, expected, actual, pass })
  console.log(`  ${pass ? '✓' : '✗'} ${name}\n      期望=${expected}  实际=${actual}`)
}

const main = async () => {
  console.log('='.repeat(72))
  console.log('API 边界与一致性探针')
  console.log('='.repeat(72))

  const A = await newUser('bd')
  const B = await newUser('be')

  console.log('\n【1】头像 URL 是否强制来自上传接口')
  const evil = await api('PUT', '/api/users/me/avatar', {
    token: A.token, body: { avatar: 'https://evil.example.com/track.png' },
  })
  row('外链头像被拒绝', '400 INVALID_IMAGE_URL（与动态图片一致）', `${evil.code} ${evil.message}`,
    evil.code === 400)
  const evilData = await api('GET', '/api/users/me', { token: A.token })
  console.log(`      → 当前头像实际落库值: ${JSON.stringify(evilData.data?.avatar)}`)

  const blank = await api('PUT', '/api/users/me/avatar', { token: A.token, body: { avatar: '   ' } })
  row('空白头像被拒绝', '400', `${blank.code} ${blank.message}`, blank.code === 400)

  console.log('\n【2】动态图片前缀校验（对照组，应与头像不同）')
  const postBadImg = await api('POST', '/api/posts', {
    token: A.token, body: { content: '前缀校验对照', images: ['https://evil.example.com/x.png'] },
  })
  row('外链动态图片被拒绝', '400', `${postBadImg.code} ${postBadImg.message}`, postBadImg.code === 400)
  const postNearMiss = await api('POST', '/api/posts', {
    token: A.token, body: { content: '前缀近似', images: ['/uploads-evil/a.png'] },
  })
  row('前缀近似串 /uploads-evil/ 被拒绝', '400', `${postNearMiss.code} ${postNearMiss.message}`,
    postNearMiss.code === 400)

  console.log('\n【3】分页边界')
  for (const [q, exp] of [['page=0', '400'], ['size=0', '400'], ['size=51', '400'], ['size=50', '200'], ['page=99999', '200']]) {
    const r = await api('GET', `/api/posts?${q}`, { token: A.token })
    row(`GET /api/posts?${q}`, exp, String(r.code), String(r.code) === exp)
  }

  console.log('\n【4】tab 取值边界')
  const badTab = await api('GET', '/api/posts?tab=hot', { token: A.token })
  row('tab=hot', '400', `${badTab.code} ${badTab.message}`, badTab.code === 400)
  const anonFollowing = await api('GET', '/api/posts?tab=following')
  row('未登录 tab=following', '401', `${anonFollowing.code} ${anonFollowing.message}`, anonFollowing.code === 401)

  console.log('\n【5】关注自身 / 不存在目标')
  const self = await api('POST', `/api/users/${A.id}/follow`, { token: A.token })
  row('关注自己', '400', `${self.code} ${self.message}`, self.code === 400)
  const ghost = await api('POST', '/api/users/99999999/follow', { token: A.token })
  row('关注不存在用户', '404', `${ghost.code} ${ghost.message}`, ghost.code === 404)

  console.log('\n【6】非法 ID / 不存在资源的一致性（同族接口应同码）')
  const cases = [
    ['GET', '/api/posts/99999999'],
    ['GET', '/api/users/99999999'],
    ['GET', '/api/posts/99999999/comments'],
    ['GET', '/api/users/99999999/posts'],
    ['GET', '/api/users/99999999/following'],
    ['GET', '/api/users/99999999/followers'],
  ]
  for (const [m, p] of cases) {
    const r = await api(m, p, { token: A.token })
    console.log(`      ${m} ${p} -> ${r.code} ${r.message}`)
  }
  const nonNumeric = await api('GET', '/api/posts/abc', { token: A.token })
  row('非数字 ID GET /api/posts/abc', '400', `${nonNumeric.code} ${nonNumeric.message}`, nonNumeric.code === 400)

  console.log('\n【7】取消关注未关注的用户（对照 BUG-001 相关路径）')
  const un = await api('DELETE', `/api/users/${B.id}/follow`, { token: A.token })
  row('取关未关注者', '404', `${un.code} ${un.message}`, un.code === 404)

  const failed = rows.filter((r) => !r.pass)
  console.log('\n' + '='.repeat(72))
  console.log(`共 ${rows.length} 项，通过 ${rows.length - failed.length}，不符预期 ${failed.length}`)
  failed.forEach((f) => console.log(`  ✗ ${f.name}  期望=${f.expected} 实际=${f.actual}`))
  console.log('='.repeat(72))
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(3) })
