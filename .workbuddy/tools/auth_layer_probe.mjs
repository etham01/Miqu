/**
 * auth_layer_probe.mjs —— 验证「认证层先于业务层」在**账号被逻辑删除**时的具体表现。
 *
 * ⚠️ 为什么必须单进程跑完并把结果写文件：
 *   本机 bash 工具的输出管道会**截断/损坏长字符串**（180 字符的 JWT 打印出来只剩 22 字符），
 *   所以 token 绝不能经 shell 变量/参数中转。本脚本自己在进程内用 child_process 执行 SQL。
 *
 * 用法：先起后端 8081，再
 *   NO_PROXY=localhost,127.0.0.1 node .workbuddy/tools/auth_layer_probe.mjs
 * 结果写入 .workbuddy/tmp/auth_layer_probe.md
 */
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';

const BASE = process.env.MIQU_BASE_URL || 'http://localhost:8081';
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 9.4/bin/mysql.exe';
const out = [];
const log = (id, q, expect, actual, verdict) => out.push({ id, q, expect, actual, verdict });

async function call(method, path, { token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  let p;
  try { p = await res.json(); } catch { return { http: res.status, code: -1, message: '<非JSON>' }; }
  return { http: res.status, code: p.code, message: p.message, data: p.data };
}

const sql = (q) => execSync(`"${MYSQL}" -uroot -proot -N -e "${q}"`, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }).trim();

async function register(prefix) {
  const username = `${prefix}${Math.random().toString(16).slice(2, 12)}`;
  await call('POST', '/api/auth/register', {
    body: { username, password: '123456', nickname: `探针${username.slice(-4)}`, email: `${username}@probe.test` },
  });
  const login = await call('POST', '/api/auth/login', { body: { username, password: '123456' } });
  return { id: Number((await call('POST', '/api/auth/login', { body: { username, password: '123456' } })).data.user.id), username, token: login.data.token };
}

// ⚠️ 注意：registration 的 UserVO.id 是字符串；这里统一转 number 便于拼 SQL
const victim = await register('qadel');
victim.id = Number(sql(`SELECT id FROM miqu.user WHERE username='${victim.username}';`));

const t1 = await call('POST', '/api/auth/login', { body: { username: 'test001', password: '123456' } });
const t1Token = t1.data.token;
const admin = await call('POST', '/api/auth/login', { body: { username: 'admin', password: '123456' } });

// ── 先确认删除前一切正常 ──────────────────────────────
{
  const r = await call('GET', '/api/users/me', { token: victim.token });
  log('A0', '删除**前**，本人 token 访问 /users/me', '200', `${r.code} ${r.message}`,
    r.code === 200 ? 'PASS' : 'FAIL');
}

// ── 逻辑删除该账号（项目没有删除用户的接口，只能直接改库）────────
sql(`UPDATE miqu.user SET deleted=1 WHERE id=${victim.id};`);
const row = sql(`SELECT id, status, deleted FROM miqu.user WHERE id=${victim.id};`);
console.log('# 被逻辑删除的用户行: id / status / deleted = ' + row);

{
  const r = await call('GET', '/api/users/me', { token: victim.token });
  log('A1', '已删除账号的旧 Token 访问受保护端点 GET /users/me',
    '401 UNAUTHORIZED（由**拦截器**给出；Service 里 requireActiveUser 的 404 分支不可达）',
    `${r.code} ${r.message}`, r.code === 401 ? 'PASS' : 'CHECK');
}
{
  const r = await call('GET', '/api/posts?page=1&size=1', { token: victim.token });
  log('A2', '已删除账号的旧 Token 访问**白名单**端点',
    '200（按游客放行，坏 Token 在白名单上不报错）', `${r.code} ${r.message}`,
    r.code === 200 ? 'PASS' : 'FAIL');
}
{
  const r = await call('GET', `/api/users/${victim.id}`, { token: t1Token });
  log('A3', '查看已删除用户主页 GET /users/{id}（requireVisibleUser）',
    '404 USER_NOT_FOUND', `${r.code} ${r.message}`, r.code === 404 ? 'PASS' : 'FAIL');
}
{
  const r = await call('POST', `/api/users/${victim.id}/follow`, { token: t1Token });
  log('A4', '关注已删除用户（**路径参数** userId → requireActiveUser）',
    '404 USER_NOT_FOUND —— 此处 404 **可达**，与 A1 形成对照', `${r.code} ${r.message}`,
    r.code === 404 ? 'PASS' : 'FAIL');
}
{
  const r = await call('POST', '/api/messages', { token: t1Token, body: { receiverId: victim.id, content: 'hi' } });
  log('A5', '给已删除用户发私信（requireActiveUser(receiverId)）',
    '404 USER_NOT_FOUND', `${r.code} ${r.message}`, r.code === 404 ? 'PASS' : 'FAIL');
}
{
  const r = await call('POST', '/api/reports', { token: t1Token, body: { targetType: 1, targetId: victim.id, reasonType: 1 } });
  log('A6', '举报已删除用户（resolveTargetOwner → requireVisibleUser）',
    '404 USER_NOT_FOUND', `${r.code} ${r.message}`, r.code === 404 ? 'PASS' : 'FAIL');
}
{
  const r = await call('POST', '/api/admin/users', { token: admin.data.token, body: {} });
  log('A7', '用错方法/错路径访问 admin 资源（404 路由）', '404 或 405（HTTP 仍 200）',
    `${r.code} ${r.message}`, 'INFO');
}

const md = ['# 认证层 / 业务层边界实测（auth_layer_probe.mjs）', '',
  `生成时间：${new Date().toISOString()}`,
  `被逻辑删除的用户：id=${victim.id} username=${victim.username}`,
  `直接改库的行：${row}（无删除用户接口，只能改库）`, ''];
for (const r of out) md.push(`## [${r.id}] ${r.q}`, `- 期望：${r.expect}`, `- 实测：${r.actual}`, `- 判定：${r.verdict}`, '');

mkdirSync('.workbuddy/tmp', { recursive: true });
writeFileSync('.workbuddy/tmp/auth_layer_probe.md', md.join('\n'), 'utf8');
console.log('written: .workbuddy/tmp/auth_layer_probe.md');
