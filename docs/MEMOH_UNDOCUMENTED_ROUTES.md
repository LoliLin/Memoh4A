# Registered Memoh routes missing from Swagger

- Compared production Echo route registrations under `internal/` and `cmd/` with the pinned Swagger spec.
- Undocumented registration count: 30.
- These entries have no OpenAPI operation schema; route-level authentication is determined by server middleware and handler checks.

| Method | Full path | Source |
| --- | --- | --- |
| GET | `/` | `internal/handlers/file_embed.go:43` |
| GET | `/*` | `internal/handlers/file_embed.go:44` |
| GET | `/api/docs` | `internal/handlers/swagger.go:37` |
| GET | `/api/docs/` | `internal/handlers/swagger.go:38` |
| GET | `/api/oauth/mcp/callback` | `internal/handlers/mcp_oauth.go:47` |
| GET | `/api/swagger.json` | `internal/handlers/swagger.go:36` |
| GET | `/assets/*` | `internal/handlers/file_embed.go:39` |
| GET | `/auth/callback` | `internal/handlers/provider_oauth.go:33` |
| POST | `/bots/:bot_id/agents/:id/codex/login/device/authorize` | `internal/handlers/external_agent_login.go:51` |
| POST | `/bots/:bot_id/agents/:id/codex/login/device/cancel` | `internal/handlers/external_agent_login.go:53` |
| POST | `/bots/:bot_id/agents/:id/codex/login/device/poll` | `internal/handlers/external_agent_login.go:52` |
| GET | `/bots/:bot_id/media/:content_hash` | `internal/handlers/message.go:137` |
| POST | `/bots/:bot_id/network/actions/:action_id` | `internal/handlers/network.go:41` |
| GET | `/bots/:bot_id/network/nodes` | `internal/handlers/network.go:40` |
| GET | `/bots/:bot_id/network/status` | `internal/handlers/network.go:39` |
| POST | `/bots/:id/channel/weixin/qr/poll` | `internal/channel/adapters/weixin/qr_handler.go:46` |
| POST | `/bots/:id/channel/weixin/qr/start` | `internal/channel/adapters/weixin/qr_handler.go:45` |
| GET | `/channels/:platform/public/media/:bot_id/:content_hash/original/:name` | `internal/handlers/public_media.go:67` |
| HEAD | `/channels/:platform/public/media/:bot_id/:content_hash/original/:name` | `internal/handlers/public_media.go:68` |
| GET | `/channels/:platform/public/media/:bot_id/:content_hash/preview.jpg` | `internal/handlers/public_media.go:65` |
| HEAD | `/channels/:platform/public/media/:bot_id/:content_hash/preview.jpg` | `internal/handlers/public_media.go:66` |
| GET | `/channels/:platform/webhook/:config_id` | `internal/channel/webhook_handler.go:52` |
| POST | `/channels/:platform/webhook/:config_id` | `internal/channel/webhook_handler.go:53` |
| GET | `/health` | `cmd/internal/channel/providers.go:380` |
| HEAD | `/health` | `internal/handlers/ping.go:39` |
| HEAD | `/health` | `cmd/channel/main.go:55` |
| GET | `/network/meta` | `internal/handlers/network.go:36` |
| GET | `/ready` | `cmd/channel/main.go:53` |
| HEAD | `/ready` | `cmd/channel/main.go:54` |
| GET | `/runtimes/connect` | `internal/handlers/runtime_connect.go:48` |
