/**
 * validation_order_probe.mjs —— 实测「分层校验顺序」，为 test_knowledge/rules/validation_order.yaml 取证。
 *
 * 要回答的核心问题（不能靠推理，必须实测）：
 *   1. 拦截器层（401/423）与 Controller 参数校验层（400）谁先？
 *   2. 禁用账号的旧 Token，访问白名单端点时的行为？
 *   3. Bean Validation(400) 与 Service 业务校验(400/403) 谁先？
 *   4. 通知的"删除"是逻辑删除还是物理删除（影响 SQL 断言）？
 *
 * 用法：先起后端 8081，再
 *   NO_PROXY=localhost,127.0.0.1 node .workbuddy/tools/validation_order_probe.mjs
 */
const BASE = process.env.MIQU_BASE_URL || 'http://localhost:8081';
const results = [];

async function call(method, path, { token, body, raw } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const http = res.status;
  if (raw) return { http, text: await res.text() };
  let payload;
  try { payload = await res.json(); } catch { return { http, code: -1, message: '<非 JSON>' }; }
  return { http, code: payload.code, message: payload.message, data: payload.data };
}

function record(id, question, expected, actual, verdict, note) {
  results.push({ id, question, expected, actual, verdict, note });
}

async function register(prefix) {
  const username = `${prefix}${Math.random().toString(16).slice(2, 12)}`;
  const r = await call('POST', '/api/auth/register', {
    body: { username, password: '123456', nickname: `探针${username.slice(-4)}`, email: `${username}@probe.test` },
  });
  if (r.code !== 200) throw new Error(`注册失败 ${username}: ${r.code} ${r.message}`);
  const login = await call('POST', '/api/auth/login', { body: { username, password: '123456' } });
  return { id: r.data.id, username, token: login.data.token };
}

const adminLogin = await call('POST', '/api/auth/login', { body: { username: 'admin', password: '123456' } });
const adminToken = adminLogin.data.token;
const t1 = await call('POST', '/api/auth/login', { body: { username: 'test001', password: '123456' } });

// ── P1 活跃用户 + 空 avatar（期望 Bean Validation 先命中 → 400）────────────
{
  const r = await call('PUT', '/api/users/me/avatar', { token: t1.data.token, body: { avatar: '' } });
  record('P1', '活跃用户 + avatar 为空', '400 PARAM_INVALID', `${r.code} ${r.message}`,
    r.code === 400 ? 'PASS' : 'FAIL');
}

// ── P2 活跃用户 + 外链 avatar（期望 Service 前缀校验 → 400 INVALID_IMAGE_URL）──
{
  const r = await call('PUT', '/api/users/me/avatar', { token: t1.data.token, body: { avatar: 'https://evil.example/x.png' } });
  record('P2', '活跃用户 + 外链 avatar', '400 图片地址不合法…', `${r.code} ${r.message}`,
    r.code === 400 ? 'PASS' : 'FAIL');
}

// ── P3 禁用账号的旧 Token + 空 avatar（顺序之争：423 还是 400？）──────────
const victim = await register('qaord');
{
  const disable = await call('PUT', `/api/admin/users/${victim.id}/status`, { token: adminToken, body: { status: 0 } });
  if (disable.code !== 200) throw new Error(`禁用失败: ${disable.code} ${disable.message}`);

  const badBody = await call('PUT', '/api/users/me/avatar', { token: victim.token, body: { avatar: '' } });
  record('P3', '禁用账号旧 Token + 空的非法 body（拦截器 vs Bean Validation）',
    '先验：423 USER_DISABLED', `${badBody.code} ${badBody.message}`, null,
    '这是本探针要回答的核心问题');

  const goodBody = await call('PUT', '/api/users/me/avatar', {
    token: victim.token, body: { avatar: '/uploads/image/2026/09/x.jpg' },
  });
  record('P4', '禁用账号旧 Token + 合法 body', '423 USER_DISABLED', `${goodBody.code} ${goodBody.message}`,
    goodBody.code === 423 ? 'PASS' : 'FAIL');

  const pub = await call('GET', '/api/posts?page=1&size=1', { token: victim.token });
  record('P5', '禁用账号旧 Token 访问**白名单**端点', '200（按游客放行，不报错）',
    `${pub.code} ${pub.message}`, pub.code === 200 ? 'PASS' : 'FAIL');

  const me = await call('GET', '/api/users/me', { token: victim.token });
  record('P6', '禁用账号旧 Token 访问**受保护**端点', '423 USER_DISABLED', `${me.code} ${me.message}`,
    me.code === 423 ? 'PASS' : 'FAIL');
}

// ── P7 非互关 + 空 content（Bean Validation vs 互关校验）─────────────
{
  const a = await register('qaord');
  const b = await register('qaord');
  const r = await call('POST', '/api/messages', { token: a.token, body: { receiverId: b.id, content: '' } });
  record('P7', '非互关 + 空 content', '400 PARAM_INVALID（DTO 先于互关）', `${r.code} ${r.message}`,
    r.code === 400 ? 'PASS' : 'FAIL');

  const r2 = await call('POST', '/api/messages', { token: a.token, body: { receiverId: b.id, content: 'hi' } });
  record('P8', '非互关 + 合法 content', '403 NOT_MUTUAL_FOLLOW', `${r2.code} ${r2.message}`,
    r2.code === 403 ? 'PASS' : 'FAIL');

  const r3 = await call('POST', '/api/messages', { token: a.token, body: { receiverId: a.id, content: 'hi' } });
  record('P9', '给自己发 + 非互关状态', '400 不能给自己发送私信', `${r3.code} ${r3.message}`,
    r3.code === 400 ? 'PASS' : 'FAIL');
}

// ── P10 举报 targetType 越界（Bean Validation @Max(3) vs Service INVALID_TARGET_TYPE）──
{
  const r = await call('POST', '/api/reports', {
    token: t1.data.token, body: { targetType: 4, targetId: 1, reasonType: 1 },
  });
  record('P10', '举报 targetType=4（@Max(3) 与 Service 分支之争）',
    '400 PARAM_INVALID（@Max 先，Service 的 INVALID_TARGET_TYPE 不可达）',
    `${r.code} ${r.message}`, r.code === 400 ? 'PASS' : 'FAIL');
}

// ── P11 管理员改自己状态 + 非法 status（Bean Validation vs CANNOT_OPERATE_SELF）──
{
  const r = await call('PUT', '/api/admin/users/1/status', { token: adminToken, body: { status: 9 } });
  record('P11', '管理员改自己状态 + status=9', '400 PARAM_INVALID（Bean Validation 先）',
    `${r.code} ${r.message}`, r.code === 400 ? 'PASS' : 'FAIL');

  const r2 = await call('PUT', '/api/admin/users/1/status', { token: adminToken, body: { status: 0 } });
  record('P12', '管理员禁用自己 + 合法 status', '400 不能对自己执行该操作', `${r2.code} ${r2.message}`,
    r2.code === 400 ? 'PASS' : 'FAIL');
}

// ── P13 通知撤回：逻辑删除还是物理删除？（决定 SQL 断言写法）──────────
{
  const x = await register('qaord');
  const y = await register('qaord');
  await call('POST', `/api/users/${y.id}/follow`, { token: x.token });
  const before = await call('GET', `/api/messages/unread-count`, { token: y.token });
  await call('DELETE', `/api/users/${y.id}/follow`, { token: x.token });
  const after = await call('GET', '/api/notifications?page=1&size=50', { token: y.token });
  const stillVisible = (after.data?.list || []).some((n) => n.type === 1);
  record('P13', '取消关注后，关注通知是否还在列表里', '不在（已撤回）', stillVisible ? '仍在' : '已消失',
    stillVisible ? 'FAIL' : 'PASS', `临时用户 x=${x.id} y=${y.id}（供后续 SQL 核对 deleted 列）`);
  results.push({ id: 'P13.sql_hint', question: 'SQL 核对逻辑删除', expected: 'deleted=1 的行仍存在',
    actual: `SELECT id,deleted FROM miqu.notification WHERE user_id=${y.id} AND actor_id=${x.id} AND type=1;`, verdict: 'MANUAL' });
}

console.log('\n================ 分层校验顺序实测 ================');
for (const r of results) {
  console.log(`\n[${r.id}] ${r.question}`);
  console.log(`  期望: ${r.expected}`);
  console.log(`  实测: ${r.actual}`);
  if (r.verdict) console.log(`  判定: ${r.verdict}`);
  if (r.note) console.log(`  备注: ${r.note}`);
}
console.log('\n=================================================');
