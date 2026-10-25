# Memoh Cloud 登录、团队与开源契约

核对日期：2026-10-23（Asia/Shanghai）。这是当前目标部署的协议记录，不是已登录端到端验收报告。

## 开源代码的作用

固定业务契约来自 `felinics/Memoh@f3e8199560d42109be4b63bfc82ac62f260a66e2`。开源 Go handlers、SDK、Web 与 Desktop 是 Bot、会话、文件、附件、事件游标和运行时协议的主要参考，不应跳过。

同时应区分这一固定版本与线上 Cloud 的平台适配：

- [固定版本 API 客户端](https://github.com/felinics/Memoh/blob/f3e8199560d42109be4b63bfc82ac62f260a66e2/apps/web/src/lib/api-client.ts) 的 `sdkAuthQuery` / `authToken` 从本地存储读取 token；请求拦截器添加 Bearer；`sdkApiBaseUrl` 的默认值是 `/api`。Desktop 可以配置直接连接的服务地址。
- 2026-10-23 的线上客户端则将账户平台与团队业务分为 `/api/v1` 和 `/api/memoh`，使用 Cookie 和 socket ticket。

这证明固定开源版本与目标部署存在差异，不能用它证明 Cloud 的全部认证协议；也不能因为在这个版本没找到某个头，就断言相关实现闭源。

Android 地址解析因此区分两种输入：官方 Cloud 根地址使用平台/业务分开的前缀；普通自托管 API 根地址完整保留，包括反向代理的路径与端口，认证和业务共同使用这个根。显式输入 `/api/memoh` 业务网关根则选择 Cloud 式分流。自托管登录按 OSS handler 发送 `username` / `password`，包括用户名本身是邮箱的情况；后续使用 Bearer，不强行追加团队或 ticket。

## 线上客户端的直接证据

| 模块 | 可核对的行为 |
| --- | --- |
| [team-B6z5R7pp.js](https://app.memoh.net/assets/team-B6z5R7pp.js) | 无团队头调用 `/teams`；解析 `teams[].team.team_id` 和外层 `role`；优先已选有效团队，否则第一项；切换时清理用户范围的聊天状态 |
| [api-client-DPB1Zklm.js](https://app.memoh.net/assets/api-client-DPB1Zklm.js) | 平台前缀 `/api/v1`、业务前缀 `/api/memoh`；请求头 `X-Team-ID`；URL 查询 `team_id`；带团队申请 `/ws-tickets`；socket 追加 `ticket` |
| [user-DjQ2eYlW.js](https://app.memoh.net/assets/user-DjQ2eYlW.js) | 平台账户从无团队头的 `/api/v1/users/me` 获取，和业务 `/users/me` 各有用途 |

这些资源带构建 hash，未来可能替换或失效；升级时要重新核对目标部署。

## 请求顺序与身份边界

1. 读取 `/api/v1/auth/config`，按部署支持的登录方式展示表单。
2. 邮箱码流程使用 `/api/v1/auth/email-code/send` 和 `/verify`，接收服务端 Cookie。存在任意 Cookie 本身不能证明已认证，应以认证后端点成功响应为准。
3. 无团队头读取 `GET /api/v1/teams`，解析成员关系：

```json
{
  "teams": [
    {
      "team": { "team_id": "<server-provided-id>", "name": "<team-name>" },
      "role": "TEAM_ROLE_OWNER"
    }
  ]
}
```

4. 验证已选团队仍在当前账户的列表中，否则选第一项；没有团队时显示明确状态，不发缺团队的业务请求。
5. 普通 JSON、multipart、文件下载及备份请求均使用这个团队上下文，不能只修 Bot 列表。
6. `POST /api/v1/ws-tickets` 带 `X-Team-ID`；业务 socket URL 带 `team_id` 和新 ticket。ticket 创建成功不等于 WebSocket 握手或消息往返成功。
7. 切换团队时清理 Bot、会话、草稿、Agent 与 socket；迟到的 HTTP/socket 结果不能写回新的团队。退出和更换服务器时也需要隔离 Cookie 与团队状态。

HTTP 头名大小写无关，因此 `X-Team-ID` 与 `X-Team-Id` 不是两种协议。未认证的 401 只证明认证层拒绝请求，不能拿来确认路由是否存在。

## 本地探针和验证边界

[探针说明](../tools/memoh-probe/README.md) 提供两种真实会话使用方式：网页同源执行（不导出 Cookie），或读取已有 cURL 抓包文本（不执行抓包命令）。默认只 GET；显式 `--ticket` 才创建临时 socket 凭据。输出只保留诊断元数据。

探针的本地 HTTP 回归已经覆盖真实嵌套团队响应、缺头负面对照、带头及查询参数成功、ticket 作用域、空团队/失败停止、cURL 解析、浏览器同源执行与输出脱敏。它是测试服务，不是线上 Cloud 已登录成功的证据。

2026-10-23 本轮匿名线上探针得到 auth/config 200、users/me 401；侧栏浏览器现有会话同样是 401。没有据此继续猜测团队业务结果，也没有导出或搜索用户凭据。

## Google 与桌面交接的核对结果

- 固定开源版本的 [auth handler](https://github.com/felinics/Memoh/blob/f3e8199560d42109be4b63bfc82ac62f260a66e2/internal/handlers/auth.go#L50) 注册 `/auth/login`、`/auth/refresh`，返回 JWT；同版本 [登录页](https://github.com/felinics/Memoh/blob/f3e8199560d42109be4b63bfc82ac62f260a66e2/apps/web/src/pages/login/index.vue) 明确使用用户名/密码流程。
- 当前 Cloud 的 [登录模块](https://app.memoh.net/assets/login-ASUEUbi5.js) 与 [auth 模块](https://app.memoh.net/assets/auth-B18BanQO.js) 将 Google 登录导航到 `/api/v1/auth/oauth/google/login?return_to=...`；返回后通过 `/api/v1/users/me` 检查 Cookie 会话，处理 `login=mfa` / `login=error`。
- [Cloud 桌面标记模块](https://app.memoh.net/assets/desktop-shell-beJShJmm.js) 识别 `desktopShell` 查询参数或 `MemohCloudDesktop` user-agent。已检查模块未提供足以确认原生客户端凭据兑换的契约。
- 固定开源 Electron 壳的 [导航策略](https://github.com/felinics/Memoh/blob/f3e8199560d42109be4b63bfc82ac62f260a66e2/apps/desktop/src/main/external-links.ts) 将外部链接交给系统浏览器；这不能证明当前 Cloud Desktop 的认证交接完全相同。

- 2026-10-23 通过网页同源请求实测：`/api/v1/auth/config` 返回 200 / `email`，OAuth providers 返回 200，列出 Google 和 GitHub；现有侧栏会话的 `/api/v1/users/me` 返回 401。
- [公共 OAuth 探针](../tools/memoh-probe/README.md) 不使用用户 Cookie、不跟随跳转：不传 `return_to` 返回 400；同源主页和同源 `?desktopShell=1` 均 302 到 Google，含 state 和 S256 PKCE；测试的 `memoh://oauth/callback`、localhost 和外域对照均 400。这里只核实授权入口，尚未完成登录或最终回调。
- 公开 chunk [desktop-shell-beJShJmm.js](https://app.memoh.net/assets/desktop-shell-beJShJmm.js)（2026-10-23）只含桌面环境检测：URL 带 `desktopShell` 参数或 UA 含 `MemohCloudDesktop` 即标记为桌面；chunk 内没有 scheme、loopback、code 兑换或 cookie 交接逻辑。登录页（2026-10-23 截图）只有 GitHub、Google、邮箱验证码三个入口，没有桌面客户端下载链接。

Google 登录仍未在 Android 实现，也未实际完成 OAuth。测试若干外域/自定义回调被拒绝，只说明这些回调不被接受；不能推出官方桌面协议不存在或所有原生登录方式都不可能。当前公开可查的桌面证据只有环境检测标记（`desktopShell` 参数、`MemohCloudDesktop` UA），没有可依赖的原生交接实现：允许的回调、一次性 code/PKCE 兑换接口、以及浏览器返回客户端方式均未见公开代码；不要以“打开网页”冒充完整原生登录。

## 2026-10-23 真实会话探针（洛铃配合登录）

洛铃在侧栏浏览器完成邮箱验证码登录后，同源探针一次通过：

- `result: scoped_business_ok`；1 个团队（`TEAM_ROLE_OWNER`），`selection: saved_membership`。
- `auth/config`、`users/me`、`teams` 均 200；负面缺团队对照复现成功（`missingTeamReproduced: true`）；`X-Team-ID` 头与 `team_id` 查询两种变体都拿到 Bot 列表；`POST /api/v1/ws-tickets` 返回非空 ticket。
- 只读补充盘点：Bot 1 个；会话列表 200（50 条分页）；会话详情 200，字段含 `runtime_type`、`session_mode`、`preferred_chat_model_id` 等；历史 `/bots/{id}/messages?session_id=…` 200（`items[]`，user 有 `text`，assistant 有 `messages` 块数组），`before_message_id` 翻页 200；`/schedule` 200；`/agents` 200；`container/fs/list` 200 返回 Linux 根目录结构。全程只读，未发送用户消息。

## 线上 Web 前端的 WS 协议（chunk 逆向，2026-10-23）

对 108 个构建 chunk 静态分析（`app/build/webapp-chunks/`，一次性下载的参考材料，已删除）。聊天实时链路只走 WebSocket，关键事实：

- 连接：`GET {wss}/api/memoh/bots/{bot_id}/web/ws?team_id=<selected>&ticket=<one-time>`；`team_id` 由请求拦截器按当前团队自动注入，ticket 先经 `POST /api/v1/ws-tickets`（带团队头）换取，然后追加为查询参数；HTTPS→`wss:`。
- 发送去重键：`message` / `retry_message` / `edit_message` → `invocation:{invocation_id}`；`abort` / `tool_approval_response` / `user_input_response` → `control:{control_id}`。收到 `run_accepted` / `run_rejected` / `error` / `command_result` / `command_error` 时按 `invocation_id` 清除待确认，`control_ack` 按 `control_id` 清除。
- 首条消息超时 30s（`Mr=30000`）：`run_accepted` 未到则视为失败并自动 abort 该 run；重连退避从 1s 起 ×1.5，上限 10s。
- 事件白名单：`run_accepted`、`model_preference_settled`、`run_rejected`、`error`、`session_created`、`command_result`、`command_error`、`runtime_snapshot`、`runtime_delta`、`runtime_dropped`、`control_ack`，其余丢弃。
- 会话创建：不带 `session_id` 发消息 → 服务器回 `session_created{session_id, invocation_id}`；客户端把 `session_id` 回填进挂起的 message 重发语义，并订阅 runtime。
- delta 归并：`delta.current_run_view` 整体替换优先；`reset_messages` 清空；`user_turn_upserts` / `steer_turn_upserts` / `steer_turn_removals` 维护轮次；`message_appends` 按 `id+type` 定位，`text` / `reasoning` 类型追加 `content`；`progress_appends` 追加 tool 的 `progress` 与可选 `input`；`message_upserts` 按 `id`（tool 另按 `tool_call_id`）覆盖；最后按 `id` 排序。
- run 状态集合（活动态）：`admitting`、`running`、`waiting_decision`、`aborting`、`finishing`——与 Android 端 `settleIfTerminal` 的集合完全一致。
- 决策响应载荷：`tool_approval_response{session_id, run_id, decision_id, control_id, decision, option_id, reason?}`；`user_input_response{session_id, run_id, decision_id, control_id, answers[], canceled?, reason?}`——与 Android `decide` / `answerUserInput` 字段一致。
- `SDK` 里有 `GET /bots/{bot_id}/sessions/events` 的 SSE 封装，但导出名（`Gt`）没有任何 chunk 导入——当前 web 包里是未接线的死代码，聊天不依赖 SSE；Android 不需要实现它。

## Android 端协议对齐结论（2026-10-23 对照）

- 一致：WS URL 组装、ticket 交换、`invocation_id` / `control_id` 去重语义、`session_created` 回填、`runtime_subscribe` / `runtime_unsubscribe`、epoch+seq 游标与快照重同步、活动态集合、审批/用户输入载荷、30s 级首条确认（Android 用 `awaitingAdmission` + 取消恢复草稿实现）。
- 仍未对齐：`composer_scope` 多视图、`requested_skills` / `workspace_target_id` 发送参数，以及 slash command 的派发和 `command_result` / `command_error` 结果呈现。Android 已发送 `model_id` / `reasoning_effort`、处理 `model_preference_settled`，并归并 user/steer 轮次增删与 progress delta。
- 重连退避：web 从 1s ×1.5 到 10s 封顶；Android 固定 2s 重试一次。可作为后续改进项，不是正确性缺陷。
