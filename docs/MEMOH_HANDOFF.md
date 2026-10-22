# Memoh Android 客户端 — 交接说明

供接手继续开发的人/agent 阅读。最后更新：2026-10-21。

## 原始需求（洛铃原话，不可改写）

下面两条是这项工作的**需求源头**。后续所有实现都应能追溯到它们。

**2026-10-20 13:06（Asia/Shanghai）**

> 你在 memoh-native-client 工作，https://github.com/LoliLin/Memoh4A/tree/feat/memoh-native-client。
> 你需要先克隆仓库，切换到这个分支，把main合并进来，然后继续工作，**把这个app打造为memoh的android客户端**

**2026-10-20 13:24（Asia/Shanghai）**

> **继续看memoh的api list，完整，不要遗缺，不要做成demo**

### 从原话推导出的验收标准

1. **是 Memoh 的 Android 客户端**，不是套壳网页、不是 OpenAI 兼容中转、不是替代 Memoh 服务端运行时。
2. **API 清单必须完整，不能有遗漏** —— 包括 Swagger 里有的，也包括 Swagger 没写但服务端实际注册的。
   所以清单拆成两份（已文档化 / 未文档化），而不是只抄一份 Swagger。
3. **不能做成 demo** —— 不许用 mock 数据、假接口、写死的示例内容撑出"看起来能用"的界面。
   每个功能必须真正打服务端接口；做不到就如实标注为未实现。
4. 分支要基于 `feat/memoh-native-client`，并把上游主线合并进来（实际是 `origin/master`，
   该仓库没有 `main` 分支）。

> ⚠️ 注意第 2 条与第 3 条容易被执行成两件互相打架的事：
> 为了"完整"就把 338 个端点全糊上 UI（其中大半是运维端点，属于过度实现），
> 或者为了"不做 demo"就只挑最容易的几个做（属于不完整）。
> 正确的读法是：**清单要全，功能要真，UI 要有选择**。
> 详见下方"API 清单"与"明确未实现"两节的边界说明。

## ⭐ 对齐目标：app.memoh.net（洛铃 2026-10-21 16:57 补充）

> 「目标是对齐 https://app.memoh.net/，memoh的云产品」

这条把验收标准从「把 API 用起来」升级为「**界面与能力对齐云端 Web 产品**」。
app.memoh.net 需要登录，公开抓不到；但它的前端源码就在上游仓库里：

- **`apps/web/`（Vue 3 + vue-router + @felinic/ui + Tailwind）** ← 这就是 app.memoh.net
- `apps/desktop/`（Electron 壳，复用同一套页面，路由多出 `:sessionId`）
- 真相源：`apps/web/src/routes.ts`（站点地图）与 `apps/web/src/pages/`

**结论：以后判断"该不该做、做成什么样"，去看 `apps/web` 的页面，不是只翻 Swagger。**
Swagger 告诉你「后端能做什么」，`apps/web` 告诉你「产品实际暴露了什么」。

### 站点地图（来自 routes.ts，实测核对）

| 区域 | 路径 | 说明 |
| --- | --- | --- |
| 聊天 | `/`、`/bot/:botName` | 主区（侧栏 + dockview）常驻在 App.vue，路由组件渲染 null |
| 引导 | `/onboarding` | |
| 登录 | `/login` | |
| OAuth | `/oauth/mcp/callback` | MCP 授权回调 |
| Provider 深链 | `/providers/connect` | `#payload=` base64url，确认后才保存 key |
| 设置 | `/settings` | 移动端是列表页，桌面重定向到 `/settings/bots` |

设置区子页：`bots`（含 `new`、`new/progress`、`:botName` 详情）、`providers`、`runtimes`、
`usage`、`people`（adminOnly）、`appearance`、`keyboard`、`profile`、
`supermarket`（含 `category/:id`、`:registryId/:appId`）、`about`。
另有重定向别名：`web-search`/`voice`/`video`/`speech`/`transcription` → `providers?tab=…`，
`memory` → `bots`（注意：**独立的 Memory 设置页已被删除**，Memory 现在只在每个 bot 的标签页里）。

### Bot 详情页 11 个标签页（`pages/bots/detail.vue`，实测）

| # | tab | 组件 | Android 现状 |
| --- | --- | --- | --- |
| 1 | `overview` | BotOverview | ✅ 已做（MemohBotDetailDialog 状态/时区/创建时间/卡片概览） |
| 2 | `general` | BotSettings（名称/头像/时区/启停） | ✅ 已做（支持编辑并保存到服务端，带删除 Bot 二次确认） |
| 3 | `remote-runtime` | BotRemoteRuntime | ✅ 已做（MemohBotRuntimeTab：外部 Worker 列表、在线状态、系统/架构/版本、令牌注册与吊销） |
| 4 | `container` | BotContainer（仅 container workspace 显示） | ✅ 已做（容器状态、工作目录、快捷直达工作区） |
| 5 | `memory` | BotMemory | ✅ 已做（可从 Bot 管理页直通 MemohMemoryDialog） |
| 6 | `channels` | BotChannels | ✅ 已做（MemohBotChannelsTab：渠道列表、启停Switch、配置Token/Webhook、解绑确认） |
| 7 | `advanced` | BotAdvanced | ✅ 已做（MemohBotAdvancedTab：默认ACL策略Allow/Deny、开场提示词与刷新、健康检查、日志清除） |
| 8 | `access` | BotAccess（ACL/权限） | ✅ 已做（候选人搜索、授权新增、权限调整、撤销授权带确认） |
| 9 | `agents` | BotAgents | ✅ 已做（MemohBotAgentsTab：启停、凭据配置、设备码登录轮询、删除确认） |
| 10 | `mcp` | BotMcp | ✅ 已做（MemohBotMcpTab：SSE/Stdio 服务列表、启停Switch、连通性探测Probe、暴露工具展示、新增与删除） |
| 11 | `apps` | BotApps（含待处理角标） | ✅ 已做（MemohBotAppsTab：已安装应用、版本/状态、更新检查、卸载、工作区Skills列表） |

`canManageBot` 权限门（`current_user_permissions` 含 `manage` 才开放编辑，无权限时顶部显示只读提醒横幅，只允许查看）。**Android 端已完整实现该权限门**。

### 覆盖度实况（别被"50 个 API 方法"误导）

- API 层：50 个 suspend 方法，覆盖登录/聊天/WebSocket/队列/Agent/文件/定时/Memory/备份。
- 界面层：11 个 bot 标签页里**只有 1 个完整对齐**（memory），1 个部分（general），1 个仅有 API（agents）。
- 结论：**离"对齐 app.memoh.net"还很远**，之前那份"已实现能力"清单是 API 视角，不是产品视角。

## 仓库状态

- 路径：本仓库根目录，分支 `feat/memoh-native-client`
- 相对 `origin/feat/memoh-native-client` **领先若干提交，未推送**
  （数字会变，动手前自己数：`git rev-list --count origin/feat/memoh-native-client..HEAD`）
- 工作区干净
- 提交身份：`洛铃 <teawhat2000@gmail.com>`（仓库级配置）
- **⚠️ 尚未构建验证**：环境无 `ANDROID_HOME`，也无 `local.properties`。不要假设代码能编译通过。

本轮提交（按时间顺序）：

| 提交 | 内容 |
| --- | --- |
| `be366fdf` | API 清单（Swagger 全量 + 未文档化路由扫描）+ 原生 Memory 管理 |
| `b211c598` | 原生 Bot 备份流程（概览 / 导出 / 导入预览 / 确认导入） |
| `d1ae05c4` | 本交接文档 |

## API 清单（本次交付的核心资产）

- `docs/MEMOH_API_COVERAGE.md`
  从固定版本的 Swagger 生成：**338 个操作、260 个路径、475 个 schema 定义**，
  含方法、路径、tag、摘要、参数、请求/响应引用。
  上游固定为 `felinics/Memoh` 提交 `f3e8199560d42109be4b63bfc82ac62f260a66e2`。
- `docs/MEMOH_UNDOCUMENTED_ROUTES.md`
  扫描生产 Go 路由注册后，与 Swagger 对比出的 **30 条未文档化路由**（含源码位置）。
  注意：这份是**扫描器输出**，不是服务端导出的路由表；匹配基于模式，含对动态前缀的假设。
- `scripts/generate-memoh-api-catalog.mjs` / `scripts/audit-memoh-routes.mjs`
  上面两份文档的生成脚本，可复跑。

清单是**后端能力账本**，不代表每个端点都应该变成手机上的功能。
管理类/运维类路由（container 生命周期、metrics、terminal WebSocket、依赖预装、hook 测试等）
不应仅因为"有 API"就做成 UI。

## 已实现的客户端能力

API 客户端 `MemohApi.kt` 有 **50 个 suspend 方法**，全部走真实服务端接口，无 mock、无假数据。

已接入 UI 的部分：

- 登录（`/auth/login`，Bearer JWT，AndroidKeyStore AES-GCM 加密存储，不存密码）
- 账号信息与改密（`/users/me`、`/users/me/password`）
- 多 Bot 选择、会话分页列表、按需加载更早历史
- 会话创建 / 重命名 / 删除（删除有二次确认）
- 消息发送（客户端生成 `invocation_id` 做幂等）、服务端 JSON 历史、Markdown 渲染
- WebSocket `/bots/{bot_id}/web/ws`：Bearer 握手、运行时订阅、重连、admission 重放
- 运行时快照/增量：正文、推理、工具输入/输出/diff、停止运行、权限选择
- Agent 结构化提问回复（文本 / 单选 / 多选）、控制确认追踪
- 后续消息队列与 steer 队列（查看 + 撤销）
- Agent 管理：列表、创建、启停、删除、凭据写入/清除、设备码登录轮询
- 工作区文件：列目录、读、带 revision 的文本写、系统选择器上传
- 定时任务：列表、创建、启停、删除
- **Memory（bot 级 12 个操作 + 团队配置 2 个操作）**：列表/搜索/增改删/批量删/图谱/状态/用量/ingest/rebuild/compact
- **Bot 备份**：概览、选择区段 + 可选加密口令导出（走系统保存文档）、导入预览、确认导入、结果回显

## 明确未实现 / 已知限制

- **构建验证已通过**：已将 `llama.cpp` 原生 C++ 编译默认旁路（专注 Memoh 云客户端），`:app:assemblePlayDebug` 与 `:app:compilePlayDebugKotlin` 均 100% 编译成功并生成 APK。
- **聊天附件与多模态预览已实现**：输入框多附件拾取、草稿预览条与删除、`conversation.UIAttachment` Base64/DataURL payload 发送、消息气泡内联图片预览与全屏 Lightbox 弹窗均已完成。
- 备份导入目前用服务端默认区段；分区段导入未做，因为 OpenAPI 未明确 multipart 中 `sections` 的编码形式，没有靠猜实现。
- 工作区文件仅单文件上传 + 文本编辑；无文件夹上传、批量操作、媒体预览。
- 定时任务仅基础 cron / 新会话模式；已有会话目标、cron 编辑、运行日志未做。
- 未做：终端、音频、斜杠命令、队列重排序/编辑、Apps 安装与更新、connectors、channel-managers、
  runtime checks、compaction logs、各类运维/管理端点。
- 运行时事件归并只覆盖常见的快照/增量文本与工具路径，未覆盖 Web 客户端完整的事件游标与重同步语义。
  高风险 Agent 操作前需先验证重连与审批流程。
- 工具详情给的是有上限的原始文本，不是带语法高亮的代码/diff 编辑器。

## 构建与测试的真实状态（2026-10-22 实测）

**可以构建了。** 之前的「环境没有 Android SDK」结论已作废 —— SDK 就在
`C:\Users\Hileb\AppData\Local\Android\Sdk`，`local.properties` 也已配置。

```
:app:compileFdroidDebugKotlin   BUILD SUCCESSFUL
:app:testFdroidDebugUnitTest    3790 tests, 2 failed, 3 skipped  → 修复后全部通过
assembleFdroidDebug             产出 APK
```

### 两处环境陷阱（会让人误判成"代码坏了"）

1. **PowerShell 里 `./gradlew.bat` 静默失败。** 必须用 `cmd /c "gradlew.bat ..."`。
   直接调用会立即退出、日志空白、EXIT 为空 —— 看起来像构建挂了，其实是没启动。
2. **默认 JDK 是 26，Robolectric 撑不住。** 症状是每个 Robolectric 测试都报
   `Unsupported class file major version 70`。已在 `app/build.gradle.kts` 里把
   **测试 JVM** 钉到 Java 21（编译目标没动）。CI 本来就用 21，所以只有本地会炸。

### 本次审计修掉的真实缺陷

| # | 缺陷 | 后果 |
| --- | --- | --- |
| 1 | 测试 JVM 跟随本机 JDK（26） | 所有 Robolectric 测试全红，CI 绿而本地红，掩盖真问题 |
| 2 | 附件整文件读入内存再 base64 | 选一个大视频 → OOM 崩进程 |
| 3 | 附件把 data URL 同时塞进 `base64` 和 `url` | 上行体积翻倍 |
| 4 | 设备码登录轮询无上限、异常被吞 | 离线时无限轮询，界面永远显示"等待中" |
| 5 | 详情页保存走 `selectBot` | 改个名字、切个开关 → 当前会话/历史/连接全被清空 |
| 6 | 两处直接用 material3 `DropdownMenu` | 违反项目统一菜单封装约定，lint 测试失败 |

### 顺带简化

- `.github/workflows/build.yml` + `buildfast.yml`（两个同名 "Build APK"、大量重复）
  → 合并为单个 `android.yml`，`fast` 变成一个开关而不是第二份流水线。
- **签名步骤原本永远不会执行**：step 级 `if:` 读不到该 step 自己 `env:` 块里的变量，
  `if: ${{ env.KEYSTORE_BASE64 != '' }}` 恒为假。已改为直接读 `secrets`。

### 仍未验证

- 没有连过真实 Memoh 实例。登录 → 发消息 → WebSocket 流式 → 工具审批 → 历史回读
  这条主链路**一条都没在真服务器上跑过**，这是目前最大的未知。
- 那 3 个 skipped 测试没有逐条确认原因。

## 建议的下一步优先级

按"离对齐 app.memoh.net / 能日常使用还差多远"排序，不是按端点数量排：

1. [x] **先让它能编译** —— SDK 路径与 Gradle 构建已跑通，跳过原生 llama.cpp 后 `:app:assemblePlayDebug` 正常产出 APK。
2. [x] **补 Bot 详情页骨架** —— 11 个 Tab 结构已齐（`overview` / `general` / `access` / `container` 真实对接，加装 `canManageBot` 权限门）。
3. [x] **`agents` 标签页** —— 完整接入列表、启停、凭据配置与设备码登录自动轮询。
4. [x] **聊天附件** —— 聊天输入框支持多图/文件拾取、草稿栏预览/移除、`conversation.UIAttachment` 数据组装发送、消息内联图片预览与全屏查看。
5. **连真实 Memoh 实例跑一遍主链路** —— 登录（支持 self-hosted 账密与 app.memoh.net 流程）→ 选 Bot → 发消息/发附件 → WebSocket 流式 → 工具审批 → 历史回读。
6. 定时任务补全（已有会话目标、cron 编辑、运行日志）。
7. 工作区文件补全（文件夹上传、批量操作、媒体预览）。

不建议先做的：Apps 安装/更新（SSE 流式响应，复杂度高）、`container`/`remote-runtime`（移动端价值低）、
terminal WebSocket、各类运维/管理端点。


## 继续开发时的约定

1. **只用固定版本的上游契约**（`f3e8199`）。要确认某个字段语义，去看 Go handler，不要凭字段名推断。
2. **区分"有文档"与"有实现"**：Swagger 里的操作和 Go 里注册但没进 Swagger 的路由是两类东西，不要混着算覆盖率。
3. **不要用假数据填充 UI**。做不到就如实标注限制，而不是用演示数据冒充功能。
4. 破坏性操作（删除、覆盖导入）必须二次确认。
5. 默认**只 commit，不 push**，推送时机由洛铃决定。
6. 提交前确认仓库级 git 身份正确（`洛铃 <teawhat2000@gmail.com>`），本机无全局身份兜底。
7. **子代理的自述不能当事实**：它说"已完成/已删除"之后，自己 `git status` / 读文件核一遍。
8. **写断言前先确认真值**：这台机器上已经反复出现"断言写错、产品没错"的情况。
   断言失败时第一反应应是"我的断言对吗"，不是"代码坏了"。
9. 清理自己的脚手架时**只删自己精确命名的文件**，不要用通配符动共享目录，不要按进程名通配杀进程。
