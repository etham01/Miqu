/**
 * 种子头像可达性核查（只读）。
 *
 * 目的：确认 DB 里那些外链头像（i.pravatar.cc）在当前网络下**是否真的能取到图片**。
 * 背景：bash 循环里跑 curl 会因沙箱写输出失败而误报 0 字节，必须用单进程顺序验证。
 *
 * 用法：NO_PROXY=localhost,127.0.0.1 node check_seed_avatars.mjs
 */
const API = 'http://127.0.0.1:8081'

const main = async () => {
  // 用管理员接口拿到用户列表太长，这里直接读 DB 不方便 —— 改用遍历种子用户主页接口
  // 更简单：直接问后端要 avatar 字段。用 test001 登录后逐个查 /api/users/{id}
  const login = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'test001', password: '123456' }),
  }).then((r) => r.json())
  const token = login.data.token

  const avatars = []
  for (let id = 1; id <= 21; id += 1) {
    const res = await fetch(`${API}/api/users/${id}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).then((r) => r.json())
    if (res.code === 200 && res.data?.avatar) avatars.push({ id, url: res.data.avatar })
  }

  console.log('='.repeat(74))
  console.log(`种子用户头像可达性核查（共 ${avatars.length} 个）`)
  console.log('='.repeat(74))

  let ok = 0
  let bad = 0
  for (const { id, url } of avatars) {
    try {
      const r = await fetch(url)
      const buf = Buffer.from(await r.arrayBuffer())
      const good = r.status === 200 && buf.length > 1000
      good ? (ok += 1) : (bad += 1)
      console.log(
        `  [${good ? 'OK ' : 'BAD'}] id=${String(id).padEnd(3)} ${url.padEnd(42)} http=${r.status} ${String(buf.length).padStart(6)}字节 ${r.headers.get('content-type')} magic=${buf.slice(0, 4).toString('hex')}`,
      )
    } catch (e) {
      bad += 1
      console.log(`  [BAD] id=${String(id).padEnd(3)} ${url.padEnd(42)} 取数异常: ${e.message}`)
    }
  }

  console.log(`\n  汇总：有效 ${ok} / 异常 ${bad}`)
  console.log('='.repeat(74))
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(3) })
