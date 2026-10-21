# Memoh Android 客户端 — 交接说明

供接手继续开发的人/agent 阅读。最后更新：2026-10-21。

## 仓库状态

- 路径：本仓库根目录，分支 `feat/memoh-native-client`
- 相对 `origin/feat/memoh-native-client` **领先 11 个提交，未推送**
- 工作区干净
- 提交身份：`洛铃 <teawhat2000@gmail.com>`（仓库级配置）
- **⚠️ 尚未构建验证**：环境无 `ANDROID_HOME`，也无 `local.properties`。不要假设代码能编译通过。

本轮新增的两个提交：

| 提交 | 内容 |
| --- | --- |
| `be366fdf` | API 清单（Swagger 全量 + 未文档化路由扫描）+ 原生 Memory 管理 |
| `b211c598` | 原生 Bot 备份流程（概览 / 导出 / 导入预览 / 确认导入） |

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

- **尚未构建验证，也未连真实 Memoh 实例跑过。** 这是最重要的一条。
- 备份导入目前用服务端默认区段；分区段导入未做，因为 OpenAPI 未明确 multipart 中 `sections` 的编码形式，没有靠猜实现。
- 聊天输入框附件、内联图片预览未实现。工作区上传器不是聊天附件 API。
- 工作区文件仅单文件上传 + 文本编辑；无文件夹上传、批量操作、媒体预览。
- 定时任务仅基础 cron / 新会话模式；已有会话目标、cron 编辑、运行日志未做。
- 未做：终端、音频、斜杠命令、队列重排序/编辑、Apps 安装与更新、connectors、channel-managers、
  runtime checks、compaction logs、各类运维/管理端点。
- 运行时事件归并只覆盖常见的快照/增量文本与工具路径，未覆盖 Web 客户端完整的事件游标与重同步语义。
  高风险 Agent 操作前需先验证重连与审批流程。
- 工具详情给的是有上限的原始文本，不是带语法高亮的代码/diff 编辑器。

## 继续开发时的约定

1. **只用固定版本的上游契约**（`f3e8199`）。要确认某个字段语义，去看 Go handler，不要凭字段名推断。
2. **区分"有文档"与"有实现"**：Swagger 里的操作和 Go 里注册但没进 Swagger 的路由是两类东西，不要混着算覆盖率。
3. **不要用假数据填充 UI**。做不到就如实标注限制，而不是用演示数据冒充功能。
4. 破坏性操作（删除、覆盖导入）必须二次确认。
5. 默认**只 commit，不 push**，推送时机由洛铃决定。
6. 提交前确认仓库级 git 身份正确（`洛铃 <teawhat2000@gmail.com>`），本机无全局身份兜底。
