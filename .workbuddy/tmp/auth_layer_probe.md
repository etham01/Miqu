# 认证层 / 业务层边界实测（auth_layer_probe.mjs）

生成时间：2026-09-17T05:05:07.543Z
被逻辑删除的用户：id=28 username=qadel7c2631a457
直接改库的行：28	1	1（无删除用户接口，只能改库）

## [A0] 删除**前**，本人 token 访问 /users/me
- 期望：200
- 实测：200 success
- 判定：PASS

## [A1] 已删除账号的旧 Token 访问受保护端点 GET /users/me
- 期望：401 UNAUTHORIZED（由**拦截器**给出；Service 里 requireActiveUser 的 404 分支不可达）
- 实测：401 未登录或登录状态已失效
- 判定：PASS

## [A2] 已删除账号的旧 Token 访问**白名单**端点
- 期望：200（按游客放行，坏 Token 在白名单上不报错）
- 实测：200 success
- 判定：PASS

## [A3] 查看已删除用户主页 GET /users/{id}（requireVisibleUser）
- 期望：404 USER_NOT_FOUND
- 实测：404 用户不存在
- 判定：PASS

## [A4] 关注已删除用户（**路径参数** userId → requireActiveUser）
- 期望：404 USER_NOT_FOUND —— 此处 404 **可达**，与 A1 形成对照
- 实测：404 用户不存在
- 判定：PASS

## [A5] 给已删除用户发私信（requireActiveUser(receiverId)）
- 期望：404 USER_NOT_FOUND
- 实测：404 用户不存在
- 判定：PASS

## [A6] 举报已删除用户（resolveTargetOwner → requireVisibleUser）
- 期望：404 USER_NOT_FOUND
- 实测：404 用户不存在
- 判定：PASS

## [A7] 用错方法/错路径访问 admin 资源（404 路由）
- 期望：404 或 405（HTTP 仍 200）
- 实测：405 请求方法不支持：POST
- 判定：INFO
