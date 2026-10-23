# Memoh Cloud 本地协议探针

核对真实 Cloud 请求顺序和团队作用域，运行需要 Node.js 20+。探针只返回状态、数量和固定诊断标签，不输出 Cookie、Authorization、邮箱、团队 ID、Bot 内容或 WebSocket ticket。

## 已核实的网页协议

2026-10-23 从线上部署的以下模块直接核对：

- [团队 store](https://app.memoh.net/assets/team-B6z5R7pp.js)：无团队头调用 `GET /api/v1/teams`，响应为 `{ teams: [{ team: { team_id, name, ... }, role }] }`；优先保留仍属于当前账户的已选团队，否则选择第一项。
- [HTTP 客户端](https://app.memoh.net/assets/api-client-DPB1Zklm.js)：平台根 `/api/v1`，业务根 `/api/memoh`；业务调用带 `X-Team-ID`；WebSocket URL 带 `team_id`，申请 `/api/v1/ws-tickets` 时也带团队头。
- [平台用户模块](https://app.memoh.net/assets/user-DjQ2eYlW.js)：`GET /api/v1/users/me` 是无团队头的账户请求。

HTTP 请求头不区分大小写，`X-Team-ID` 和 `X-Team-Id` 是同一个头。未登录返回 401 只说明请求被认证层拒绝，不能证明相应路由存在。这两点纠正了第一版探针的错误假设。

## 不需要会话的本地自检

在仓库根目录运行：

```powershell
node --test tools/memoh-probe/memoh-probe.test.mjs tools/memoh-probe/oauth.test.mjs
node tools/memoh-probe/memoh-probe.mjs --anonymous
```

测试使用本地 HTTP 服务复现真实嵌套团队响应和缺团队时的 400，然后验证团队头、查询参数、ticket、过期团队回退及输出脱敏。匿名线上探针预期停在 `not_authenticated`，这不属于已登录链路验证。

## 不登录也能核对 Google 入口

```powershell
node tools/memoh-probe/memoh-probe.mjs --oauth
```

此模式不读取抓包、不带用户 Cookie，也不跟随 Google 跳转；只核对 HTTP 状态、是否开始 Google 授权、state 是否存在及 PKCE 方法。响应体、Cookie、Location 和 state 值均不输出。

2026-10-23 对 `app.memoh.net` 实测：

| `return_to` 对照 | 状态 | 直接观察 |
| --- | --- | --- |
| 不传 | 400 | 请求被拒绝 |
| 同源主页 | 302 | 进入 Google 授权，含 state 和 S256 PKCE |
| 同源 `?desktopShell=1` | 302 | 进入 Google 授权，含 state 和 S256 PKCE |
| 测试的 `memoh://oauth/callback` | 400 | 请求被拒绝 |
| 测试的 localhost 回调 | 400 | 请求被拒绝 |
| 外域负面对照 | 400 | 请求被拒绝 |

这只能判断授权入口的行为。302 不代表完成 Google 登录、完成最终回调或取得原生客户端凭据；若干回调被拒绝也不代表所有原生交接方式都不存在。报告始终标记 `authenticationCompleted: false`。

## 使用网页现有登录，不导出 Cookie

先在 Cloud 网页登录，再生成同源浏览器探针：

```powershell
node tools/memoh-probe/memoh-probe.mjs --browser | Set-Clipboard
```

在 `https://app.memoh.net` 页面的开发者工具 Console 粘贴执行。浏览器通过 `credentials: include` 使用现有会话，读取已选团队的 `memoh.current_team_id` 并核对它仍在团队列表中。脚本不读取或输出 Cookie，也不更改团队选择。

默认只有 GET 请求。如果还需要检查短期 socket 凭据申请，生成时显式加 `--ticket`：

```powershell
node tools/memoh-probe/memoh-probe.mjs --browser --ticket | Set-Clipboard
```

`--ticket` 会增加一次带团队头的 `POST /api/v1/ws-tickets`。它创建临时凭据；探针只报告是否返回了非空 ticket，不输出值，也不打开 socket。

## 可选：已有 cURL 抓包

支持 Chrome 的 bash/cmd 格式 Copy as cURL。脚本只提取 Cookie、Authorization 和已选团队头，不执行抓包里的命令，也不发送其中的业务请求体。

将抓包保存在本目录 `curl.txt`，然后运行：

```powershell
node tools/memoh-probe/memoh-probe.mjs
# 或者指定已有的抓包路径
node tools/memoh-probe/memoh-probe.mjs --session-file tools/memoh-probe/curl.txt
```

此输入包含会话凭据，已加入 `.gitignore`。不要把输入文件发到聊天或提交到仓库；使用完后删除自己的抓包。诊断输出可以分享。

## 如何解读

| 输出 | 含义 |
| --- | --- |
| `not_authenticated` | 无有效登录；停止团队和业务请求，不判断路由是否存在 |
| `teams_failed` / `unexpected_team_shape` | 团队列表未成功获取或形状不符；不继续请求 Bot |
| `no_teams` | 账户没有有效团队；探针不创建团队 |
| `missingTeamReproduced: true` | 不带团队的负面对照复现了原错误 |
| `scoped_business_ok` | 带团队头获取到了符合形状的 Bot 列表，可为空列表 |
| `queryVariantSucceeded` | `team_id` 查询参数变体是否也成功 |
| `ticketPresent` | 显式启用后是否申请到了短期 socket 凭据 |
| `socketHandshakeVerified: false` | 探针未做 WebSocket 握手或收发消息 |

只接受官方 Cloud 地址和本地回环测试地址，拒绝把抓包凭据发到其他远程来源。默认 GET 诊断不持久化任何响应。Google/桌面授权交接是独立协议，本探针不据回调拒绝或 401 推导它是否可用。
