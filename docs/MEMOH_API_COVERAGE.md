# Memoh API catalog

- Source: `felinics/Memoh` Swagger 2.0 spec at commit `f3e8199560d42109be4b63bfc82ac62f260a66e2`
- API base path: `/`
- Paths: 260; operations: 338; definitions: 475.
- This is the backend contract inventory, not a claim that every administrative or internal endpoint belongs in the Android UI.
- Authentication is reported only as declared by the spec; runtime middleware may add constraints.
- Production routes absent from Swagger are listed in [the route registration appendix](MEMOH_UNDOCUMENTED_ROUTES.md).

| Method | Path | Tag | Operation | Auth | Parameters | Request | Responses |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/acp/profiles` | acp | List ACP profiles<br><code>—</code> | none declared | — | — | 200 OK → `acpprofile.ProfilesResponse` |
| POST | `/agent-authorizations` | agent-authorizations | Authorize an Agent account independently of a Bot<br><code>—</code> | none declared | payload (body, required) → `agentcredential.AuthorizationRequest` |  → `agentcredential.AuthorizationRequest` | 201 Created → `agentcredential.Authorization`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>429 Too Many Requests → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| DELETE | `/agent-authorizations/{id}` | agent-authorizations | Discard a temporary authorization without disconnecting an already-created Agent<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/agent-authorizations/{id}` | agent-authorizations | Read an authorization session owned by the current user<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `agentcredential.Authorization`<br>403 Forbidden → `server.Problem`<br>410 Gone → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/agent-authorizations/{id}/exchange` | agent-authorizations | Complete Claude browser authorization with the returned authorization code<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body, required) → `handlers.AgentAuthorizationExchangeRequest` |  → `handlers.AgentAuthorizationExchangeRequest` | 200 OK → `agentcredential.Authorization`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>410 Gone → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/agent-authorizations/{id}/poll` | agent-authorizations | Poll a pending Agent device authorization<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `agentcredential.Authorization`<br>403 Forbidden → `server.Problem`<br>410 Gone → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/auth/login` | auth | Login<br><code>—</code> | none declared | payload (body, required) → `handlers.LoginRequest` |  → `handlers.LoginRequest` | 200 OK → `handlers.LoginResponse`<br>400 Bad Request → `server.Problem`<br>401 Unauthorized → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/auth/refresh` | auth | Refresh Token<br><code>—</code> | BearerAuth | — | — | 200 OK → `handlers.RefreshResponse`<br>401 Unauthorized → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots` | bots | List bots<br><code>—</code> | none declared | owner_id (query) → `string` | — | 200 OK → `bots.ListBotsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots` | bots | Create bot user<br><code>—</code> | none declared | Idempotency-Key (header) → `string`<br>payload (body, required) → `bots.CreateBotRequest` |  → `bots.CreateBotRequest` | 201 Created → `bots.Bot`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/acl/channel-identities` | bots | Search ACL channel identity candidates<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>q (query) → `string`<br>limit (query) → `integer` | — | 200 OK → `acl.ChannelIdentityCandidateListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/acl/channel-identities/{channel_identity_id}/conversations` | bots | List observed conversations for a channel identity<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>channel_identity_id (path, required) → `string` | — | 200 OK → `acl.ObservedConversationCandidateListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/acl/channel-types/{channel_type}/conversations` | bots | List observed conversations for a platform type<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>channel_type (path, required) → `string` | — | 200 OK → `acl.ObservedConversationCandidateListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/acl/default-effect` | bots | Get bot ACL default effect<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `acl.DefaultEffectResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/acl/default-effect` | bots | Set bot ACL default effect<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `acl.DefaultEffectResponse` |  → `acl.DefaultEffectResponse` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/acl/rules` | bots | List bot ACL rules<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `acl.ListRulesResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/acl/rules` | bots | Create ACL rule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `acl.CreateRuleRequest` |  → `acl.CreateRuleRequest` | 201 Created → `acl.Rule`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/acl/rules/{rule_id}` | bots | Delete ACL rule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>rule_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/acl/rules/{rule_id}` | bots | Update ACL rule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>rule_id (path, required) → `string`<br>payload (body, required) → `acl.UpdateRuleRequest` |  → `acl.UpdateRuleRequest` | 200 OK → `acl.Rule`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/acp-runtimes` | acp | Create an unbound ACP runtime (pre-session model picker)<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeCreateRequest` |  → `handlers.acpRuntimeCreateRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>429 Too Many Requests → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/acp-runtimes/{runtime_id}` | acp | Close an ACP runtime<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>runtime_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/acp-runtimes/{runtime_id}` | acp | Get ACP runtime state by runtime ID<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>runtime_id (path, required) → `string` | — | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PATCH | `/bots/{bot_id}/acp-runtimes/{runtime_id}/mode` | acp | Set an unbound ACP runtime's mode<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>runtime_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeModeRequest` |  → `handlers.acpRuntimeModeRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| PATCH | `/bots/{bot_id}/acp-runtimes/{runtime_id}/model` | acp | Set (or reset) an ACP runtime's model<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>runtime_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeModelRequest` |  → `handlers.acpRuntimeModelRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| PATCH | `/bots/{bot_id}/acp-runtimes/{runtime_id}/reasoning` | acp | Set an ACP runtime's reasoning effort<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>runtime_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeReasoningRequest` |  → `handlers.acpRuntimeReasoningRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/bots/{bot_id}/agents` | bot-agents | List a bot's Agents<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `botagents.ListResponse`<br>403 Forbidden → `server.Problem` |
| POST | `/bots/{bot_id}/agents` | bot-agents | Add an Agent to a bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `botagents.CreateRequest` |  → `botagents.CreateRequest` | 201 Created → `botagents.BotAgent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| DELETE | `/bots/{bot_id}/agents/{id}` | bot-agents | Delete a bot Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 204 No Content<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem` |
| GET | `/bots/{bot_id}/agents/{id}` | bot-agents | Get a bot Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `botagents.BotAgent`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| PATCH | `/bots/{bot_id}/agents/{id}` | bot-agents | Update a bot Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>payload (body, required) → `botagents.UpdateRequest` |  → `botagents.UpdateRequest` | 200 OK → `botagents.BotAgent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem` |
| GET | `/bots/{bot_id}/agents/{id}/codex/usage` | external-agents | Get the Codex usage limits of the Agent's ChatGPT account<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `externalagent.CodexUsageResponse`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| DELETE | `/bots/{bot_id}/agents/{id}/credential` | agent-credentials | Disconnect a Bot Agent's credential<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 204 No Content<br>404 Not Found → `server.Problem` |
| GET | `/bots/{bot_id}/agents/{id}/credential` | agent-credentials | Get the credential attached to a Bot Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `agentcredential.PublicCredential`<br>404 Not Found → `server.Problem` |
| PUT | `/bots/{bot_id}/agents/{id}/credential` | agent-credentials | Attach a credential to a Bot Agent, replacing any previous one<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>payload (body, required) → `handlers.agentCredentialPutRequest` |  → `handlers.agentCredentialPutRequest` | 200 OK → `agentcredential.PublicCredential`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/agents/{id}/credential/claim` | agent-authorizations | Bind a completed temporary authorization to a Bot Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>payload (body, required) → `handlers.AgentAuthorizationClaimRequest` |  → `handlers.AgentAuthorizationClaimRequest` | 200 OK → `agentcredential.PublicCredential`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>410 Gone → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/agents/{id}/login/device/authorize` | external-agents | Start a device-code account sign-in on a direct runtime Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `externalagent.DeviceLoginAuthorizeResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/agents/{id}/login/device/cancel` | external-agents | Cancel a pending device-code sign-in<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>body (body, required) → `externalagent.DeviceLoginPollRequest` |  → `externalagent.DeviceLoginPollRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/agents/{id}/login/device/poll` | external-agents | Poll a pending device-code sign-in<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>body (body, required) → `externalagent.DeviceLoginPollRequest` |  → `externalagent.DeviceLoginPollRequest` | 200 OK → `externalagent.DeviceLoginPollResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| GET | `/bots/{bot_id}/agents/{id}/models` | bot-agents | List models available to a bot Agent<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>model_id (query) → `string`<br>project_path (query) → `string` | — | 200 OK → `external.ModelCatalog`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/agents/{id}/runtime-controls` | bot-agents | Get bot Agent default runtime controls<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `external.Controls`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/apps` | apps | List the Apps installed for a bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>refresh (query) → `boolean` | — | 200 OK → `handlers.AppListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/apps` | apps | Install an App release into a bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.AppInstallRequest` |  → `handlers.AppInstallRequest` | 200 SSE stream of operation events → `handlers.AppStreamEvent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| DELETE | `/bots/{bot_id}/apps/{installation_id}` | apps | Remove an App from a bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>installation_id (path, required) → `string`<br>remove_unreferenced_required (query) → `boolean` | — | 200 SSE stream of operation events → `handlers.AppStreamEvent`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| GET | `/bots/{bot_id}/apps/{installation_id}` | apps | Get one installed App<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>installation_id (path, required) → `string` | — | 200 OK → `handlers.AppItem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/apps/{installation_id}/connectors/{connector_type}/api-key` | apps | Connect an API-key connector an App references<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>installation_id (path, required) → `string`<br>connector_type (path, required) → `string`<br>payload (body, required) → `handlers.AppConnectorCredentialRequest` |  → `handlers.AppConnectorCredentialRequest` | 201 Created → `connectors.Connector`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/apps/{installation_id}/connectors/{connector_type}/oauth` | apps | Authorize a connector an App references<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>installation_id (path, required) → `string`<br>connector_type (path, required) → `string`<br>payload (body, required) → `handlers.AppConnectorOAuthRequest` |  → `handlers.AppConnectorOAuthRequest` | 201 Created → `connectit.OAuthAuthorization`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/apps/{installation_id}/removal-preview` | apps | Preview what removing an App would do<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>installation_id (path, required) → `string` | — | 200 OK → `handlers.AppRemovalPreviewResponse`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/apps/{installation_id}/resume` | apps | Continue a partial App installation<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>installation_id (path, required) → `string` | — | 200 SSE stream of operation events → `handlers.AppStreamEvent`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/apps/check-updates` | apps | Check installed Apps for newer releases<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.AppListResponse`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| POST | `/bots/{bot_id}/apps/update` | apps | Update parts of an App on a bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.AppUpdateRequest` |  → `handlers.AppUpdateRequest` | 200 SSE stream of operation events → `handlers.AppStreamEvent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| POST | `/bots/{bot_id}/backup/export` | bots | Export a full bot backup<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `botbackup.ExportRequest` |  → `botbackup.ExportRequest` | 200 OK → `file`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/backup/summary` | bots | Summarize what a bot would export<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `botbackup.SummaryResult`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/channel-managers` | bots | List channel managers<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `channelaccess.ListManagersResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/channel-managers` | bots | Set a channel manage override<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `channelaccess.SetManagerRequest` |  → `channelaccess.SetManagerRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/channel-managers/{channel_identity_id}` | bots | Clear a channel manage override<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>channel_identity_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/compaction/logs` | compaction | Delete compaction logs<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/compaction/logs` | compaction | List compaction logs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>limit (query) → `integer`<br>offset (query) → `integer` | — | 200 OK → `compaction.ListLogsResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/connectors` | connectors | List bot connectors<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `connectors.ListResponse`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| DELETE | `/bots/{bot_id}/connectors/{connection_id}` | connectors | Disconnect a connector<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>connection_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/connectors/{connection_id}` | connectors | Get a bot connector<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>connection_id (path, required) → `string` | — | 200 OK → `connectors.Connector`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| PATCH | `/bots/{bot_id}/connectors/{connection_id}` | connectors | Enable or disable a connector<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>connection_id (path, required) → `string`<br>payload (body, required) → `handlers.ConnectorEnabledRequest` |  → `handlers.ConnectorEnabledRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/connectors/{connection_id}/reauth` | connectors | Reauthorize a connector<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>connection_id (path, required) → `string` | — | 200 OK → `connectit.OAuthAuthorization`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| DELETE | `/bots/{bot_id}/container` | containerd | Delete workspace for bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>preserve_data (query) → `boolean` | — | 202 Accepted<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/container` | containerd | Get workspace info for bot<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.GetContainerResponse`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/container` | containerd | Create workspace for bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.CreateContainerRequest` |  → `handlers.CreateContainerRequest` | 200 SSE stream of workspace creation events → `handlers.CreateContainerResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/container/browser/sessions` | containerd | Create browser proxy session for bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.browserSessionCreateRequest` |  → `handlers.browserSessionCreateRequest` | 200 OK → `handlers.browserSessionCreateResponse`<br>400 Bad Request → `server.Problem`<br>401 Unauthorized → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/container/browser/sessions/{session_id}` | containerd | Delete browser proxy session<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 204 No Content<br>401 Unauthorized → `server.Problem`<br>403 Forbidden → `server.Problem` |
| POST | `/bots/{bot_id}/container/browser/sessions/{session_id}/keepalive` | containerd | Keep browser proxy session alive<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `handlers.browserSessionKeepAliveResponse`<br>401 Unauthorized → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/container/data/restore` | containerd | Restore previously preserved data into workspace<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `object`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/container/display` | containerd | Check workspace display availability for bot<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.displayInfoResponse`<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/container/display/prepare` | containerd | Prepare workspace display dependencies<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 SSE stream of display preparation events → `string`<br>404 Not Found → `server.Problem` |
| GET | `/bots/{bot_id}/container/display/sessions` | containerd | List active workspace display WebRTC sessions<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.displaySessionListResponse`<br>404 Not Found → `server.Problem` |
| DELETE | `/bots/{bot_id}/container/display/sessions/{session_id}` | containerd | Close a workspace display WebRTC session<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 204 No Content<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/container/display/webrtc/offer` | containerd | Create a WebRTC answer for bot workspace display<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.displayWebRTCOfferRequest` |  → `handlers.displayWebRTCOfferRequest` | 200 OK → `handlers.displayWebRTCOfferResponse`<br>400 Bad Request → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/fs` | containerd | Get file or directory info<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>path (query, required) → `string` | — | 200 OK → `handlers.FSFileInfo`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/archive` | containerd | Download files and directories as tar.gz<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.FSArchiveRequest` |  → `handlers.FSArchiveRequest` | 200 OK → `file`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/delete` | containerd | Delete a file or directory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.FSDeleteRequest` |  → `handlers.FSDeleteRequest` | 200 OK → `handlers.fsOpResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/fs/download` | containerd | Download a file as binary stream<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>path (query, required) → `string` | — | 200 OK → `file`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/extract` | containerd | Extract an archive file<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.FSExtractRequest` |  → `handlers.FSExtractRequest` | 200 OK → `handlers.FSExtractResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/fs/list` | containerd | List directory contents<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>path (query, required) → `string` | — | 200 OK → `handlers.FSListResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/mkdir` | containerd | Create a directory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.FSMkdirRequest` |  → `handlers.FSMkdirRequest` | 200 OK → `handlers.fsOpResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/fs/read` | containerd | Read file content as text<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>path (query, required) → `string` | — | 200 OK → `handlers.FSReadResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/rename` | containerd | Rename or move a file/directory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.FSRenameRequest` |  → `handlers.FSRenameRequest` | 200 OK → `handlers.fsOpResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/upload` | containerd | Upload a file via multipart form<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>path (formData, required) → `string`<br>file (formData, required) → `file` | multipart/form-data | 200 OK → `handlers.FSUploadResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/fs/write` | containerd | Write text content to a file<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.FSWriteRequest` |  → `handlers.FSWriteRequest` | 200 OK → `handlers.fsOpResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/metrics` | containerd | Get current workspace metrics for bot<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.GetContainerMetricsResponse`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/container/metrics` | containerd | Update workspace metrics settings for bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.UpdateContainerMetricsRequest` |  → `handlers.UpdateContainerMetricsRequest` | 200 OK → `handlers.GetContainerMetricsResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/container/removal/cancel` | containerd | Withdraw a failed workspace removal<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 204 No Content<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/container/skills` | containerd | Delete Memoh-managed skills<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.SkillsDeleteRequest` |  → `handlers.SkillsDeleteRequest` | 200 OK → `handlers.skillsOpResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/skills` | containerd | List skills from the bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>workspace_target_id (query) → `string` | — | 200 OK → `handlers.SkillsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/container/skills` | containerd | Upload skills into Memoh-managed directory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.SkillsUpsertRequest` |  → `handlers.SkillsUpsertRequest` | 200 OK → `handlers.skillsOpResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/container/skills/actions` | containerd | Apply an action to a discovered or managed skill source<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.SkillsActionRequest` |  → `handlers.SkillsActionRequest` | 200 OK → `handlers.skillsOpResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/container/snapshots` | containerd | List snapshots<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>snapshotter (query) → `string` | — | 200 OK → `handlers.ListSnapshotsResponse`<br>501 Snapshots currently not supported on this backend → `server.Problem` |
| POST | `/bots/{bot_id}/container/snapshots` | containerd | Create workspace snapshot for bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.CreateSnapshotRequest` |  → `handlers.CreateSnapshotRequest` | 200 OK → `handlers.CreateSnapshotResponse`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>501 Snapshots currently not supported on this backend → `server.Problem` |
| POST | `/bots/{bot_id}/container/snapshots/rollback` | containerd | Roll back workspace to a previous snapshot version<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.RollbackRequest` |  → `handlers.RollbackRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/container/start` | containerd | Start workspace for bot<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `object`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/container/stop` | containerd | Stop workspace for bot<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `object`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/container/terminal` | containerd | Check terminal availability for bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.terminalInfoResponse`<br>404 Not Found → `server.Problem` |
| GET | `/bots/{bot_id}/container/terminal/ws` | containerd | Interactive WebSocket terminal for bot workspace<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>cols (query) → `integer`<br>rows (query) → `integer`<br>token (query) → `string` | — | 101 WebSocket upgrade<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/dependencies` | containerd | List workspace dependencies<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>refresh (query) → `boolean` | — | 200 OK → `handlers.WorkspaceDependencyListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/dependencies/{dep_id}/install` | containerd | Install or reinstall a referenced workspace dependency<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>dep_id (path, required) → `string`<br>payload (body) → `handlers.WorkspaceDependencyInstallRequest` |  → `handlers.WorkspaceDependencyInstallRequest` | 200 SSE stream of operation events → `handlers.WorkspaceDependencyStreamEvent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/dependencies/{dep_id}/reinstall` | containerd | Reinstall a workspace dependency<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>dep_id (path, required) → `string`<br>payload (body) → `handlers.WorkspaceDependencyInstallRequest` |  → `handlers.WorkspaceDependencyInstallRequest` | 200 SSE stream of operation events → `handlers.WorkspaceDependencyStreamEvent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/dependencies/{dep_id}/rollback` | containerd | Roll a workspace dependency back to its previous version<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>dep_id (path, required) → `string` | — | 200 OK → `handlers.WorkspaceDependencyOperationResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/dependencies/{dep_id}/script` | containerd | Show the script a dependency action would run<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>dep_id (path, required) → `string`<br>action (query) → `string`<br>definition_revision (query) → `string` | — | 200 OK → `handlers.WorkspaceDependencyScriptResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/dependencies/{dep_id}/update` | containerd | Update a workspace dependency<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>dep_id (path, required) → `string`<br>payload (body) → `handlers.WorkspaceDependencyInstallRequest` |  → `handlers.WorkspaceDependencyInstallRequest` | 200 SSE stream of operation events → `handlers.WorkspaceDependencyStreamEvent`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>422 Unprocessable Entity → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/dependencies/check-updates` | containerd | Check workspace dependencies for updates<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.WorkspaceDependencyListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/dependencies/preflight` | containerd | Check whether dependencies are ready<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.WorkspaceDependencyPreflightRequest` |  → `handlers.WorkspaceDependencyPreflightRequest` | 200 OK → `handlers.WorkspaceDependencyPreflightResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/hooks/events` | hooks | List supported bot hook events<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.HooksEventsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/hooks/test` | hooks | Run bot hooks for a synthetic event<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.HookTestRequest` |  → `handlers.HookTestRequest` | 200 OK → `handlers.HookTestResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/mcp` | mcp | List MCP connections<br><code>—</code> | none declared | — | — | 200 OK → `mcp.ListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/mcp` | mcp | Create MCP connection<br><code>—</code> | none declared | payload (body, required) → `mcp.UpsertRequest` |  → `mcp.UpsertRequest` | 201 Created → `github_com_felinics_memoh_internal_mcp.Connection`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/mcp-ops/batch-delete` | mcp | Batch delete MCP connections<br><code>—</code> | none declared | payload (body, required) → `handlers.BatchDeleteRequest` |  → `handlers.BatchDeleteRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/mcp-ops/export` | mcp | Export MCP connections<br><code>—</code> | none declared | — | — | 200 OK → `mcp.ExportResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/mcp-ops/import` | mcp | Import MCP connections<br><code>—</code> | none declared | payload (body, required) → `mcp.ImportRequest` |  → `mcp.ImportRequest` | 200 OK → `mcp.ListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/mcp-stdio` | containerd | Create MCP stdio proxy<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.MCPStdioRequest` |  → `handlers.MCPStdioRequest` | 200 OK → `handlers.MCPStdioResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/mcp-stdio/{connection_id}` | containerd | MCP stdio proxy (JSON-RPC)<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>connection_id (path, required) → `string`<br>payload (body, required) → `object` |  → `object` | 200 JSON-RPC response: {jsonrpc,id,result\|error} → `object`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/mcp/{id}` | mcp | Delete MCP connection<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/mcp/{id}` | mcp | Get MCP connection<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `github_com_felinics_memoh_internal_mcp.Connection`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/mcp/{id}` | mcp | Update MCP connection<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body, required) → `mcp.UpsertRequest` |  → `mcp.UpsertRequest` | 200 OK → `github_com_felinics_memoh_internal_mcp.Connection`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/mcp/{id}/oauth/authorize` | mcp | Start OAuth authorization flow<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body) → `handlers.oauthAuthorizeRequest` |  → `handlers.oauthAuthorizeRequest` | 200 OK → `mcp.AuthorizeResult`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/mcp/{id}/oauth/discover` | mcp | Discover OAuth configuration for MCP server<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body) → `handlers.oauthDiscoverRequest` |  → `handlers.oauthDiscoverRequest` | 200 OK → `mcp.DiscoveryResult`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/bots/{bot_id}/mcp/{id}/oauth/exchange` | mcp | Exchange OAuth authorization code for tokens<br><code>—</code> | none declared | payload (body, required) → `handlers.oauthExchangeRequest` |  → `handlers.oauthExchangeRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem` |
| GET | `/bots/{bot_id}/mcp/{id}/oauth/status` | mcp | Get OAuth status for MCP connection<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `mcp.OAuthStatus`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| DELETE | `/bots/{bot_id}/mcp/{id}/oauth/token` | mcp | Revoke OAuth tokens for MCP connection<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem` |
| POST | `/bots/{bot_id}/mcp/{id}/probe` | mcp | Probe MCP connection<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `handlers.ProbeResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/memory` | memory | Delete memories<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body) → `handlers.memoryDeletePayload` |  → `handlers.memoryDeletePayload` | 200 OK → `adapters.DeleteResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/memory` | memory | Get all memories<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>no_stats (query) → `boolean` | — | 200 OK → `adapters.SearchResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/memory` | memory | Add memory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.memoryAddPayload` |  → `handlers.memoryAddPayload` | 200 OK → `adapters.SearchResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| DELETE | `/bots/{bot_id}/memory/{id}` | memory | Delete a single memory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `adapters.DeleteResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| PUT | `/bots/{bot_id}/memory/{memory_id}` | memory | Update a single memory by id<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>memory_id (path, required) → `string`<br>payload (body, required) → `handlers.memoryUpdatePayload` |  → `handlers.memoryUpdatePayload` | 200 OK → `adapters.MemoryItem`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/memory/compact` | memory | Compact memories<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.memoryCompactPayload` |  → `handlers.memoryCompactPayload` | 200 OK → `adapters.CompactResult`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>501 Not Implemented → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/memory/graph` | memory | Get memory graph<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.graphResponse`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/memory/ingest` | memory | Ingest agent-authored memory markdown into the wiki store<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `adapters.IngestResult`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/memory/rebuild` | memory | Rebuild memories from filesystem<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `adapters.RebuildResult`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{bot_id}/memory/search` | memory | Search memory<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.memorySearchPayload` |  → `handlers.memorySearchPayload` | 200 OK → `adapters.SearchResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/memory/status` | memory | Get memory runtime status<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `adapters.MemoryStatusResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/memory/usage` | memory | Get memory usage<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `adapters.UsageResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| DELETE | `/bots/{bot_id}/messages` | messages | Delete all bot history messages<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/messages` | messages | List session history messages<br><code>—</code> | none declared | bot_id (path, required) → `string/uuid`<br>session_id (query, required) → `string/uuid`<br>limit (query) → `integer`<br>before (query) → `string`<br>before_message_id (query) → `string/uuid` | — | 200 OK → `handlers.UIMessageListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/messages/locate` | messages | Locate a bot history message<br><code>—</code> | none declared | bot_id (path, required) → `string/uuid`<br>session_id (query, required) → `string/uuid`<br>external_message_id (query, required) → `string`<br>before (query) → `integer`<br>after (query) → `integer` | — | 200 OK → `handlers.UILocateMessageResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/quick-actions/execute` | quick-actions | Execute a Web quick action<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.QuickActionExecuteRequest` |  → `handlers.QuickActionExecuteRequest` | 200 OK → `handlers.CommandEventResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/schedule` | schedule | List schedules<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `schedule.ListResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/schedule` | schedule | Create schedule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `schedule.CreateRequest` |  → `schedule.CreateRequest` | 201 Created → `schedule.Schedule`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/schedule/{id}` | schedule | Delete schedule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/schedule/{id}` | schedule | Get schedule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string` | — | 200 OK → `schedule.Schedule`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/schedule/{id}` | schedule | Update schedule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>payload (body, required) → `schedule.UpdateRequest` |  → `schedule.UpdateRequest` | 200 OK → `schedule.Schedule`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/schedule/{id}/logs` | schedule | List schedule logs by schedule<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>limit (query) → `integer`<br>offset (query) → `integer` | — | 200 OK → `schedule.ListLogsResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/schedule/{id}/sessions` | schedule | List the sessions a schedule's runs created<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>id (path, required) → `string`<br>limit (query) → `integer`<br>cursor (query) → `string` | — | 200 OK → `schedule.ListRunSessionsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/schedule/logs` | schedule | Delete schedule logs<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/schedule/logs` | schedule | List schedule logs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>limit (query) → `integer`<br>offset (query) → `integer` | — | 200 OK → `schedule.ListLogsResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions` | sessions | List bot sessions<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>types (query) → `string`<br>parent_session_id (query) → `string`<br>workdir_id (query) → `string`<br>hide_schedule_runs (query) → `boolean`<br>limit (query) → `integer`<br>cursor (query) → `string` | — | 200 OK → `handlers.listSessionsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem` |
| POST | `/bots/{bot_id}/sessions` | sessions | Create a new chat session<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>body (body, required) → `handlers.createSessionRequest` |  → `handlers.createSessionRequest` | 201 Created → `session.Session`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem` |
| DELETE | `/bots/{bot_id}/sessions/{session_id}` | sessions | Soft-delete a session<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}` | sessions | Get a session by ID<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `session.Session`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}` | sessions | Update a session<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.updateSessionRequest` |  → `handlers.updateSessionRequest` | 200 OK → `session.Session`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/acp-runtime` | acp | Get ACP session runtime state<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/acp-runtime` | acp | Ensure ACP session runtime is started<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}/acp-runtime/mode` | acp | Set ACP session runtime mode<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeModeRequest` |  → `handlers.acpRuntimeModeRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}/acp-runtime/model` | acp | Set ACP session runtime model<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeModelRequest` |  → `handlers.acpRuntimeModelRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}/acp-runtime/reasoning` | acp | Set ACP session runtime reasoning effort<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.acpRuntimeReasoningRequest` |  → `handlers.acpRuntimeReasoningRequest` | 200 OK → `acpagent.RuntimeStatus`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/compact` | compaction | Trigger immediate context compaction<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `handlers.TriggerCompactResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/context-lifecycle` | sessions | Get session context lifecycle<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>limit (query) → `integer` | — | 200 OK → `handlers.ContextLifecycleResponse`<br>400 Bad Request → `server.Problem`<br>401 Unauthorized → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/follow-up-queue` | sessions | List pending follow-up inputs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `handlers.followUpQueueResponse`<br>403 Forbidden → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/follow-up-queue` | sessions | Enqueue follow-up input for the active session run<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.enqueueQueueRequest` |  → `handlers.enqueueQueueRequest` | 202 Accepted → `handlers.followUpQueueItemResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| DELETE | `/bots/{bot_id}/sessions/{session_id}/follow-up-queue/{item_id}` | sessions | Cancel an accepted follow-up input<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>item_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}/follow-up-queue/{item_id}` | sessions | Edit an accepted follow-up input<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>item_id (path, required) → `string`<br>body (body, required) → `handlers.updateQueueRequest` |  → `handlers.updateQueueRequest` | 200 OK → `handlers.followUpQueueItemResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/follow-up-queue/{item_id}/steer` | sessions | Promote an accepted follow-up input to steer the active run<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>item_id (path, required) → `string` | — | 202 Accepted → `handlers.steerQueueItemResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| PUT | `/bots/{bot_id}/sessions/{session_id}/follow-up-queue/reorder` | sessions | Reorder accepted follow-up inputs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.followUpQueueReorderRequest` |  → `handlers.followUpQueueReorderRequest` | 200 OK → `handlers.followUpQueueResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/fork` | sessions | Fork a chat session from an assistant reply<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.forkSessionRequest` |  → `handlers.forkSessionRequest` | 201 Created → `session.Session`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/invocations/{invocation_id}` | sessions | Look up the run admitted for a client invocation<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>invocation_id (path, required) → `string` | — | 200 OK → `handlers.sessionInvocationResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/queue` | sessions | List pending steer and follow-up inputs in one response<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `handlers.sessionQueueResponse`<br>403 Forbidden → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/runtime-controls` | sessions | Get session runtime controls<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `external.Controls`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/runtime-controls/commands` | sessions | Execute a read or operation runtime command<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>payload (body, required) → `handlers.RuntimeCommandRequest` |  → `handlers.RuntimeCommandRequest` | 200 OK → `handlers.RuntimeCommandResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/runtime-controls/goal` | sessions | Get the runtime-owned goal<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `handlers.RuntimeGoalResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/runtime-controls/goal` | sessions | Pause or clear a runtime-owned goal<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>payload (body, required) → `handlers.RuntimeGoalRequest` |  → `handlers.RuntimeGoalRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}/runtime-controls/mode` | sessions | Set session runtime permission or planning mode<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>payload (body, required) → `handlers.RuntimeModeRequest` |  → `handlers.RuntimeModeRequest` | 200 OK → `external.ModeState`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/status` | sessions | Get session info<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>model_id (query) → `string` | — | 200 OK → `handlers.SessionInfoResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/{session_id}/steer-queue` | sessions | List pending steer inputs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string` | — | 200 OK → `handlers.steerQueueResponse`<br>403 Forbidden → `server.Problem` |
| POST | `/bots/{bot_id}/sessions/{session_id}/steer-queue` | sessions | Enqueue steer input for the active session run<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.enqueueQueueRequest` |  → `handlers.enqueueQueueRequest` | 202 Accepted → `handlers.steerQueueItemResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| DELETE | `/bots/{bot_id}/sessions/{session_id}/steer-queue/{item_id}` | sessions | Cancel an accepted steer input<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>item_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| PATCH | `/bots/{bot_id}/sessions/{session_id}/steer-queue/{item_id}` | sessions | Edit an accepted steer input<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>item_id (path, required) → `string`<br>body (body, required) → `handlers.updateQueueRequest` |  → `handlers.updateQueueRequest` | 200 OK → `handlers.steerQueueItemResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| PUT | `/bots/{bot_id}/sessions/{session_id}/steer-queue/reorder` | sessions | Reorder accepted steer inputs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>session_id (path, required) → `string`<br>body (body, required) → `handlers.steerQueueReorderRequest` |  → `handlers.steerQueueReorderRequest` | 200 OK → `handlers.steerQueueResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/events` | messages | Stream bot-wide sessions activity<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 SSE stream → `string`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/sessions/model-preference-seed` | sessions | Welcome composer model seed<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.modelPreferenceSeedResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem` |
| DELETE | `/bots/{bot_id}/settings` | settings | Delete user settings<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/settings` | settings | Get user settings<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `settings.Settings`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/settings` | settings | Update user settings<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `settings.UpsertRequest` |  → `settings.UpsertRequest` | 200 OK → `settings.Settings`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| PUT | `/bots/{bot_id}/settings` | settings | Update user settings<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `settings.UpsertRequest` |  → `settings.UpsertRequest` | 200 OK → `settings.Settings`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/skills/catalog` | skills | List runtime-safe skills for chat-time skill selection<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.SafeSkillsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/starter-prompts` | bots | Get new-chat starter prompts<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>locale (query) → `string`<br>known_hash (query) → `string` | — | 200 OK → `handlers.StarterPromptsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/starter-prompts/refresh` | bots | Generate a different set of new-chat starter prompts<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.StarterPromptsRefreshRequest` |  → `handlers.StarterPromptsRefreshRequest` | 200 OK → `handlers.StarterPromptsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/token-usage` | token-usage | Get token usage statistics<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>from (query, required) → `string`<br>to (query, required) → `string`<br>model_id (query) → `string`<br>session_type (query) → `string` | — | 200 OK → `handlers.TokenUsageResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/token-usage/records` | token-usage | List per-call token usage records<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>from (query, required) → `string`<br>to (query, required) → `string`<br>model_id (query) → `string`<br>session_type (query) → `string`<br>limit (query) → `integer`<br>offset (query) → `integer` | — | 200 OK → `handlers.TokenUsageRecordsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/tool-approvals/{approval_id}/approve` | tool-approvals | Approve a pending tool call<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>approval_id (path, required) → `string`<br>payload (body) → `handlers.ToolApprovalDecisionRequest` |  → `handlers.ToolApprovalDecisionRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/tool-approvals/{approval_id}/reject` | tool-approvals | Reject a pending tool call<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>approval_id (path, required) → `string`<br>payload (body) → `handlers.ToolApprovalDecisionRequest` |  → `handlers.ToolApprovalDecisionRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/tools` | containerd | Unified MCP tools gateway<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `object` |  → `object` | 200 JSON-RPC response: {jsonrpc,id,result\|error} → `object`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/tts/synthesize` | bots | Synthesize speech for a bot<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>request (body, required) → `handlers.synthesizeRequest` |  → `handlers.synthesizeRequest` | 200 OK → `handlers.synthesizeResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/user-access` | bots | List bot user access grants<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `handlers.BotUserGrantListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/user-access` | bots | Create bot user access grant<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `bots.CreateUserGrantRequest` |  → `bots.CreateUserGrantRequest` | 201 Created → `bots.UserGrant`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/user-access/{grant_id}` | bots | Delete bot user access grant<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>grant_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{bot_id}/user-access/{grant_id}` | bots | Update bot user access grant<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>grant_id (path, required) → `string`<br>payload (body, required) → `bots.UpdateUserGrantRequest` |  → `bots.UpdateUserGrantRequest` | 200 OK → `bots.UserGrant`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/user-access/candidates` | bots | List grantable workspace members<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>q (query) → `string`<br>limit (query) → `integer` | — | 200 OK → `handlers.BotUserCandidateListResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/web/messages` | local-channel | Send a message to a local channel<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>payload (body, required) → `handlers.LocalChannelMessageRequest` |  → `handlers.LocalChannelMessageRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/web/stream` | local-channel | Subscribe to local channel events via SSE<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 SSE stream → `string`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/web/ws` | local-channel | WebSocket chat endpoint<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 101 Switching Protocols → `string`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/workdirs` | workdirs | List a Bot's workdirs<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>include_archived (query) → `boolean` | — | 200 OK → `workdir.WorkdirsResponse`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/workdirs` | workdirs | Create a Bot workdir<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>request (body, required) → `workdir.CreateRequest` |  → `workdir.CreateRequest` | 201 Created → `workdir.Workdir`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem` |
| DELETE | `/bots/{bot_id}/workdirs/{workdir_id}` | workdirs | Archive a Bot workdir<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>workdir_id (path, required) → `string` | — | 204 No Content<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| PATCH | `/bots/{bot_id}/workdirs/{workdir_id}` | workdirs | Rename a Bot workdir<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>workdir_id (path, required) → `string`<br>request (body, required) → `workdir.UpdateRequest` |  → `workdir.UpdateRequest` | 200 OK → `workdir.Workdir`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| GET | `/bots/{bot_id}/workdirs/{workdir_id}/git-branch` | workdirs | Read a workdir's current Git branch<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>workdir_id (path, required) → `string` | — | 200 OK → `workdir.GitBranchResponse`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/{bot_id}/workdirs/{workdir_id}/git-branch` | workdirs | Switch a workdir to an existing local Git branch<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>workdir_id (path, required) → `string`<br>request (body, required) → `workdir.SwitchGitBranchRequest` |  → `workdir.SwitchGitBranchRequest` | 200 OK → `workdir.GitBranchResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{bot_id}/workdirs/directories` | workdirs | List directories for choosing a workdir path<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>workspace_target_id (query) → `string`<br>path (query) → `string` | — | 200 OK → `workdir.DirectoriesResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{bot_id}/workspace-targets` | workspace-targets | List a Bot's workspace targets<br><code>—</code> | none declared | bot_id (path, required) → `string` | — | 200 OK → `workspace.WorkspaceTargetsResponse`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{bot_id}/workspace-targets/{target_id}` | workspace-targets | Delete a Remote Runtime workspace target<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>target_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| PUT | `/bots/{bot_id}/workspace-targets/{target_id}/tool-approval` | workspace-targets | Update tool approval for one workspace target<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>target_id (path, required) → `string`<br>request (body, required) → `workspace.UpdateWorkspaceTargetToolApprovalRequest` |  → `workspace.UpdateWorkspaceTargetToolApprovalRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| PUT | `/bots/{bot_id}/workspace-targets/primary` | workspace-targets | Set a Bot's Primary workspace target<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>request (body, required) → `workspace.SetPrimaryWorkspaceTargetRequest` |  → `workspace.SetPrimaryWorkspaceTargetRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| PUT | `/bots/{bot_id}/workspace-targets/remotes/{runtime_id}` | workspace-targets | Add or update a Remote Runtime workspace target<br><code>—</code> | none declared | bot_id (path, required) → `string`<br>runtime_id (path, required) → `string` | — | 200 OK → `workspace.WorkspaceTarget`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem` |
| DELETE | `/bots/{id}` | bots | Delete bot<br><code>—</code> | none declared | id (path, required) → `string` | — | 202 Accepted → `object`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/{id}` | bots | Get bot details<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `bots.Bot`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{id}` | bots | Update bot details<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body, required) → `bots.UpdateBotRequest` |  → `bots.UpdateBotRequest` | 200 OK → `bots.Bot`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/bots/{id}/channel/{platform}` | bots | Delete bot channel config<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{id}/channel/{platform}` | bots | Get bot channel config<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string` | — | 200 OK → `channel.ChannelConfig`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{id}/channel/{platform}` | bots | Update bot channel config<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string`<br>payload (body, required) → `channel.UpsertConfigRequest` |  → `channel.UpsertConfigRequest` | 200 OK → `channel.ChannelConfig`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{id}/channel/{platform}/send` | bots | Send message via bot channel<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string`<br>payload (body, required) → `channel.SendRequest` |  → `channel.SendRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{id}/channel/{platform}/send_chat` | bots | Send message via bot channel session token<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string`<br>payload (body, required) → `channel.SendRequest` |  → `channel.SendRequest` | 200 OK → `object`<br>400 Bad Request → `server.Problem`<br>401 Unauthorized → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| PATCH | `/bots/{id}/channel/{platform}/status` | bots | Update bot channel status<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string`<br>payload (body, required) → `channel.UpdateChannelStatusRequest` |  → `channel.UpdateChannelStatusRequest` | 200 OK → `channel.ChannelConfig`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| POST | `/bots/{id}/channel/{platform}/webhook-endpoint` | bots | Set bot channel webhook endpoint<br><code>—</code> | none declared | id (path, required) → `string`<br>platform (path, required) → `string`<br>payload (body, required) → `channel.SetWebhookEndpointRequest` |  → `channel.SetWebhookEndpointRequest` | 200 OK → `channel.SetWebhookEndpointResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/bots/{id}/checks` | bots | List bot runtime checks<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `bots.ListChecksResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/bots/{id}/owner` | bots | Transfer bot owner (admin only)<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body, required) → `bots.TransferBotRequest` |  → `bots.TransferBotRequest` | 200 OK → `bots.Bot`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/backup/import` | bots | Import a bot backup<br><code>—</code> | none declared | file (formData, required) → `file`<br>mode (formData) → `string`<br>target_bot_id (formData) → `string`<br>sections (formData) → `string`<br>passphrase (formData) → `string` | multipart/form-data | 200 OK → `botbackup.ImportResult`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/bots/backup/import/preview` | bots | Preview a bot backup import<br><code>—</code> | none declared | file (formData, required) → `file`<br>mode (formData) → `string`<br>target_bot_id (formData) → `string`<br>sections (formData) → `string`<br>passphrase (formData) → `string` | multipart/form-data | 200 OK → `botbackup.PreviewResult`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/name-availability` | bots | Check bot name availability<br><code>—</code> | none declared | name (query, required) → `string`<br>exclude_bot_id (query) → `string` | — | 200 OK → `bots.NameAvailability`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/bots/user-access/candidates` | bots | Search workspace member candidates for a bot being created<br><code>—</code> | none declared | q (query) → `string`<br>limit (query) → `integer` | — | 200 OK → `handlers.BotUserCandidateListResponse`<br>401 Unauthorized → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/channels` | channel | List channel capabilities and schemas<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/channels/{platform}` | channel | Get channel capabilities and schemas<br><code>—</code> | none declared | platform (path, required) → `string` | — | 200 OK → `handlers.ChannelMeta`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| GET | `/connectors/catalog` | connectors | List connector catalog<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>403 Forbidden → `server.Problem`<br>502 Bad Gateway → `server.Problem`<br>503 Service Unavailable → `server.Problem` |
| GET | `/fetch-providers` | fetch-providers | List fetch providers<br><code>—</code> | none declared | provider (query) → `string` | application/json | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| POST | `/fetch-providers` | fetch-providers | Create a fetch provider<br><code>—</code> | none declared | request (body, required) → `fetchproviders.CreateRequest` |  → `fetchproviders.CreateRequest` | 201 Created → `fetchproviders.GetResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/fetch-providers/{id}` | fetch-providers | Delete a fetch provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/fetch-providers/{id}` | fetch-providers | Get a fetch provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `fetchproviders.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| PUT | `/fetch-providers/{id}` | fetch-providers | Update a fetch provider<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `fetchproviders.UpdateRequest` |  → `fetchproviders.UpdateRequest` | 200 OK → `fetchproviders.GetResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/fetch-providers/meta` | fetch-providers | List fetch provider metadata<br><code>—</code> | none declared | — | — | 200 OK → `array` |
| GET | `/memory/config` | memory | Get Built-in Memory configuration<br><code>—</code> | none declared | — | — | 200 OK → `adapters.MemoryConfig`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/memory/config` | memory | Update Built-in Memory configuration<br><code>—</code> | none declared | request (body, required) → `adapters.MemoryConfigUpdateRequest` |  → `adapters.MemoryConfigUpdateRequest` | 200 OK → `adapters.MemoryConfig`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/models` | models | List all models<br><code>—</code> | none declared | type (query) → `string`<br>client_type (query) → `string` | — | 200 OK → `array`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/models` | models | Create a new model<br><code>—</code> | none declared | payload (body, required) → `models.AddRequest` |  → `models.AddRequest` | 201 Created → `models.AddResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/models/{id}` | models | Delete model by internal ID<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/models/{id}` | models | Get model by internal ID<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `models.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/models/{id}` | models | Update model by internal ID<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body, required) → `models.UpdateRequest` |  → `models.UpdateRequest` | 200 OK → `models.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/models/{id}/test` | models | Test model connectivity<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `models.TestResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/models/count` | models | Get model count<br><code>—</code> | none declared | type (query) → `string` | — | 200 OK → `models.CountResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/models/model/{modelId}` | models | Delete model by model ID<br><code>—</code> | none declared | modelId (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/models/model/{modelId}` | models | Get model by model ID<br><code>—</code> | none declared | modelId (path, required) → `string` | — | 200 OK → `models.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/models/model/{modelId}` | models | Update model by model ID<br><code>—</code> | none declared | modelId (path, required) → `string`<br>payload (body, required) → `models.UpdateRequest` |  → `models.UpdateRequest` | 200 OK → `models.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/oauth/mcp/callback` | mcp | OAuth callback for MCP connections<br><code>—</code> | none declared | code (query) → `string`<br>state (query) → `string`<br>error (query) → `string`<br>error_description (query) → `string` | — | 200 HTML result page → `string`<br>400 HTML error page → `string` |
| GET | `/ping` | system | Health check with server capabilities<br><code>—</code> | none declared | — | — | 200 OK → `handlers.PingResponse` |
| GET | `/provider-templates` | provider-templates | List provider templates<br><code>—</code> | none declared | domain (query) → `string` | — | 200 OK → `array`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/provider-templates/{id}` | provider-templates | Get a provider template<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `providertemplates.GetResponse`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers` | providers | List all LLM providers<br><code>—</code> | none declared | — | application/json | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| POST | `/providers` | providers | Create a new LLM provider<br><code>—</code> | none declared | request (body, required) → `providers.CreateRequest` |  → `providers.CreateRequest` | 201 Created → `providers.GetResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/providers/{id}` | providers | Delete provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 204 No Content<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers/{id}` | providers | Get provider by ID<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `providers.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/providers/{id}` | providers | Update provider<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `providers.UpdateRequest` |  → `providers.UpdateRequest` | 200 OK → `providers.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/providers/{id}/import-models` | providers | Import models from provider<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body) → `providers.ImportModelsRequest` |  → `providers.ImportModelsRequest` | 200 OK → `providers.ImportModelsResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers/{id}/models` | providers | List provider models<br><code>—</code> | none declared | id (path, required) → `string`<br>type (query) → `string` | — | 200 OK → `array`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers/{id}/oauth/authorize` | providers-oauth | Start OAuth2 authorization for an LLM provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `providers.OAuthAuthorizeResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/providers/{id}/oauth/poll` | providers-oauth | Poll OAuth device authorization for an LLM provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `providers.OAuthStatus`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| GET | `/providers/{id}/oauth/status` | providers-oauth | Get OAuth2 status for an LLM provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `providers.OAuthStatus`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| DELETE | `/providers/{id}/oauth/token` | providers-oauth | Revoke stored OAuth2 tokens for an LLM provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/providers/{id}/test` | providers | Test provider connectivity<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `providers.TestResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers/count` | providers | Count providers<br><code>—</code> | none declared | — | application/json | 200 OK → `providers.CountResponse`<br>500 Internal Server Error → `server.Problem` |
| POST | `/providers/from-template` | providers | Create a provider from a global template<br><code>—</code> | none declared | request (body, required) → `providers.CreateFromTemplateRequest` |  → `providers.CreateFromTemplateRequest` | 201 Created → `providers.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers/name/{name}` | providers | Get provider by name<br><code>—</code> | none declared | name (path, required) → `string` | application/json | 200 OK → `providers.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/providers/oauth/callback` | providers-oauth | OAuth2 callback for LLM providers<br><code>—</code> | none declared | code (query, required) → `string`<br>state (query, required) → `string` | — | 200 HTML success page → `string`<br>400 Bad Request → `server.Problem` |
| GET | `/search-providers` | search-providers | List search providers<br><code>—</code> | none declared | provider (query) → `string` | application/json | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| POST | `/search-providers` | search-providers | Create a search provider<br><code>—</code> | none declared | request (body, required) → `searchproviders.CreateRequest` |  → `searchproviders.CreateRequest` | 201 Created → `searchproviders.GetResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/search-providers/{id}` | search-providers | Delete a search provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/search-providers/{id}` | search-providers | Get a search provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `searchproviders.GetResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| PUT | `/search-providers/{id}` | search-providers | Update a search provider<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `searchproviders.UpdateRequest` |  → `searchproviders.UpdateRequest` | 200 OK → `searchproviders.GetResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/search-providers/meta` | search-providers | List search provider metadata<br><code>—</code> | none declared | — | — | 200 OK → `array` |
| GET | `/site-icon` | site-icon | Resolve a public site's favicons for light and dark color schemes<br><code>—</code> | none declared | url (query, required) → `string` | — | 200 OK → `handlers.SiteIconResponse` |
| GET | `/speech-models` | speech-models | List all speech models<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/speech-models/{id}` | speech-models | Get a speech model<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `audio.SpeechModelResponse`<br>404 Not Found → `server.Problem` |
| PUT | `/speech-models/{id}` | speech-models | Update a speech model<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `audio.UpdateSpeechModelRequest` |  → `audio.UpdateSpeechModelRequest` | 200 OK → `audio.SpeechModelResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/speech-models/{id}/capabilities` | speech-models | Get speech model capabilities<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `audio.ModelCapabilities`<br>404 Not Found → `server.Problem` |
| POST | `/speech-models/{id}/test` | speech-models | Test speech model synthesis<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `audio.TestSynthesizeRequest` |  → `audio.TestSynthesizeRequest` | 200 Audio data → `file`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/speech-providers` | speech-providers | List speech providers<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/speech-providers/{id}` | speech-providers | Get speech provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `audio.SpeechProviderResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/speech-providers/{id}/import-models` | speech-providers | Import speech models from provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `audio.ImportModelsResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/speech-providers/{id}/models` | speech-providers | List speech models by provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `array`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/speech-providers/meta` | speech-providers | List speech provider metadata<br><code>—</code> | none declared | — | — | 200 OK → `array` |
| GET | `/supermarket/apps` | supermarket | List Skill Apps across supermarket Registries<br><code>—</code> | none declared | q (query) → `string`<br>registry (query) → `string`<br>category (query) → `string`<br>tag (query) → `string`<br>component (query) → `string`<br>page (query) → `integer`<br>limit (query) → `integer`<br>sort (query) → `string` | — | 200 OK → `handlers.SupermarketAppListResponse`<br>400 Bad Request → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/artifacts/icon/{digest}` | supermarket | Get a mirrored Skill icon<br><code>—</code> | none declared | digest (path, required) → `string` | — | 200 OK → `file`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/categories` | supermarket | List App categories with localized names<br><code>—</code> | none declared | registry (query) → `string` | — | 200 OK → `handlers.SupermarketAppCategoryListResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/registries` | supermarket | List Skill Registries from supermarket<br><code>—</code> | none declared | — | — | 200 OK → `handlers.SupermarketRegistryListResponse`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/registries/{registry_id}/apps` | supermarket | List Skill Apps in one Registry<br><code>—</code> | none declared | registry_id (path, required) → `string`<br>q (query) → `string`<br>category (query) → `string`<br>tag (query) → `string`<br>page (query) → `integer`<br>limit (query) → `integer`<br>sort (query) → `string` | — | 200 OK → `handlers.SupermarketAppListResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/registries/{registry_id}/apps/{app_id}` | supermarket | Get a namespaced Skill App<br><code>—</code> | none declared | registry_id (path, required) → `string`<br>app_id (path, required) → `string` | — | 200 OK → `handlers.SupermarketAppDescriptor`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/registries/{registry_id}/apps/{app_id}/releases/{revision}` | supermarket | Get an immutable Skill App release<br><code>—</code> | none declared | registry_id (path, required) → `string`<br>app_id (path, required) → `string`<br>revision (path, required) → `string` | — | 200 OK → `handlers.SupermarketAppDescriptor`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/registries/{registry_id}/apps/{app_id}/skills/{skill_id}` | supermarket | Get a namespaced Registry Skill<br><code>—</code> | none declared | registry_id (path, required) → `string`<br>app_id (path, required) → `string`<br>skill_id (path, required) → `string` | — | 200 OK → `handlers.SupermarketCatalogSkill`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/supermarket/skills` | supermarket | List Skills across supermarket Registries<br><code>—</code> | none declared | q (query) → `string`<br>registry (query) → `string`<br>app (query) → `string`<br>category (query) → `string`<br>tag (query) → `string`<br>page (query) → `integer`<br>limit (query) → `integer`<br>sort (query) → `string` | — | 200 OK → `handlers.SupermarketCatalogSkillListResponse`<br>400 Bad Request → `server.Problem`<br>502 Bad Gateway → `server.Problem` |
| GET | `/transcription-models` | transcription-models | List all transcription models<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/transcription-models/{id}` | transcription-models | Get a transcription model<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `audio.TranscriptionModelResponse`<br>404 Not Found → `server.Problem` |
| PUT | `/transcription-models/{id}` | transcription-models | Update a transcription model<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `audio.UpdateSpeechModelRequest` |  → `audio.UpdateSpeechModelRequest` | 200 OK → `audio.TranscriptionModelResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/transcription-models/{id}/capabilities` | transcription-models | Get transcription model capabilities<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `audio.ModelCapabilities`<br>404 Not Found → `server.Problem` |
| POST | `/transcription-models/{id}/test` | transcription-models | Test transcription model recognition<br><code>—</code> | none declared | id (path, required) → `string`<br>file (formData, required) → `file`<br>config (formData) → `string` | multipart/form-data | 200 OK → `audio.TestTranscriptionResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/transcription-providers` | transcription-providers | List transcription providers<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/transcription-providers/{id}` | speech-providers | Get speech provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `audio.SpeechProviderResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/transcription-providers/{id}/import-models` | transcription-providers | Import transcription models from provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `audio.ImportModelsResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/transcription-providers/{id}/models` | transcription-providers | List transcription models by provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `array`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/transcription-providers/meta` | transcription-providers | List transcription provider metadata<br><code>—</code> | none declared | — | — | 200 OK → `array` |
| GET | `/users` | users | List users (admin only)<br><code>—</code> | none declared | — | — | 200 OK → `accounts.ListAccountsResponse`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/users` | users | Create human user (admin only)<br><code>—</code> | none declared | payload (body, required) → `accounts.CreateAccountRequest` |  → `accounts.CreateAccountRequest` | 201 Created → `accounts.Account`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/users/{id}` | users | Deactivate member (admin only)<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/users/{id}` | users | Get user by ID<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `accounts.Account`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/users/{id}` | users | Update user (admin only)<br><code>—</code> | none declared | id (path, required) → `string`<br>payload (body, required) → `accounts.UpdateAccountRequest` |  → `accounts.UpdateAccountRequest` | 200 OK → `accounts.Account`<br>400 Bad Request → `server.Problem`<br>403 Forbidden → `server.Problem`<br>404 Not Found → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/users/me` | users | Get current user<br><code>—</code> | none declared | — | — | 200 OK → `accounts.Account`<br>400 Bad Request → `server.Problem`<br>401 Unauthorized → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/users/me` | users | Update current user profile<br><code>—</code> | none declared | payload (body, required) → `accounts.UpdateProfileRequest` |  → `accounts.UpdateProfileRequest` | 200 OK → `accounts.Account`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/users/me/channel-identities` | users | List connected channel identities<br><code>—</code> | none declared | — | — | 200 OK → `channelaccess.ListBindingsResponse`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/users/me/channel-identities/{channel_identity_id}` | users | Disconnect a channel identity<br><code>—</code> | none declared | channel_identity_id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| POST | `/users/me/channel-links` | users | Issue an account link code<br><code>—</code> | none declared | payload (body) → `channelaccess.IssueLinkCodeRequest` |  → `channelaccess.IssueLinkCodeRequest` | 201 Created → `channelaccess.LinkCode`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/users/me/channels/{platform}` | channel | Get channel user config<br><code>—</code> | none declared | platform (path, required) → `string` | — | 200 OK → `channel.ChannelIdentityBinding`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/users/me/channels/{platform}` | channel | Update channel user config<br><code>—</code> | none declared | platform (path, required) → `string`<br>payload (body, required) → `channel.UpsertChannelIdentityConfigRequest` |  → `channel.UpsertChannelIdentityConfigRequest` | 200 OK → `channel.ChannelIdentityBinding`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/users/me/computer-access` | user-runtimes | List the caller's bot-to-Computer access grants<br><code>—</code> | none declared | — | — | 200 OK → `workspace.WorkspaceTargetGrantsResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| PUT | `/users/me/password` | users | Update current user password<br><code>—</code> | none declared | payload (body, required) → `accounts.UpdatePasswordRequest` |  → `accounts.UpdatePasswordRequest` | 204 No Content<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/users/me/runtimes` | user-runtimes | List Remote Runtime credentials<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| POST | `/users/me/runtimes` | user-runtimes | Create a Remote Runtime credential<br><code>—</code> | none declared | request (body, required) → `userruntime.CreateRuntimeRequest` |  → `userruntime.CreateRuntimeRequest` | 201 Created → `userruntime.Runtime`<br>400 Bad Request → `server.Problem`<br>409 Conflict → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| DELETE | `/users/me/runtimes/{id}` | user-runtimes | Revoke a Remote Runtime credential<br><code>—</code> | none declared | id (path, required) → `string` | — | 204 No Content<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/video-models` | video-models | List all video models<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/video-models/{id}` | video-models | Get a video model<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `video.ModelResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| PUT | `/video-models/{id}` | video-models | Update a video model<br><code>—</code> | none declared | id (path, required) → `string`<br>request (body, required) → `video.UpdateModelRequest` |  → `video.UpdateModelRequest` | 200 OK → `video.ModelResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/video-providers` | video-providers | List video providers<br><code>—</code> | none declared | — | — | 200 OK → `array`<br>500 Internal Server Error → `server.Problem` |
| GET | `/video-providers/{id}` | video-providers | Get video provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `video.ProviderResponse`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |
| POST | `/video-providers/{id}/import-models` | video-providers | Import video models from provider<br><code>—</code> | none declared | id (path, required) → `string` | application/json | 200 OK → `video.ImportModelsResponse`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/video-providers/{id}/models` | video-providers | List video models by provider<br><code>—</code> | none declared | id (path, required) → `string` | — | 200 OK → `array`<br>400 Bad Request → `server.Problem`<br>500 Internal Server Error → `server.Problem` |
| GET | `/video-providers/meta` | video-providers | List video provider metadata<br><code>—</code> | none declared | — | — | 200 OK → `array` |
| GET | `/webhook-tunnel/status` | system | Get webhook tunnel status<br><code>—</code> | none declared | — | — | 200 OK → `webhooktunnel.Status` |
| GET | `/workspace-dependencies` | containerd | List published workspace dependency metadata<br><code>—</code> | none declared | — | — | 200 OK → `handlers.WorkspaceDependencyCatalogResponse`<br>503 Service Unavailable → `server.Problem` |
| GET | `/workspace-dependencies/icons/{digest}` | containerd | Read a cached verified dependency icon<br><code>—</code> | none declared | digest (path, required) → `string` | — | 200 OK → `file`<br>400 Bad Request → `server.Problem`<br>404 Not Found → `server.Problem` |

## Schema definitions

### `accounts.Account`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `created_at` | `string` | no | — |
| `display_name` | `string` | no | — |
| `email` | `string` | no | — |
| `id` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `joined_at` | `string` | no | — |
| `last_login_at` | `string` | no | — |
| `membership_is_active` | `boolean` | no | — |
| `membership_updated_at` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `principal_is_active` | `boolean` | no | — |
| `role` | `string` | no | — |
| `timezone` | `string` | no | — |
| `title_model_id` | `string` | no | — |
| `updated_at` | `string` | no | — |
| `username` | `string` | no | — |

### `accounts.CreateAccountRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `display_name` | `string` | no | — |
| `email` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `password` | `string` | no | — |
| `role` | `string` | no | — |
| `username` | `string` | no | — |

### `accounts.ListAccountsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of accounts.Account` | no | — |

### `accounts.UpdateAccountRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `is_active` | `boolean` | no | — |
| `role` | `string` | no | — |

### `accounts.UpdatePasswordRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `current_password` | `string` | no | — |
| `new_password` | `string` | no | — |

### `accounts.UpdateProfileMetadata`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `onboarding_completed` | `boolean` | no | — |

### `accounts.UpdateProfileRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `display_name` | `string` | no | — |
| `metadata` | `accounts.UpdateProfileMetadata` | no | — |
| `timezone` | `string` | no | — |
| `title_model_id` | `string` | no | — |

### `acl.ChannelIdentityCandidate`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `channel` | `string` | no | — |
| `channel_subject_id` | `string` | no | — |
| `display_name` | `string` | no | — |
| `id` | `string` | no | — |

### `acl.ChannelIdentityCandidateListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of acl.ChannelIdentityCandidate` | no | — |

### `acl.CreateRuleRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_id` | `string` | no | — |
| `description` | `string` | no | — |
| `effect` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `source_scope` | `acl.SourceScope` | no | — |
| `subject_channel_type` | `string` | no | — |

### `acl.DefaultEffectResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `default_effect` | `string` | no | — |

### `acl.ListRulesResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of acl.Rule` | no | — |

### `acl.ObservedConversationCandidate`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel` | `string` | no | — |
| `conversation_avatar_url` | `string` | no | — |
| `conversation_id` | `string` | no | — |
| `conversation_name` | `string` | no | — |
| `conversation_type` | `string` | no | — |
| `last_observed_at` | `string` | no | — |
| `route_id` | `string` | no | — |
| `thread_id` | `string` | no | — |

### `acl.ObservedConversationCandidateListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of acl.ObservedConversationCandidate` | no | — |

### `acl.Rule`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string` | no | — |
| `bot_id` | `string` | no | — |
| `channel_identity_avatar_url` | `string` | no | — |
| `channel_identity_display_name` | `string` | no | — |
| `channel_identity_id` | `string` | no | — |
| `channel_subject_id` | `string` | no | — |
| `channel_type` | `string` | no | — |
| `created_at` | `string` | no | — |
| `description` | `string` | no | — |
| `effect` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `id` | `string` | no | — |
| `source_conversation_avatar_url` | `string` | no | — |
| `source_conversation_name` | `string` | no | — |
| `source_scope` | `acl.SourceScope` | no | — |
| `subject_channel_type` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `acl.SourceScope`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `conversation_id` | `string` | no | — |
| `conversation_type` | `string` | no | — |
| `thread_id` | `string` | no | — |

### `acl.UpdateRuleRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_id` | `string` | no | — |
| `description` | `string` | no | — |
| `effect` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `source_scope` | `acl.SourceScope` | no | — |
| `subject_channel_type` | `string` | no | — |

### `acpagent.RuntimeStatus`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_session_id` | `string` | no | — |
| `agent_id` | `string` | no | — |
| `available_commands` | `array[] of acpclient.AvailableCommandInfo` | no | — |
| `default_model_id` | `string` | no | — |
| `models` | `acpclient.ModelState` | no | — |
| `modes` | `acpclient.ModeState` | no | — |
| `project_path` | `string` | no | — |
| `reasoning` | `acpclient.ReasoningState` | no | — |
| `runtime_id` | `string` | no | — |
| `session_id` | `string` | no | — |
| `state` | `string` | no | — |

### `acpclient.AvailableCommandInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `input_hint` | `string` | no | — |
| `name` | `string` | no | — |

### `acpclient.ModeInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |

### `acpclient.ModelInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |

### `acpclient.ModelState`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `available_models` | `array[] of acpclient.ModelInfo` | no | — |
| `current_model_id` | `string` | no | — |
| `supported` | `boolean` | no | — |

### `acpclient.ModeState`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `available_modes` | `array[] of acpclient.ModeInfo` | no | — |
| `current_mode_id` | `string` | no | — |
| `supported` | `boolean` | no | — |

### `acpclient.ReasoningEffortInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |

### `acpclient.ReasoningState`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `available_efforts` | `array[] of acpclient.ReasoningEffortInfo` | no | — |
| `current_effort` | `string` | no | — |
| `supported` | `boolean` | no | — |

### `acpprofile.ManagedField`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `help` | `string` | no | — |
| `id` | `string` | no | — |
| `label` | `string` | no | — |
| `placeholder` | `string` | no | — |
| `required` | `boolean` | no | — |
| `sensitive` | `boolean` | no | — |
| `type` | `string` | no | — |

### `acpprofile.ProfilesResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of acpprofile.PublicProfile` | no | — |

### `acpprofile.PublicProfile`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `display_name` | `string` | no | — |
| `id` | `string` | no | — |
| `managed_fields` | `array[] of acpprofile.ManagedField` | no | — |
| `setup_modes` | `array[] of string` | no | — |
| `supported_backends` | `array[] of string` | no | — |

### `adapters.CompactResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `after_count` | `integer` | no | — |
| `before_count` | `integer` | no | — |
| `ratio` | `number` | no | — |
| `results` | `array[] of adapters.MemoryItem` | no | — |

### `adapters.CompactUnavailableReason`

Schema: `string enum(unsupported, not_configured)`

### `adapters.DeleteResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `message` | `string` | no | — |

### `adapters.HealthStatus`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `error` | `string` | no | — |
| `ok` | `boolean` | no | — |

### `adapters.IngestResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ingested` | `integer` | no | Ingested is the number of memory nodes written to the store (inserts + updates; re-ingesting an unchanged file counts as an update). |
| `skipped` | `integer` | no | Skipped is the number of source items that parsed to empty content or failed to persist. |

### `adapters.MemoryCompactCapability`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `archive` | `boolean` | no | — |
| `reason` | `any & adapters.CompactUnavailableReason` | no | Reason says why Semantic is false. |
| `rebuild_index` | `boolean` | no | — |
| `semantic` | `boolean` | no | — |

### `adapters.MemoryConfig`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `embedding_model_id` | `string` | no | EmbeddingModelID optionally maintains the pgvector semantic seed index for graph recall. Empty means graph-only recall. |

### `adapters.MemoryConfigUpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `embedding_model_id` | `string` | no | — |

### `adapters.MemoryItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `agent_id` | `string` | no | — |
| `bot_id` | `string` | no | — |
| `created_at` | `string` | no | — |
| `hash` | `string` | no | — |
| `id` | `string` | no | — |
| `memory` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `run_id` | `string` | no | — |
| `score` | `number` | no | — |
| `updated_at` | `string` | no | — |

### `adapters.MemoryStatusResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `can_manual_sync` | `boolean` | no | — |
| `compact` | `adapters.MemoryCompactCapability` | no | — |
| `degraded` | `boolean` | no | Degraded reports that the semantic seed index is behind the wiki store (failed upserts are queued for retry); graph recall still works. |
| `edge_count` | `integer` | no | — |
| `encoder` | `adapters.HealthStatus` | no | — |
| `indexed_count` | `integer` | no | — |
| `markdown_file_count` | `integer` | no | — |
| `memory_mode` | `string` | no | — |
| `overview_path` | `string` | no | — |
| `pgvector` | `adapters.HealthStatus` | no | — |
| `provider_type` | `string` | no | — |
| `retry_queue_depth` | `integer` | no | — |
| `source_count` | `integer` | no | — |
| `source_dir` | `string` | no | — |
| `vector_index` | `string` | no | — |

### `adapters.Message`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `content` | `string` | no | — |
| `role` | `string` | no | — |

### `adapters.RebuildResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fs_count` | `integer` | no | — |
| `missing_count` | `integer` | no | — |
| `restored_count` | `integer` | no | — |
| `storage_count` | `integer` | no | — |

### `adapters.SearchResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fallback_reason` | `string` | no | — |
| `relations` | `array[] of any` | no | — |
| `results` | `array[] of adapters.MemoryItem` | no | — |
| `retrieval_mode` | `string` | no | — |

### `adapters.UsageResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avg_text_bytes` | `integer` | no | — |
| `count` | `integer` | no | — |
| `estimated_storage_bytes` | `integer` | no | — |
| `total_text_bytes` | `integer` | no | — |

### `agentcredential.Authorization`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_kind` | `string` | yes | — |
| `authorization_url` | `string` | no | — |
| `expires_at` | `string` | yes | — |
| `id` | `string` | yes | — |
| `interval_seconds` | `integer` | no | — |
| `runtime` | `string` | yes | — |
| `status` | `string enum(pending, ready, claimed)` | yes | — |
| `user_code` | `string` | no | — |
| `verification_url` | `string` | no | — |

### `agentcredential.AuthorizationRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_kind` | `string` | yes | — |
| `runtime` | `string enum(codex, claude-code, grok-build)` | yes | — |
| `secret` | `object map of string` | no | — |

### `agentcredential.PublicCredential`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `account_metadata` | `object map of any` | no | — |
| `auth_kind` | `string` | no | — |
| `created_at` | `string` | no | — |
| `credential_version` | `integer` | no | — |
| `expires_at` | `string` | no | — |
| `id` | `string` | no | — |
| `label` | `string` | no | — |
| `owner_user_id` | `string` | no | — |
| `provider` | `string` | no | — |
| `revoked` | `boolean` | no | — |
| `updated_at` | `string` | no | — |

### `apperror.Fault`

Schema: `string enum(client, server, dependency, canceled)`

### `audio.ConfigSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fields` | `array[] of audio.FieldSchema` | no | — |

### `audio.FieldSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `advanced` | `boolean` | no | — |
| `description` | `string` | no | — |
| `enum` | `array[] of string` | no | — |
| `example` | `any` | no | — |
| `key` | `string` | no | — |
| `order` | `integer` | no | — |
| `required` | `boolean` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |

### `audio.ImportModelsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `created` | `integer` | no | — |
| `models` | `array[] of string` | no | — |
| `skipped` | `integer` | no | — |

### `audio.ModelCapabilities`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `audio.ConfigSchema` | no | — |
| `formats` | `array[] of string` | no | — |
| `metadata` | `object map of string` | no | — |
| `pitch` | `audio.ParamConstraint` | no | — |
| `speed` | `audio.ParamConstraint` | no | — |
| `voices` | `array[] of audio.VoiceInfo` | no | — |

### `audio.ModelInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `capabilities` | `audio.ModelCapabilities` | no | — |
| `config_schema` | `audio.ConfigSchema` | no | — |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `template_only` | `boolean` | no | — |

### `audio.ParamConstraint`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `default` | `number` | no | — |
| `max` | `number` | no | — |
| `min` | `number` | no | — |
| `options` | `array[] of number` | no | — |

### `audio.ProviderMetaResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `audio.ConfigSchema` | no | — |
| `default_model` | `string` | no | — |
| `default_synthesis_model` | `string` | no | — |
| `default_transcription_model` | `string` | no | — |
| `description` | `string` | no | — |
| `display_name` | `string` | no | — |
| `models` | `array[] of audio.ModelInfo` | no | — |
| `provider` | `string` | no | — |
| `supports_synthesis_list` | `boolean` | no | — |
| `supports_transcription_list` | `boolean` | no | — |
| `synthesis_models` | `array[] of audio.ModelInfo` | no | — |
| `transcription_models` | `array[] of audio.ModelInfo` | no | — |

### `audio.SpeechModelResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `provider_type` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `audio.SpeechProviderResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `client_type` | `string` | no | — |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `enable` | `boolean` | no | — |
| `icon` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `audio.TestSynthesizeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `text` | `string` | no | — |

### `audio.TestTranscriptionResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `duration_seconds` | `number` | no | — |
| `language` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `text` | `string` | no | — |
| `words` | `array[] of audio.TranscriptionWord` | no | — |

### `audio.TranscriptionModelResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `provider_type` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `audio.TranscriptionWord`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `end` | `number` | no | — |
| `speaker_id` | `string` | no | — |
| `start` | `number` | no | — |
| `text` | `string` | no | — |

### `audio.UpdateSpeechModelRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `name` | `string` | no | — |

### `audio.VoiceInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | `string` | no | — |
| `lang` | `string` | no | — |
| `name` | `string` | no | — |

### `botagents.BotAgent`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `agent_credential_id` | `string` | no | AgentCredentialID points at the encrypted credential this instance uses; empty means not connected (legacy metadata path). |
| `bot_id` | `string` | no | — |
| `created_at` | `string` | no | — |
| `deleted_at` | `string` | no | — |
| `dependency` | `any & botagents.DependencyRequirement` | no | Dependency comes from the runtime driver at read time. It is not persisted and is omitted for runtimes without a declaration (ACP). |
| `enabled` | `boolean` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `runtime` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `botagents.CreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | no | Enabled defaults to true when omitted. The web passes false for direct runtimes so the dependency preflight runs before the agent goes live. |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `runtime` | `string` | no | — |

### `botagents.DependencyRequirement`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dependency_id` | `string` | no | — |

### `botagents.ListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of botagents.BotAgent` | no | — |

### `botagents.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |

### `botbackup.ExportRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `passphrase` | `string` | no | — |
| `sections` | `array[] of botbackup.Section` | no | — |

### `botbackup.ImportMode`

Schema: `string enum(create, overwrite)`

### `botbackup.ImportResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_id` | `string` | no | — |
| `created` | `boolean` | no | — |
| `imported` | `object map of integer` | no | Imported reports how many items were restored per section, powering the post-import summary in the UI. |
| `warnings` | `array[] of string` | no | — |

### `botbackup.Manifest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app` | `string` | no | — |
| `checksums` | `object map of string` | no | — |
| `entries` | `array[] of botbackup.ManifestEntry` | no | — |
| `exported_at` | `string` | no | — |
| `options` | `botbackup.ManifestOptions` | no | — |
| `schema_version` | `integer` | no | — |
| `source_bot_id` | `string` | no | — |
| `source_bot_name` | `string` | no | — |
| `warnings` | `array[] of string` | no | — |

### `botbackup.ManifestEntry`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | no | — |
| `type` | `string` | no | — |

### `botbackup.ManifestOptions`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `sections` | `array[] of botbackup.Section` | no | — |

### `botbackup.PreviewResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `conflicts` | `array[] of string` | no | — |
| `encrypted` | `boolean` | no | Encrypted reports that the uploaded bundle is passphrase-encrypted. When true and no (or a wrong) passphrase was supplied, the other fields are empty and the UI should prompt for the passphrase. RequiresPassphrase distinguishes "needs a passphrase" from "passphrase was wrong". |
| `manifest` | `botbackup.Manifest` | no | — |
| `missing` | `array[] of string` | no | — |
| `profile` | `botbackup.ProfilePreview` | no | — |
| `requires_passphrase` | `boolean` | no | — |
| `restore_plan` | `botbackup.RestorePlan` | no | — |
| `sections` | `array[] of botbackup.SectionSummary` | no | — |
| `warnings` | `array[] of string` | no | — |

### `botbackup.ProfilePreview`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `display_name` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `timezone` | `string` | no | — |

### `botbackup.RestorePlan`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dependency_matches` | `object map of integer` | no | — |
| `mode` | `botbackup.ImportMode` | no | — |
| `target_bot_id` | `string` | no | — |
| `will_create_bot` | `boolean` | no | — |
| `will_restore_workspace` | `boolean` | no | — |

### `botbackup.Section`

Schema: `string enum(profile, settings, models, acl, channels, mcp, schedules, history, assets, workspace)`

### `botbackup.SectionSummary`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `conflict` | `boolean` | no | — |
| `count` | `integer` | no | — |
| `items` | `array[] of string` | no | — |
| `key` | `botbackup.Section` | no | — |
| `sensitive` | `boolean` | no | — |
| `target_count` | `integer` | no | — |

### `botbackup.SummaryResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profile` | `botbackup.ProfilePreview` | no | — |
| `sections` | `array[] of botbackup.SectionSummary` | no | — |

### `bots.Bot`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `check_issue_count` | `integer` | no | — |
| `check_state` | `string` | no | — |
| `created_at` | `string` | no | — |
| `current_user_permissions` | `array[] of string` | no | CurrentUserPermissions lists the effective access permissions of the requesting user on this bot (e.g. "chat", "manage"). It is populated by the API layer per request and is not persisted. |
| `display_name` | `string` | no | — |
| `id` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `owner_user_id` | `string` | no | — |
| `status` | `string` | no | — |
| `timezone` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `bots.BotCheck`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `detail` | `string` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `status` | `string` | no | — |
| `subtitle` | `string` | no | — |
| `summary` | `string` | no | — |
| `title_key` | `string` | no | — |
| `type` | `string` | no | — |

### `bots.CreateBotRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acl_preset` | `string` | no | — |
| `avatar_url` | `string` | no | — |
| `display_name` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `timezone` | `string` | no | — |
| `wait_for_ready` | `boolean` | no | — |

### `bots.CreateUserGrantRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `permissions` | `array[] of string` | no | — |
| `subject_type` | `string` | no | — |
| `user_id` | `string` | no | — |

### `bots.ListBotsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of bots.Bot` | no | — |

### `bots.ListChecksResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of bots.BotCheck` | no | — |

### `bots.NameAvailability`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `available` | `boolean` | no | — |
| `reason` | `string` | no | — |

### `bots.TransferBotRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `owner_user_id` | `string` | no | — |

### `bots.UpdateBotRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `display_name` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `timezone` | `string` | no | — |

### `bots.UpdateUserGrantRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `permissions` | `array[] of string` | no | — |

### `bots.UserGrant`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_id` | `string` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `is_owner` | `boolean` | no | — |
| `permissions` | `array[] of string` | no | — |
| `subject_type` | `string` | no | — |
| `updated_at` | `string` | no | — |
| `user_avatar_url` | `string` | no | — |
| `user_display_name` | `string` | no | — |
| `user_id` | `string` | no | — |
| `user_username` | `string` | no | — |

### `channel.Action`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `label` | `string` | no | — |
| `row` | `integer` | no | Row groups buttons into keyboard rows for renderers that support grids (e.g. Telegram inline keyboards). Buttons sharing a Row render together; rows appear in ascending numeric order. Renderers without grid support ignore this field. 0 is the default (single row, prior behavior). |
| `type` | `string` | no | — |
| `url` | `string` | no | — |
| `value` | `string` | no | — |

### `channel.Attachment`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `base64` | `string` | no | data URL for agent delivery |
| `caption` | `string` | no | — |
| `content_hash` | `string` | no | — |
| `duration_ms` | `integer` | no | — |
| `height` | `integer` | no | — |
| `metadata` | `object map of any` | no | — |
| `mime` | `string` | no | — |
| `name` | `string` | no | — |
| `path` | `string` | no | workspace-local filesystem path |
| `platform_key` | `string` | no | — |
| `size` | `integer` | no | — |
| `source_platform` | `string` | no | — |
| `thumbnail_url` | `string` | no | — |
| `type` | `channel.AttachmentType` | no | — |
| `url` | `string` | no | HTTP(S) or data URL |
| `width` | `integer` | no | — |

### `channel.AttachmentType`

Schema: `string enum(image, audio, video, voice, file, gif)`

### `channel.ChannelCapabilities`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attachments` | `boolean` | no | — |
| `block_streaming` | `boolean` | no | — |
| `buttons` | `boolean` | no | — |
| `chat_types` | `array[] of string` | no | — |
| `edit` | `boolean` | no | — |
| `markdown` | `boolean` | no | — |
| `media` | `boolean` | no | — |
| `native_commands` | `boolean` | no | — |
| `native_user_input` | `boolean` | no | — |
| `polls` | `boolean` | no | — |
| `reactions` | `boolean` | no | — |
| `reply` | `boolean` | no | — |
| `rich_text` | `boolean` | no | — |
| `streaming` | `boolean` | no | — |
| `text` | `boolean` | no | — |
| `threads` | `boolean` | no | — |
| `unsend` | `boolean` | no | — |
| `url_buttons` | `boolean` | no | — |

### `channel.ChannelConfig`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_id` | `string` | no | — |
| `channel_type` | `channel.ChannelType` | no | — |
| `created_at` | `string` | no | — |
| `credentials` | `object map of any` | no | — |
| `disabled` | `boolean` | no | — |
| `external_identity` | `string` | no | — |
| `id` | `string` | no | — |
| `routing` | `object map of any` | no | — |
| `self_identity` | `object map of any` | no | — |
| `team_id` | `string` | no | — |
| `updated_at` | `string` | no | — |
| `verified_at` | `string` | no | — |

### `channel.ChannelIdentityBinding`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_id` | `string` | no | — |
| `channel_type` | `channel.ChannelType` | no | — |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `channel.ChannelType`

Schema: `string enum(telegram, feishu, dingtalk, matrix, discord, qq, wecom, weixin, wechatoa, local, slack, line)`

### `channel.ConfigSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fields` | `object map of channel.FieldSchema` | no | — |
| `version` | `integer` | no | — |

### `channel.FieldSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `enum` | `array[] of string` | no | — |
| `example` | `any` | no | — |
| `order` | `integer` | no | — |
| `required` | `boolean` | no | — |
| `title` | `string` | no | — |
| `type` | `channel.FieldType` | no | — |

### `channel.FieldType`

Schema: `string enum(string, secret, bool, number, enum)`

### `channel.ForwardRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attachments_known` | `boolean` | no | — |
| `date` | `integer` | no | — |
| `from_conversation_id` | `string` | no | — |
| `from_user_id` | `string` | no | — |
| `message_id` | `string` | no | — |
| `sender` | `string` | no | — |

### `channel.Message`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `actions` | `array[] of channel.Action` | no | — |
| `attachments` | `array[] of channel.Attachment` | no | — |
| `format` | `channel.MessageFormat` | no | — |
| `forward` | `channel.ForwardRef` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `parts` | `array[] of channel.MessagePart` | no | — |
| `reply` | `channel.ReplyRef` | no | — |
| `text` | `string` | no | — |
| `thread` | `channel.ThreadRef` | no | — |

### `channel.MessageFormat`

Schema: `string enum(plain, markdown, rich)`

### `channel.MessagePart`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_id` | `string` | no | — |
| `emoji` | `string` | no | — |
| `language` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `styles` | `array[] of channel.MessageTextStyle` | no | — |
| `text` | `string` | no | — |
| `type` | `channel.MessagePartType` | no | — |
| `url` | `string` | no | — |

### `channel.MessagePartType`

Schema: `string enum(text, link, code_block, mention, emoji, heading, blockquote, list_item)`

### `channel.MessageTextStyle`

Schema: `string enum(bold, italic, strikethrough, code, underline, spoiler)`

### `channel.ReplyRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attachments` | `array[] of channel.Attachment` | no | — |
| `attachments_known` | `boolean` | no | — |
| `message_id` | `string` | no | — |
| `preview` | `string` | no | — |
| `sender` | `string` | no | — |
| `target` | `string` | no | — |

### `channel.SendRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_id` | `string` | no | — |
| `message` | `channel.Message` | no | — |
| `target` | `string` | no | — |

### `channel.SetWebhookEndpointRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `endpoint` | `string` | no | — |

### `channel.SetWebhookEndpointResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `endpoint` | `string` | no | — |

### `channel.TargetHint`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `example` | `string` | no | — |
| `label` | `string` | no | — |

### `channel.TargetSpec`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `format` | `string` | no | — |
| `hints` | `array[] of channel.TargetHint` | no | — |

### `channel.ThreadRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | `string` | no | — |

### `channel.UpdateChannelStatusRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `disabled` | `boolean` | no | — |

### `channel.UpsertChannelIdentityConfigRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |

### `channel.UpsertConfigRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `credentials` | `object map of any` | no | — |
| `disabled` | `boolean` | no | — |
| `external_identity` | `string` | no | — |
| `routing` | `object map of any` | no | — |
| `self_identity` | `object map of any` | no | — |
| `verified_at` | `string` | no | — |

### `channelaccess.Binding`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_avatar_url` | `string` | no | — |
| `channel_identity_display_name` | `string` | no | — |
| `channel_identity_id` | `string` | no | — |
| `channel_subject_id` | `string` | no | — |
| `channel_type` | `string` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `user_id` | `string` | no | — |

### `channelaccess.IssueLinkCodeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_type` | `string` | no | — |

### `channelaccess.LinkCode`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_type` | `string` | no | — |
| `created_at` | `string` | no | — |
| `expires_at` | `string` | no | — |
| `token` | `string` | no | — |
| `user_id` | `string` | no | — |

### `channelaccess.ListBindingsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of channelaccess.Binding` | no | — |

### `channelaccess.ListManagersResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of channelaccess.Manager` | no | — |

### `channelaccess.Manager`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bound` | `boolean` | no | Bound reports whether this identity is linked to a workspace member of this bot (any permission). It marks the identity as a "platform member" in the UI regardless of whether it carries Manage. |
| `channel_identity_avatar_url` | `string` | no | — |
| `channel_identity_display_name` | `string` | no | — |
| `channel_identity_id` | `string` | no | — |
| `channel_subject_id` | `string` | no | — |
| `channel_type` | `string` | no | — |
| `has_override` | `boolean` | no | HasOverride reports whether a local Channel Access override exists for it. |
| `inherited` | `boolean` | no | Inherited reports whether this identity is bound to a web member that carries the Manage capability (owner or workspace manage grant). |
| `manage` | `boolean` | no | Manage is the effective Manage capability (local override ?? inherited). |

### `channelaccess.SetManagerRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `channel_identity_id` | `string` | no | — |
| `granted` | `boolean` | no | Granted forces Manage ON (true) or OFF (false) locally. |

### `compaction.ListLogsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of compaction.Log` | no | — |
| `total_count` | `integer` | no | — |

### `compaction.Log`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_id` | `string` | no | — |
| `completed_at` | `string` | no | — |
| `error_message` | `string` | no | — |
| `id` | `string` | no | — |
| `message_count` | `integer` | no | — |
| `model_id` | `string` | no | — |
| `session_id` | `string` | no | — |
| `started_at` | `string` | no | — |
| `status` | `string` | no | — |
| `summary` | `string` | no | — |
| `usage` | `any` | no | — |

### `connectit.AuthMethod`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `credential_fields` | `array[] of connectit.CredentialField` | no | — |
| `key` | `string` | no | — |
| `label` | `string` | no | — |
| `type` | `string` | no | — |

### `connectit.Connector`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_methods` | `array[] of connectit.AuthMethod` | no | — |
| `categories` | `array[] of string` | no | — |
| `description` | `string` | no | — |
| `homepage_url` | `string` | no | — |
| `icon_url` | `string` | no | — |
| `mode` | `string` | no | — |
| `name` | `string` | no | — |
| `status` | `string` | no | — |
| `type` | `string` | no | — |

### `connectit.CredentialField`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `default_value` | `string` | no | — |
| `description` | `string` | no | — |
| `input_type` | `string` | no | — |
| `key` | `string` | no | — |
| `label` | `string` | no | — |
| `options` | `array[] of string` | no | — |
| `pattern` | `string` | no | — |
| `required` | `boolean` | no | — |
| `secret` | `boolean` | no | — |

### `connectit.OAuthAuthorization`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `authorization_url` | `string` | no | — |
| `connection_id` | `string` | no | — |

### `connectors.Connector`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `alias` | `string` | no | — |
| `auth_method` | `string` | no | — |
| `connection_id` | `string` | no | — |
| `connector_type` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `status` | `string` | no | — |
| `tool_discovery_error` | `string` | no | ToolDiscoveryError is set when the last attempt to load this connection's tools for the agent failed, even though the connection itself is authorized. Empty when the tools loaded or were never asked. |

### `connectors.ListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of connectors.Connector` | no | — |
| `tool_discovery` | `any & connectors.ToolDiscoveryStatus` | no | ToolDiscovery reports the last outcome of loading the bot's connector tools for the agent; absent until the agent has tried. |

### `connectors.ToolDiscoveryFailure`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `alias` | `string` | no | — |
| `connection_id` | `string` | no | — |
| `error` | `string` | no | — |

### `connectors.ToolDiscoveryStatus`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `error` | `string` | no | — |
| `failed_connections` | `array[] of connectors.ToolDiscoveryFailure` | no | — |
| `observed_at` | `string` | no | — |

### `contextfrag.CacheClass`

Schema: `string enum(stable, dynamic, never)`

### `contextfrag.CacheComparison`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `first_step_cache_read_tokens` | `integer` | no | — |
| `outcome` | `string` | no | — |
| `prev_age_ms` | `integer` | no | — |

### `contextfrag.CacheUsageRecord`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attempt` | `integer` | no | — |
| `cache_read_tokens` | `integer` | no | — |
| `cache_write_1h_tokens` | `integer` | no | — |
| `cache_write_5m_tokens` | `integer` | no | — |
| `cache_write_tokens` | `integer` | no | — |
| `no_cache_tokens` | `integer` | no | — |
| `step_index` | `integer` | no | — |

### `contextfrag.ContentRange`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `end` | `integer` | no | — |
| `start` | `integer` | no | — |

### `contextfrag.ContextBudgetPlan`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `actual_system_cost` | `integer` | no | — |
| `current_request_cost` | `integer` | no | — |
| `estimator` | `string` | no | — |
| `estimator_safety_factor_percent` | `integer` | no | — |
| `history_budget` | `integer` | no | — |
| `output_reserve` | `integer` | no | — |
| `output_reserve_resolution` | `string` | no | — |
| `system_budget` | `integer` | no | — |
| `tool_defs_cost` | `integer` | no | — |
| `window` | `integer` | no | — |

### `contextfrag.ContextRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `content_hash` | `string` | no | — |
| `durability` | `contextfrag.RefDurability` | no | — |
| `hash_algo` | `string` | no | — |
| `hash_scope` | `string` | no | — |
| `id` | `string` | no | — |
| `namespace` | `string` | no | — |
| `range` | `contextfrag.ContentRange` | no | — |
| `schema` | `string` | no | — |
| `version` | `integer` | no | — |

### `contextfrag.Kind`

Schema: `string enum(system_prompt, system_policy, bot_identity, workspace_instruction, platform_identity, tool_usage, conversation_event, current_user_message, attachment_ref, native_image, skills_catalog, hook_context, injected_message, background_summary, runtime_context, memory_recall, conversation_summary)`

### `contextfrag.KindBreakdown`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fragments` | `integer` | no | — |
| `images` | `integer` | no | — |
| `kind` | `contextfrag.Kind` | no | — |
| `text_bytes` | `integer` | no | — |
| `token_estimate` | `integer` | no | — |

### `contextfrag.LifecycleSnapshot`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `assistant_message_id` | `string` | no | — |
| `breakdown` | `array[] of contextfrag.KindBreakdown` | no | — |
| `budget_plan` | `contextfrag.ContextBudgetPlan` | no | — |
| `cache_comparison` | `contextfrag.CacheComparison` | no | — |
| `cache_read_tokens` | `integer` | no | — |
| `cache_usage` | `array[] of contextfrag.CacheUsageRecord` | no | — |
| `cache_write_tokens` | `integer` | no | — |
| `client_type` | `string` | no | — |
| `counts` | `contextfrag.ManifestCounts` | no | — |
| `final_input_hash` | `string` | no | — |
| `loop_selection_mode` | `string` | no | — |
| `memory_recall` | `contextfrag.MemoryRecallTrace` | no | — |
| `model` | `string` | no | — |
| `mutations` | `array[] of contextfrag.MutationRecord` | no | — |
| `selection` | `contextfrag.SelectionTrace` | no | — |
| `selection_decisions` | `array[] of contextfrag.SelectionDecision` | no | — |
| `stable_message_count` | `integer` | no | — |
| `stable_prefix_hash` | `string` | no | — |
| `stable_prefix_token_estimate` | `integer` | no | — |
| `steps` | `array[] of contextfrag.StepSnapshot` | no | — |
| `tool_defs` | `array[] of contextfrag.ToolDefAccounting` | no | — |
| `trust_breakdown` | `array[] of contextfrag.TrustBreakdown` | no | — |
| `version` | `integer` | no | — |
| `view` | `contextfrag.ManifestView` | no | — |

### `contextfrag.ManifestCounts`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fragments` | `integer` | no | — |
| `images` | `integer` | no | — |
| `messages` | `integer` | no | — |
| `text_bytes` | `integer` | no | — |
| `token_estimate` | `integer` | no | — |

### `contextfrag.ManifestView`

Schema: `string enum(run_config_pre_provider, external_agent_prompt)`

### `contextfrag.MemoryRecallQueryTrace`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `recent_messages` | `integer` | no | — |
| `source` | `string` | no | — |
| `truncated` | `boolean` | no | — |

### `contextfrag.MemoryRecallResultTrace`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `context_bytes` | `integer` | no | — |
| `count` | `integer` | no | — |
| `refs` | `array[] of string` | no | — |

### `contextfrag.MemoryRecallTrace`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache_state` | `string` | no | — |
| `fallback_reason` | `string` | no | — |
| `memory_version` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `query` | `contextfrag.MemoryRecallQueryTrace` | no | — |
| `result` | `contextfrag.MemoryRecallResultTrace` | no | — |
| `retrieval_mode` | `string` | no | — |

### `contextfrag.MutationKind`

Schema: `string enum(before_model_call_hook, background_summary, mid_task_prune, loop_step_reselection, injected_message, context_view_fallback, context_budget_failure, context_budget_disabled, capability_gate, read_media, renderer_prune, mid_stream_retry, current_input_omitted, current_input_images_omitted, run_abort_observed)`

### `contextfrag.MutationRecord`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `detail` | `string` | no | — |
| `kind` | `contextfrag.MutationKind` | no | — |

### `contextfrag.RefDurability`

Schema: `string enum(durable, synthetic, debug)`

### `contextfrag.RetentionTier`

Schema: `string enum(, required, preferred, optional)`

### `contextfrag.SelectionDecision`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache_class` | `contextfrag.CacheClass` | no | — |
| `decision` | `contextfrag.SelectionDecisionKind` | no | — |
| `id` | `string` | no | — |
| `image_count` | `integer` | no | — |
| `reason` | `string` | no | — |
| `ref` | `contextfrag.ContextRef` | no | — |
| `retention_tier` | `contextfrag.RetentionTier` | no | — |
| `slot` | `contextfrag.Slot` | no | — |
| `source` | `string` | no | — |
| `source_id` | `string` | no | — |
| `text_bytes` | `integer` | no | — |
| `token_estimate` | `integer` | no | — |

### `contextfrag.SelectionDecisionKind`

Schema: `string enum(selected, trimmed, dropped)`

### `contextfrag.SelectionTrace`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `drop_reason_tokens` | `object map of integer` | no | DropReasonTokens is the token estimate lost per drop reason, rolled up when the snapshot is built so readers never need the per-fragment audit. |
| `drop_reasons` | `object map of integer` | no | — |
| `dropped` | `integer` | no | — |
| `selected` | `integer` | no | — |
| `trimmed` | `integer` | no | — |

### `contextfrag.Slot`

Schema: `string enum(system, before_history, history, after_history_before_current, current_user, after_current)`

### `contextfrag.StepSnapshot`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attempt` | `integer` | no | — |
| `drop_reasons` | `object map of integer` | no | — |
| `dropped` | `integer` | no | — |
| `post_prepare_input_hash` | `string` | no | — |
| `reselection_applied` | `boolean` | no | — |
| `reselection_outcome` | `string` | no | — |
| `step_index` | `integer` | no | — |
| `truncated` | `integer` | no | — |

### `contextfrag.ToolDefAccounting`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bytes` | `integer` | no | — |
| `name` | `string` | no | — |
| `provider` | `string` | no | — |
| `token_estimate` | `integer` | no | — |

### `contextfrag.TrustBreakdown`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fragments` | `integer` | no | — |
| `images` | `integer` | no | — |
| `text_bytes` | `integer` | no | — |
| `token_estimate` | `integer` | no | — |
| `trust` | `contextfrag.TrustLevel` | no | — |

### `contextfrag.TrustLevel`

Schema: `string enum(system, workspace, user, external)`

### `conversation.SkillActivation`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `prompt` | `string` | no | — |
| `skills` | `array[] of conversation.SkillActivationSkill` | no | — |

### `conversation.SkillActivationSkill`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `display_name` | `string` | no | — |
| `name` | `string` | no | — |
| `source_kind` | `string` | no | — |
| `state` | `string` | no | — |

### `conversation.UIAttachment`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `base64` | `string` | no | — |
| `bot_id` | `string` | no | — |
| `content_hash` | `string` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `mime` | `string` | no | — |
| `name` | `string` | no | — |
| `path` | `string` | no | — |
| `size` | `integer` | no | — |
| `storage_key` | `string` | no | — |
| `type` | `string` | no | — |
| `url` | `string` | no | — |

### `conversation.UIBackgroundTask`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `agent_id` | `string` | no | — |
| `agent_session_id` | `string` | no | — |
| `chunk` | `string` | no | — |
| `command` | `string` | no | — |
| `duration` | `string` | no | — |
| `exit_code` | `integer` | no | — |
| `output_file` | `string` | no | — |
| `output_tail` | `string` | no | — |
| `stalled` | `boolean` | no | — |
| `status` | `string` | no | — |
| `stream` | `string` | no | — |
| `task_id` | `string` | no | — |

### `conversation.UIExecutionLocation`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `kind` | `string` | no | — |
| `name` | `string` | no | — |

### `conversation.UIForwardRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `date` | `integer` | no | — |
| `from_conversation_id` | `string` | no | — |
| `from_user_id` | `string` | no | — |
| `message_id` | `string` | no | — |
| `sender` | `string` | no | — |

### `conversation.UIMessage`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `approval` | `conversation.UIToolApproval` | no | — |
| `args` | `object map of string` | no | Args are the machine-readable parameters of a notice or error block: the string values of the runtime_notice event metadata (dep_id and install_task_id for a workspace dependency notice, for instance), or the catalog args stored with a failure's code. The client renders text and actions from them instead of parsing Content. |
| `attachments` | `array[] of conversation.UIAttachment` | no | — |
| `background_task` | `conversation.UIBackgroundTask` | no | — |
| `code` | `string` | no | — |
| `content` | `string` | no | — |
| `diff` | `string` | no | Diff is a UI-only unified diff attached to the tool call at execution time (edit/write tools). It never reaches the model: rows persist it under the diffs metadata key — on the assistant row (lifted out of providerMetadata at store time) or, for the deferred-approval path, on the tool message row — never inside the tool result. |
| `elapsed_time_seconds` | `number` | no | — |
| `execution_location` | `conversation.UIExecutionLocation` | no | — |
| `id` | `integer` | no | — |
| `input` | `any` | no | — |
| `name` | `string` | no | — |
| `output` | `any` | no | — |
| `progress` | `array[] of any` | no | — |
| `reasoning_timing` | `conversation.UIReasoningTiming` | no | — |
| `running` | `boolean` | no | — |
| `tool_call_id` | `string` | no | — |
| `type` | `conversation.UIMessageType` | no | — |
| `user_input` | `conversation.UIUserInput` | no | — |

### `conversation.UIMessageType`

Schema: `string enum(text, reasoning, tool, attachments, error, command, status, notice)`

### `conversation.UIReasoningTiming`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `duration_ms` | `integer` | no | — |

### `conversation.UIReplyRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attachments` | `array[] of conversation.UIAttachment` | no | — |
| `message_id` | `string` | no | — |
| `preview` | `string` | no | — |
| `sender` | `string` | no | — |

### `conversation.UIToolApproval`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `approval_id` | `string` | no | — |
| `can_approve` | `boolean` | no | — |
| `decision_reason` | `string` | no | — |
| `options` | `array[] of conversation.UIToolApprovalOption` | no | Options are the agent-provided permission options, verbatim; the client renders one action per option and answers with the chosen option id. |
| `selected_option_id` | `string` | no | — |
| `short_id` | `integer` | no | — |
| `status` | `string` | no | — |

### `conversation.UIToolApprovalOption`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | `string` | no | — |
| `kind` | `string` | no | — |
| `name` | `string` | no | — |

### `conversation.UITurn`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `attachments` | `array[] of conversation.UIAttachment` | no | — |
| `background_task` | `conversation.UIBackgroundTask` | no | — |
| `external_message_id` | `string` | no | — |
| `forward` | `conversation.UIForwardRef` | no | — |
| `id` | `string` | no | — |
| `kind` | `string` | no | — |
| `messages` | `array[] of conversation.UIMessage` | no | — |
| `platform` | `string` | no | — |
| `reply` | `conversation.UIReplyRef` | no | — |
| `role` | `string enum(user, assistant, system)` | yes | — |
| `runtime_forkable` | `boolean` | no | RuntimeForkable means this persisted turn has a runtime fork anchor. Session-level runtime support and access permissions still apply. |
| `sender_avatar_url` | `string` | no | — |
| `sender_display_name` | `string` | no | — |
| `sender_user_id` | `string` | no | — |
| `skill_activation` | `conversation.SkillActivation` | no | — |
| `text` | `string` | no | — |
| `timestamp` | `string/date-time` | yes | — |
| `turn_id` | `string/uuid` | yes | — |
| `turn_position` | `integer` | no | TurnPosition is the immutable turn-level sequence reserved at admission. The frontend uses it to order turns and reconcile the settled list against live/optimistic turns; never derived from text or timestamps. |
| `user_message_kind` | `string` | no | — |

### `conversation.UIUserInput`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `answers` | `array[] of userinput.UIAnswer` | no | — |
| `can_respond` | `boolean` | no | — |
| `questions` | `array[] of userinput.UIQuestion` | no | — |
| `short_id` | `integer` | no | — |
| `status` | `string` | no | — |
| `user_input_id` | `string` | no | — |

### `display.SessionInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `codec` | `string` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `state` | `string` | no | — |

### `external.Controls`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `capabilities` | `turn.RuntimeControlCapabilities` | no | — |
| `commands` | `array[] of turn.RuntimeCommand` | no | — |
| `modes` | `turn.RuntimeModeState` | no | — |
| `plan_mode` | `turn.RuntimeModeState` | no | — |
| `session_id` | `string` | no | — |

### `external.Goal`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `objective` | `string` | no | — |
| `status` | `string` | no | — |
| `time_used_seconds` | `integer` | no | — |
| `token_budget` | `integer` | no | — |
| `tokens_used` | `integer` | no | — |

### `external.ModelCatalog`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `configured_model_id` | `string` | no | — |
| `configured_reasoning_effort` | `string` | no | — |
| `models` | `array[] of external.ModelOption` | no | — |

### `external.ModelOption`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `default` | `boolean` | no | — |
| `default_reasoning_effort` | `string` | no | — |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `reasoning_efforts` | `array[] of external.ReasoningEffortOption` | no | — |
| `resolved_model_id` | `string` | no | ResolvedModelID preserves a runtime-advertised full model name for validation without duplicating its alias in the model picker. |
| `unavailable_permission_modes` | `array[] of string` | no | IDs belong to this runtime's permission menu, not a shared preset enum. |

### `external.ModeState`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `apply_on_next_turn` | `boolean` | no | Direct runtimes save a preference and apply it when starting the next turn. |
| `available_modes` | `array[] of turn.RuntimeMode` | no | — |
| `current_mode_id` | `string` | no | — |
| `kind` | `string` | no | Kind distinguishes permission presets from arbitrary agent session modes. |
| `supported` | `boolean` | no | — |

### `external.ReasoningEffortOption`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |

### `externalagent.CodexUsageResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `limit_reached` | `boolean` | yes | — |
| `windows` | `array[] of externalagent.CodexUsageWindow` | yes | — |

### `externalagent.CodexUsageWindow`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `resets_at` | `string` | no | — |
| `used_percent` | `integer` | yes | — |
| `window_minutes` | `integer` | yes | WindowMinutes is 0 when the backend does not report the window length. |

### `externalagent.DeviceLoginAuthorizeResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `login_id` | `string` | yes | — |
| `user_code` | `string` | yes | — |
| `verification_url` | `string` | yes | — |

### `externalagent.DeviceLoginPollRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `login_id` | `string` | yes | — |

### `externalagent.DeviceLoginPollResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | `string enum(pending, success, error, unknown)` | yes | — |

### `fetchproviders.CreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `provider` | `fetchproviders.ProviderName` | no | — |

### `fetchproviders.GetResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `enable` | `boolean` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `fetchproviders.ProviderConfigSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fields` | `object map of fetchproviders.ProviderFieldSchema` | no | — |

### `fetchproviders.ProviderFieldSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `enum` | `array[] of string` | no | — |
| `example` | `any` | no | — |
| `required` | `boolean` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |

### `fetchproviders.ProviderMeta`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `fetchproviders.ProviderConfigSchema` | no | — |
| `display_name` | `string` | no | — |
| `provider` | `string` | no | — |

### `fetchproviders.ProviderName`

Schema: `string enum(native, jina, cloudflare_markdown)`

### `fetchproviders.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `enable` | `boolean` | no | — |
| `name` | `string` | no | — |
| `provider` | `fetchproviders.ProviderName` | no | — |

### `github_com_felinics_memoh_internal_mcp.Connection`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_type` | `string` | no | — |
| `bot_id` | `string` | no | — |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `is_active` | `boolean` | no | — |
| `last_probed_at` | `string` | no | — |
| `name` | `string` | no | — |
| `status` | `string` | no | — |
| `status_message` | `string` | no | — |
| `tools_cache` | `array[] of mcp.ToolDescriptor` | no | — |
| `type` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `handlers.acpRuntimeCreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_agent_id` | `string` | no | — |
| `bot_agent_id` | `string` | no | BotAgentID names the Agent instance whose setup launches the runtime; only a session bound to the same instance can later adopt it. |
| `project_path` | `string` | no | — |

### `handlers.acpRuntimeModelRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `model_id` | `string` | no | — |

### `handlers.acpRuntimeModeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `mode_id` | `string` | yes | — |

### `handlers.acpRuntimeReasoningRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `reasoning_effort` | `string` | no | — |

### `handlers.AgentAuthorizationClaimRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `authorization_id` | `string` | yes | — |

### `handlers.AgentAuthorizationExchangeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | `string` | yes | — |

### `handlers.agentCredentialPutRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_kind` | `string` | no | — |
| `secret` | `object map of string` | no | — |

### `handlers.AppConnectorCredentialRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_method` | `string` | yes | — |
| `fields` | `object map of string` | no | — |

### `handlers.AppConnectorItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `connection_id` | `string` | no | — |
| `connector` | `connectors.Connector` | no | — |
| `required` | `boolean` | no | — |
| `status` | `string enum(linked, needs_auth)` | no | Status is linked once a connection is bound, otherwise needs_auth. |
| `type` | `string` | no | — |

### `handlers.AppConnectorOAuthRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_method` | `string` | yes | — |

### `handlers.AppDependencyItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dependency` | `handlers.WorkspaceDependencyItem` | no | — |
| `id` | `string` | no | — |
| `shared` | `boolean` | no | Shared is set when another installed App references the same dependency on this bot workspace. |

### `handlers.AppInstallRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | yes | — |
| `registry_id` | `string` | yes | — |
| `revision` | `string` | yes | — |

### `handlers.AppItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | no | — |
| `author` | `handlers.SupermarketAuthor` | no | — |
| `available_revision` | `string` | no | AvailableRevision and AvailableVersion name the registry's newer release after a check found one. |
| `available_version` | `string` | no | — |
| `category` | `string` | no | — |
| `category_name` | `string` | no | — |
| `connectors` | `array[] of handlers.AppConnectorItem` | no | — |
| `dependencies` | `array[] of handlers.AppDependencyItem` | no | — |
| `description` | `string` | no | — |
| `homepage` | `string` | no | — |
| `icon` | `handlers.SupermarketSkillIcon` | no | — |
| `installation_id` | `string` | no | InstallationID is empty for a discovered App: a dependency the workspace carries that no installed App references, shown through its canonical App. |
| `installed_at` | `string` | no | — |
| `last_checked_at` | `string` | no | — |
| `last_error` | `string` | no | LastError is text recorded by earlier servers. A failure recorded now carries LastErrorCode, which clients render as errors.<code>. |
| `last_error_code` | `string` | no | — |
| `license` | `string` | no | — |
| `name` | `string` | no | — |
| `reason` | `string enum(user, required)` | no | — |
| `registry_id` | `string` | no | — |
| `repository` | `string` | no | — |
| `revision` | `string` | no | — |
| `skills` | `array[] of handlers.AppSkillItem` | no | — |
| `status` | `string enum(installed, partial, installing, updating, removing, failed, discovered)` | no | Status is discovered for Apps without an installation record. |
| `tags` | `array[] of string` | no | — |
| `translations` | `object map of handlers.SupermarketAppTranslation` | no | — |
| `updated_at` | `string` | no | — |
| `version` | `string` | no | — |

### `handlers.AppListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dependency_catalog_refreshing` | `boolean` | no | — |
| `dependency_catalog_stale` | `boolean` | no | — |
| `items` | `array[] of handlers.AppItem` | no | — |
| `update_checks_incomplete` | `boolean` | no | — |
| `workspace_state` | `string enum(running, not_running, missing)` | no | — |

### `handlers.AppRemovalPreviewApp`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | no | — |
| `installation_id` | `string` | no | — |
| `registry_id` | `string` | no | — |
| `version` | `string` | no | — |

### `handlers.AppRemovalPreviewConnector`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string enum(disconnect, keep, none)` | no | — |
| `connection_id` | `string` | no | — |
| `reason` | `string enum(shared)` | no | — |
| `type` | `string` | no | — |

### `handlers.AppRemovalPreviewDependency`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string enum(remove, keep)` | no | — |
| `id` | `string` | no | — |
| `reason` | `string enum(shared, image, absent, required)` | no | — |

### `handlers.AppRemovalPreviewResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `connectors` | `array[] of handlers.AppRemovalPreviewConnector` | no | — |
| `dependencies` | `array[] of handlers.AppRemovalPreviewDependency` | no | — |
| `installation_id` | `string` | no | — |
| `required_apps` | `array[] of handlers.AppRemovalPreviewApp` | no | — |

### `handlers.AppSkillItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `icon` | `handlers.SupermarketSkillIcon` | no | — |
| `install_id` | `string` | no | — |
| `name` | `string` | no | — |
| `skill_id` | `string` | no | — |

### `handlers.AppStreamEvent`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `args` | `object map of string` | no | — |
| `code` | `string` | no | — |
| `data` | `string` | no | — |
| `detail` | `string` | no | — |
| `fault` | `apperror.Fault` | no | — |
| `id` | `string` | no | — |
| `kind` | `string enum(app, dependency, skills, connector)` | no | — |
| `message` | `string` | no | — |
| `request_id` | `string` | no | — |
| `status` | `string` | no | — |
| `stream` | `string enum(stdout, stderr)` | no | — |
| `type` | `string enum(started, step, log, step_done, done, error)` | no | — |
| `version` | `string` | no | — |

### `handlers.AppUpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | yes | — |
| `dependencies` | `array[] of string` | no | Dependencies are updated to their latest version. |
| `registry_id` | `string` | yes | — |
| `release` | `boolean` | no | Release moves the installation to the registry's current release. |

### `handlers.BatchDeleteRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ids` | `array[] of string` | no | — |

### `handlers.BotUserCandidate`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `display_name` | `string` | no | — |
| `id` | `string` | no | — |
| `username` | `string` | no | — |

### `handlers.BotUserCandidateListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of handlers.BotUserCandidate` | no | — |

### `handlers.BotUserGrantListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of bots.UserGrant` | no | — |

### `handlers.browserSessionCreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | no | — |
| `port` | `integer` | no | — |

### `handlers.browserSessionCreateResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `expires_at` | `string` | no | — |
| `id` | `string` | no | — |
| `url` | `string` | no | — |

### `handlers.browserSessionKeepAliveResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `expires_at` | `string` | no | — |
| `id` | `string` | no | — |

### `handlers.CacheStats`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache_hit_rate` | `number` | no | — |
| `cache_read_tokens` | `integer` | no | — |
| `total_input_tokens` | `integer` | no | — |

### `handlers.ChannelMeta`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `capabilities` | `channel.ChannelCapabilities` | no | — |
| `config_schema` | `channel.ConfigSchema` | no | — |
| `configless` | `boolean` | no | — |
| `display_name` | `string` | no | — |
| `owner_only` | `boolean` | no | — |
| `target_spec` | `channel.TargetSpec` | no | — |
| `type` | `string` | no | — |
| `user_config_schema` | `channel.ConfigSchema` | no | — |

### `handlers.CommandActionListItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `i18n_key` | `string` | no | — |
| `id` | `string` | no | — |
| `kind` | `string` | no | — |
| `title` | `string` | no | — |

### `handlers.CommandActionResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | `any` | no | — |
| `items` | `array[] of handlers.CommandActionListItem` | no | — |
| `kind` | `string` | no | — |
| `notice` | `string` | no | — |
| `text` | `string` | no | — |
| `text_key` | `string` | no | — |
| `title` | `string` | no | — |

### `handlers.CommandEventResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action_id` | `string` | no | — |
| `args` | `object map of string` | no | — |
| `code` | `string` | no | Code, Args, Message and Fault describe a command_error, as the error event of a stream does; the client renders the code. |
| `composer_scope` | `string` | no | — |
| `fault` | `apperror.Fault` | no | — |
| `invocation_id` | `string` | no | — |
| `message` | `string` | no | — |
| `result` | `handlers.CommandActionResult` | no | — |
| `session_id` | `string` | no | — |
| `terminal` | `boolean` | no | — |
| `type` | `string` | no | — |

### `handlers.CompactionInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auto_tokens` | `integer` | no | — |
| `enabled` | `boolean` | no | — |

### `handlers.ConnectorEnabledRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | yes | Pointer so an empty or mistyped body fails validation instead of silently disabling the connector. |

### `handlers.ContainerCPUMetricsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cores` | `integer` | no | — |
| `kernel_nanoseconds` | `integer` | no | — |
| `usage_nanocores` | `integer` | no | — |
| `usage_nanoseconds` | `integer` | no | — |
| `usage_percent` | `number` | no | — |
| `user_nanoseconds` | `integer` | no | — |

### `handlers.ContainerGPURequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `devices` | `array[] of string` | no | — |

### `handlers.ContainerMemoryMetricsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `limit_bytes` | `integer` | no | — |
| `usage_bytes` | `integer` | no | — |
| `usage_percent` | `number` | no | — |

### `handlers.ContainerMetricsPayloadResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cpu` | `handlers.ContainerCPUMetricsResponse` | no | — |
| `memory` | `handlers.ContainerMemoryMetricsResponse` | no | — |
| `storage` | `handlers.ContainerStorageMetricsResponse` | no | — |

### `handlers.ContainerMetricsStatusResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `exists` | `boolean` | no | — |
| `task_running` | `boolean` | no | — |

### `handlers.ContainerResourceLimitCapabilitiesResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cpu` | `handlers.ContainerResourceLimitCapabilityResponse` | no | — |
| `memory` | `handlers.ContainerResourceLimitCapabilityResponse` | no | — |
| `storage` | `handlers.ContainerResourceLimitCapabilityResponse` | no | — |

### `handlers.ContainerResourceLimitCapabilityResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `hard_limit_supported` | `boolean` | no | — |
| `soft_limit_supported` | `boolean` | no | — |

### `handlers.ContainerResourceLimitObservedResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cpu_usage_percent` | `number` | no | — |
| `memory_limit_bytes` | `integer` | no | — |
| `memory_usage_bytes` | `integer` | no | — |
| `storage_over_soft_limit` | `boolean` | no | — |
| `storage_used_bytes` | `integer` | no | — |

### `handlers.ContainerResourceLimitValuesResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cpu_millicores` | `integer` | no | — |
| `memory_bytes` | `integer` | no | — |
| `storage_bytes` | `integer` | no | — |

### `handlers.ContainerStorageMetricsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `limit_bytes` | `integer` | no | — |
| `path` | `string` | no | — |
| `used_bytes` | `integer` | no | UsedBytes is omitted when only the allotment is known. |

### `handlers.ContextLifecycleAggregates`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `drop_reasons` | `object map of integer` | no | — |
| `mutation_kinds` | `object map of integer` | no | — |
| `total_cache_read_tokens` | `integer` | no | — |
| `total_cache_write_tokens` | `integer` | no | — |
| `turns` | `integer` | no | — |

### `handlers.ContextLifecycleResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `aggregate_scope` | `string` | no | AggregateScope is always "returned_page": aggregates cover the returned turns, never the whole session. |
| `aggregates` | `handlers.ContextLifecycleAggregates` | no | — |
| `has_more` | `boolean` | no | HasMore reports whether older lifecycle turns exist beyond this page. |
| `legacy_history_may_exist` | `boolean` | no | LegacyHistoryMayExist reports that pre-run-table assistant metadata also exists for this session while the run-keyed table served the page, so this response does not cover the session's full history era. |
| `legacy_source` | `boolean` | no | LegacySource reports that turns were recovered from pre-run-table assistant metadata instead of the run-keyed lifecycle table. |
| `limit` | `integer` | no | Limit is the page bound the turns and aggregates were computed over. |
| `turns` | `array[] of handlers.ContextLifecycleTurn` | no | — |

### `handlers.ContextLifecycleTurn`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `assistant_message_id` | `string` | no | — |
| `created_at` | `string` | no | — |
| `error_code` | `string` | no | — |
| `run_id` | `string` | no | — |
| `snapshot` | `contextfrag.LifecycleSnapshot` | no | — |
| `status` | `string` | no | — |

### `handlers.ContextUsage`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `breakdown` | `array[] of contextfrag.KindBreakdown` | no | — |
| `budget_plan` | `contextfrag.ContextBudgetPlan` | no | — |
| `compaction` | `handlers.CompactionInfo` | no | — |
| `context_window` | `integer` | no | — |
| `tool_defs` | `array[] of handlers.ToolDefBucket` | no | — |
| `used_tokens` | `integer` | no | — |

### `handlers.CreateContainerRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `gpu` | `handlers.ContainerGPURequest` | no | — |
| `image` | `string` | no | — |
| `restore_data` | `boolean` | no | RestoreData imports the preserved /data archive into the new workspace once it is running. Backends that expose snapshot mounts restore the archive while starting regardless of this flag; the flag matters for backends that restore through the bridge. |

### `handlers.CreateContainerResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cdi_devices` | `array[] of string` | no | — |
| `container_id` | `string` | no | — |
| `container_path` | `string` | no | — |
| `data_restored` | `boolean` | no | — |
| `has_preserved_data` | `boolean` | no | — |
| `image` | `string` | no | — |
| `runtime_backend` | `string` | no | — |
| `snapshotter` | `string` | no | — |
| `started` | `boolean` | no | — |
| `workspace_backend` | `string` | no | — |

### `handlers.createSessionRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_runtime_id` | `string` | no | ACPRuntimeID optionally binds a warm pre-session runtime (created via POST /bots/{bot_id}/acp-runtimes) to the new ACP session. It is a transient in-memory handle reference, never persisted in metadata. |
| `bot_agent_id` | `string` | no | — |
| `channel_type` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `preferred_chat_model_id` | `string` | no | PreferredChatModelID / PreferredReasoningEffort carry the first-send picker pair (issue #879 spec v2). The composer sends them only when the pair has an explicit source (user pick or remembered session); omitted fields leave the columns NULL so the session follows the bot default. |
| `preferred_reasoning_effort` | `string` | no | — |
| `runtime_metadata` | `object map of any` | no | — |
| `runtime_type` | `string` | no | — |
| `session_mode` | `string` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |
| `workdir_id` | `string` | no | WorkdirID immutably binds the new session to a bot workdir. The workdir decides the session's workspace target and working directory for its whole life; there is no way to change or clear it later. |

### `handlers.CreateSnapshotRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `snapshot_name` | `string` | no | — |

### `handlers.CreateSnapshotResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `container_id` | `string` | no | — |
| `display_name` | `string` | no | — |
| `runtime_snapshot_name` | `string` | no | — |
| `snapshot_name` | `string` | no | — |
| `snapshotter` | `string` | no | — |
| `source` | `string` | no | — |
| `version` | `integer` | no | — |

### `handlers.DailyTokenUsage`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache_read_tokens` | `integer` | no | — |
| `day` | `string` | no | — |
| `input_tokens` | `integer` | no | — |
| `output_tokens` | `integer` | no | — |
| `reasoning_tokens` | `integer` | no | — |

### `handlers.displayInfoResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `a11y_available` | `boolean` | no | — |
| `available` | `boolean` | no | — |
| `browser_available` | `boolean` | no | — |
| `desktop_available` | `boolean` | no | — |
| `enabled` | `boolean` | no | — |
| `encoder` | `string` | no | — |
| `encoder_available` | `boolean` | no | — |
| `prepare_supported` | `boolean` | no | — |
| `prepare_system` | `string` | no | — |
| `running` | `boolean` | no | — |
| `toolkit_available` | `boolean` | no | — |
| `transport` | `string` | no | — |
| `unavailable_reason` | `string` | no | — |

### `handlers.displaySessionListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of display.SessionInfo` | no | — |

### `handlers.displayWebRTCOfferRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `candidate_host` | `string` | no | — |
| `sdp` | `string` | no | — |
| `session_id` | `string` | no | — |
| `type` | `string` | no | — |

### `handlers.displayWebRTCOfferResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `sdp` | `string` | no | — |
| `session_id` | `string` | no | — |
| `type` | `string` | no | — |

### `handlers.enqueueQueueRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `invocation_id` | `string` | yes | — |
| `text` | `string` | yes | — |

### `handlers.followUpQueueItemResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enqueued_during_run_id` | `string` | no | — |
| `item_id` | `string` | no | — |
| `position` | `integer` | no | — |
| `status` | `sessionruntime.QueueStatus` | no | — |
| `text` | `string` | no | — |

### `handlers.followUpQueueReorderRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `before` | `sessionruntime.FollowUpPendingRef` | no | — |
| `item` | `sessionruntime.FollowUpPendingRef` | no | — |

### `handlers.followUpQueueResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of handlers.followUpQueueItemResponse` | no | — |

### `handlers.forkSessionRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `message_id` | `string/uuid` | no | MessageID is the pre-turn spelling of TurnID, resolved server-side to the round that contains it. Deprecated: send turn_id. A client holds a turn id from admission onward, while a stored message id exists only once the round has been persisted. |
| `title` | `string` | no | — |
| `turn_id` | `string/uuid` | no | — |

### `handlers.FSArchiveRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `paths` | `array[] of string` | no | — |

### `handlers.FSDeleteRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | no | — |
| `recursive` | `boolean` | no | — |

### `handlers.FSExtractRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | no | — |

### `handlers.FSExtractResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `destination` | `string` | no | — |
| `directories` | `integer` | no | — |
| `files` | `integer` | no | — |

### `handlers.FSFileInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `isDir` | `boolean` | no | — |
| `modTime` | `string` | no | — |
| `mode` | `string` | no | — |
| `name` | `string` | no | — |
| `path` | `string` | no | — |
| `size` | `integer` | no | — |

### `handlers.FSListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `entries` | `array[] of handlers.FSFileInfo` | no | — |
| `path` | `string` | no | — |

### `handlers.FSMkdirRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | no | — |
| `workspace_target_id` | `string` | no | WorkspaceTargetID overrides the Bot's Primary target for this request. |

### `handlers.fsOpResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ok` | `boolean` | no | — |
| `revision` | `string` | no | — |

### `handlers.FSReadResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `content` | `string` | no | — |
| `path` | `string` | no | — |
| `revision` | `string` | no | — |
| `size` | `integer` | no | — |

### `handlers.FSRenameRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `newPath` | `string` | no | — |
| `oldPath` | `string` | no | — |

### `handlers.FSUploadResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | no | — |
| `size` | `integer` | no | — |

### `handlers.FSWriteRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `content` | `string` | no | — |
| `expectedRevision` | `string` | no | — |
| `path` | `string` | no | — |

### `handlers.GetContainerMetricsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `backend` | `string` | no | — |
| `metrics` | `handlers.ContainerMetricsPayloadResponse` | no | — |
| `resource_limits` | `handlers.GetContainerResourceLimitsResponse` | no | — |
| `sampled_at` | `string` | no | — |
| `status` | `handlers.ContainerMetricsStatusResponse` | no | — |
| `supported` | `boolean` | no | — |
| `unsupported_reason` | `string` | no | — |

### `handlers.GetContainerResourceLimitsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `applied` | `handlers.ContainerResourceLimitValuesResponse` | no | — |
| `backend` | `string` | no | — |
| `capabilities` | `handlers.ContainerResourceLimitCapabilitiesResponse` | no | — |
| `desired` | `handlers.ContainerResourceLimitValuesResponse` | no | — |
| `observed` | `handlers.ContainerResourceLimitObservedResponse` | no | — |
| `requires_recreate` | `boolean` | no | — |
| `runtime_backend` | `string` | no | — |
| `status` | `string` | no | — |
| `workspace_backend` | `string` | no | — |

### `handlers.GetContainerResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cdi_devices` | `array[] of string` | no | — |
| `container_id` | `string` | no | — |
| `container_path` | `string` | no | — |
| `created_at` | `string` | no | — |
| `has_preserved_data` | `boolean` | no | — |
| `image` | `string` | no | — |
| `legacy` | `boolean` | no | — |
| `namespace` | `string` | no | — |
| `removal` | `handlers.WorkspaceRemovalResponse` | no | — |
| `runtime_backend` | `string` | no | — |
| `status` | `string` | no | — |
| `task_running` | `boolean` | no | — |
| `updated_at` | `string` | no | — |
| `workspace_backend` | `string` | no | — |

### `handlers.graphEdge`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `count` | `integer` | no | — |
| `rel` | `string` | no | — |
| `rels` | `array[] of string` | no | — |
| `source` | `string` | no | — |
| `target` | `string` | no | — |
| `weight` | `number` | no | — |

### `handlers.graphNode`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `count` | `integer` | no | — |
| `id` | `string` | no | — |
| `label` | `string` | no | — |
| `memory` | `string` | no | — |
| `memory_ids` | `array[] of string` | no | — |
| `metadata` | `object map of any` | no | — |
| `slug` | `string` | no | — |
| `subject` | `string` | no | — |
| `topic` | `string` | no | — |

### `handlers.graphResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `edges` | `array[] of handlers.graphEdge` | no | — |
| `nodes` | `array[] of handlers.graphNode` | no | — |

### `handlers.HookEventInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | `string` | no | — |
| `runtime_supported` | `boolean` | no | — |

### `handlers.HooksEventsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `actions` | `array[] of string` | no | — |
| `config_path` | `string` | no | — |
| `decisions` | `array[] of string` | no | — |
| `events` | `array[] of handlers.HookEventInfo` | no | — |

### `handlers.HookTestRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `approval` | `object map of any` | no | — |
| `channel` | `object map of any` | no | — |
| `chat_id` | `string` | no | — |
| `error` | `string` | no | — |
| `event` | `string` | no | — |
| `extra` | `object map of any` | no | — |
| `memory` | `object map of any` | no | — |
| `session_id` | `string` | no | — |
| `tool` | `hooks.ToolPayload` | no | — |
| `turn` | `object map of any` | no | — |

### `handlers.HookTestResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_exists` | `boolean` | no | — |
| `result` | `hooks.Result` | no | — |

### `handlers.listSessionsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of session.Session` | no | — |
| `next_cursor` | `string` | no | — |

### `handlers.ListSnapshotsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `snapshots` | `array[] of handlers.SnapshotInfo` | no | — |
| `snapshotter` | `string` | no | — |

### `handlers.LocalChannelMessageRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `message` | `channel.Message` | yes | — |
| `model_id` | `string` | no | — |
| `reasoning_effort` | `string` | no | — |
| `workspace_target_id` | `string` | no | — |

### `handlers.LoginRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `password` | `string` | no | — |
| `username` | `string` | no | — |

### `handlers.LoginResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `access_token` | `string` | no | — |
| `display_name` | `string` | no | — |
| `expires_at` | `string` | no | — |
| `role` | `string` | no | — |
| `timezone` | `string` | no | — |
| `token_type` | `string` | no | — |
| `user_id` | `string` | no | — |
| `username` | `string` | no | — |

### `handlers.MCPStdioRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `args` | `array[] of string` | no | — |
| `command` | `string` | no | — |
| `cwd` | `string` | no | — |
| `env` | `object map of string` | no | — |
| `name` | `string` | no | — |

### `handlers.MCPStdioResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `connection_id` | `string` | no | — |
| `tools` | `array[] of string` | no | — |
| `url` | `string` | no | — |

### `handlers.memoryAddPayload`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `embedding_enabled` | `boolean` | no | — |
| `filters` | `object map of any` | no | — |
| `infer` | `boolean` | no | — |
| `message` | `string` | no | — |
| `messages` | `array[] of adapters.Message` | no | — |
| `metadata` | `object map of any` | no | — |
| `namespace` | `string` | no | — |
| `run_id` | `string` | no | — |

### `handlers.memoryCompactPayload`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `decay_days` | `integer` | no | — |
| `ratio` | `number` | no | — |

### `handlers.memoryDeletePayload`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `memory_ids` | `array[] of string` | no | — |

### `handlers.memorySearchPayload`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `embedding_enabled` | `boolean` | no | — |
| `filters` | `object map of any` | no | — |
| `limit` | `integer` | no | — |
| `no_stats` | `boolean` | no | — |
| `query` | `string` | no | — |
| `run_id` | `string` | no | — |
| `sources` | `array[] of string` | no | — |

### `handlers.memoryUpdatePayload`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `memory` | `string` | no | — |

### `handlers.modelPreferenceSeedResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `model_id` | `string` | no | — |
| `reasoning_effort` | `string` | no | — |

### `handlers.ModelTokenUsage`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `input_tokens` | `integer` | no | — |
| `model_id` | `string` | no | — |
| `model_name` | `string` | no | — |
| `model_slug` | `string` | no | — |
| `output_tokens` | `integer` | no | — |
| `provider_name` | `string` | no | — |

### `handlers.oauthAuthorizeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `callback_url` | `string` | no | — |
| `client_id` | `string` | no | — |
| `client_secret` | `string` | no | — |

### `handlers.oauthDiscoverRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `url` | `string` | no | — |

### `handlers.oauthExchangeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | `string` | no | — |
| `state` | `string` | no | — |

### `handlers.PingResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `commit_hash` | `string` | no | — |
| `connectors` | `boolean` | no | — |
| `container_backend` | `string` | no | — |
| `snapshot_supported` | `boolean` | no | — |
| `status` | `string` | no | — |
| `version` | `string` | no | — |

### `handlers.ProbeResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_required` | `boolean` | no | — |
| `error` | `string` | no | — |
| `status` | `string` | no | — |
| `tools` | `array[] of mcp.ToolDescriptor` | no | — |

### `handlers.QuickActionExecuteRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action_id` | `string` | no | — |
| `composer_scope` | `string` | no | — |
| `invocation_id` | `string` | no | — |
| `params` | `object map of any` | no | — |
| `session_id` | `string` | no | — |

### `handlers.RefreshResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `access_token` | `string` | no | — |
| `expires_at` | `string` | no | — |
| `token_type` | `string` | no | — |

### `handlers.RollbackRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `version` | `integer` | no | — |

### `handlers.RuntimeCommandRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `command` | `string` | no | — |

### `handlers.RuntimeCommandResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | `any` | no | — |
| `notice` | `string` | no | — |
| `text` | `string` | no | — |

### `handlers.RuntimeGoalRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string enum(pause, clear)` | no | — |

### `handlers.RuntimeGoalResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `goal` | `external.Goal` | no | — |

### `handlers.RuntimeModeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `mode_id` | `string` | no | — |
| `mode_kind` | `string enum(permission, plan)` | no | Omitted means permission; plan changes the independent planning mode. |

### `handlers.SafeSkillsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `skills` | `array[] of skills.SafeCatalogItem` | no | — |

### `handlers.SessionInfoResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache_stats` | `handlers.CacheStats` | no | — |
| `context_usage` | `handlers.ContextUsage` | no | — |
| `message_count` | `integer` | no | — |
| `skills` | `array[] of string` | no | — |

### `handlers.sessionInvocationResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `found` | `boolean` | no | Found is false when the session has no run for this invocation, which is an ordinary answer (never admitted, or not yet) rather than an error. |
| `invocation_id` | `string` | no | — |
| `run_id` | `string` | no | — |
| `session_id` | `string` | no | — |
| `state` | `string` | no | State is the durable run state as the ledger records it, e.g. accepted, running, waiting_decision, finishing, completed, aborted, failed or lost. |
| `turn_id` | `string` | no | — |
| `turn_position` | `integer` | no | — |

### `handlers.sessionQueueResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `follow_up` | `array[] of handlers.followUpQueueItemResponse` | no | — |
| `steer` | `array[] of handlers.steerQueueItemResponse` | no | — |
| `steer_supported` | `boolean` | no | — |

### `handlers.SiteIconResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dark` | `string` | no | — |
| `dark_mask` | `string` | no | — |
| `light` | `string` | no | — |
| `light_mask` | `string` | no | — |

### `handlers.SkillItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | no | — |
| `content` | `string` | no | — |
| `deletable` | `boolean` | no | — |
| `description` | `string` | no | — |
| `editable` | `boolean` | no | — |
| `managed` | `boolean` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `raw` | `string` | no | — |
| `registry_id` | `string` | no | — |
| `shadowed_by` | `string` | no | — |
| `skill_id` | `string` | no | — |
| `source_kind` | `string` | no | — |
| `source_path` | `string` | no | — |
| `source_root` | `string` | no | — |
| `state` | `string` | no | — |

### `handlers.SkillsActionRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string` | no | — |
| `target_path` | `string` | no | — |

### `handlers.SkillsDeleteRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `source_paths` | `array[] of string` | no | SourcePaths are SKILL.md paths reported in the skill list. Deleting by name cannot address registry skills, which are nested by registry and app. |

### `handlers.skillsOpResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ok` | `boolean` | no | — |

### `handlers.SkillsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `skills` | `array[] of handlers.SkillItem` | no | — |

### `handlers.SkillsUpsertRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `skills` | `array[] of string` | no | — |
| `source_path` | `string` | no | SourcePath is the existing SKILL.md being edited when saving a single skill. Empty means create (or overwrite by frontmatter name under /data/skills/<name>/). |

### `handlers.SnapshotInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `created_at` | `string` | no | — |
| `display_name` | `string` | no | — |
| `kind` | `string` | no | — |
| `labels` | `object map of string` | no | — |
| `managed` | `boolean` | no | — |
| `name` | `string` | no | — |
| `parent` | `string` | no | — |
| `runtime_snapshot_name` | `string` | no | — |
| `snapshotter` | `string` | no | — |
| `source` | `string` | no | — |
| `updated_at` | `string` | no | — |
| `version` | `integer` | no | — |

### `handlers.StarterPrompt`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `prompt` | `string` | yes | — |
| `title` | `string` | yes | — |

### `handlers.StarterPromptsRefreshRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avoid` | `array[] of string` | no | Avoid lists the prompts currently shown, which the new set must not repeat. |
| `locale` | `string` | no | Locale is the BCP 47 language tag the suggestions are written in. Defaults to en. |

### `handlers.StarterPromptsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `hash` | `string` | yes | — |
| `items` | `array[] of handlers.StarterPrompt` | yes | — |
| `unchanged` | `boolean` | yes | — |

### `handlers.steerQueueItemResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `item_id` | `string` | no | — |
| `position` | `integer` | no | — |
| `status` | `sessionruntime.QueueStatus` | no | — |
| `target_run_id` | `string` | no | — |
| `text` | `string` | no | — |

### `handlers.steerQueueReorderRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `before` | `sessionruntime.SteerPendingRef` | no | — |
| `item` | `sessionruntime.SteerPendingRef` | no | — |

### `handlers.steerQueueResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of handlers.steerQueueItemResponse` | no | — |

### `handlers.SupermarketAppCategory`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_count` | `integer` | yes | — |
| `id` | `string` | yes | — |
| `name` | `string` | yes | — |
| `names` | `object map of string` | yes | — |
| `order` | `integer` | yes | — |
| `registries` | `array[] of handlers.SupermarketAppCategoryRegistry` | yes | — |

### `handlers.SupermarketAppCategoryListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | `array[] of handlers.SupermarketAppCategory` | yes | — |

### `handlers.SupermarketAppCategoryRegistry`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `count` | `integer` | yes | — |
| `id` | `string` | yes | — |

### `handlers.SupermarketAppConnector`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `required` | `boolean` | yes | — |
| `type` | `string` | yes | — |

### `handlers.SupermarketAppDescriptor`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | yes | — |
| `author` | `handlers.SupermarketAuthor` | no | — |
| `categories` | `array[] of handlers.SupermarketAppSkillCategory` | yes | — |
| `category` | `string` | yes | — |
| `category_name` | `string` | yes | — |
| `connector_count` | `integer` | yes | — |
| `connectors` | `array[] of handlers.SupermarketAppConnector` | yes | — |
| `dependencies` | `array[] of string` | yes | — |
| `dependency_count` | `integer` | yes | — |
| `description` | `string` | yes | — |
| `homepage` | `string` | no | — |
| `icon` | `handlers.SupermarketSkillIcon` | no | — |
| `license` | `string` | no | — |
| `name` | `string` | yes | — |
| `registry_id` | `string` | yes | — |
| `repository` | `string` | no | — |
| `revision` | `string` | yes | — |
| `schema_version` | `string` | yes | — |
| `skill_count` | `integer` | yes | — |
| `skills` | `array[] of handlers.SupermarketCatalogSkill` | yes | — |
| `tags` | `array[] of string` | yes | — |
| `translations` | `object map of handlers.SupermarketAppTranslation` | no | — |
| `version` | `string` | no | — |

### `handlers.SupermarketAppListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | `array[] of handlers.SupermarketAppSummary` | yes | — |
| `limit` | `integer` | yes | — |
| `page` | `integer` | yes | — |
| `total` | `integer` | yes | — |

### `handlers.SupermarketAppSkillCategory`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | `string` | yes | — |
| `name` | `string` | yes | — |
| `skill_count` | `integer` | yes | — |

### `handlers.SupermarketAppSummary`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | yes | — |
| `author` | `handlers.SupermarketAuthor` | no | — |
| `categories` | `array[] of handlers.SupermarketAppSkillCategory` | yes | — |
| `category` | `string` | yes | — |
| `category_name` | `string` | yes | — |
| `connector_count` | `integer` | yes | — |
| `connectors` | `array[] of handlers.SupermarketAppConnector` | yes | — |
| `dependencies` | `array[] of string` | yes | — |
| `dependency_count` | `integer` | yes | — |
| `description` | `string` | yes | — |
| `homepage` | `string` | no | — |
| `icon` | `handlers.SupermarketSkillIcon` | no | — |
| `license` | `string` | no | — |
| `name` | `string` | yes | — |
| `registry_id` | `string` | yes | — |
| `repository` | `string` | no | — |
| `schema_version` | `string` | yes | — |
| `skill_count` | `integer` | yes | — |
| `tags` | `array[] of string` | yes | — |
| `translations` | `object map of handlers.SupermarketAppTranslation` | no | — |
| `version` | `string` | no | — |

### `handlers.SupermarketAppTranslation`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `name` | `string` | no | — |

### `handlers.SupermarketAuthor`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `email` | `string` | yes | — |
| `name` | `string` | yes | — |

### `handlers.SupermarketCatalogSkill`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `app_id` | `string` | yes | — |
| `artifact` | `handlers.SupermarketSkillArtifact` | yes | — |
| `author` | `handlers.SupermarketAuthor` | yes | — |
| `category` | `string` | yes | — |
| `category_name` | `string` | yes | — |
| `description` | `string` | yes | — |
| `files` | `array[] of string` | yes | — |
| `homepage` | `string` | no | — |
| `icon` | `handlers.SupermarketSkillIcon` | no | — |
| `install_id` | `string` | yes | — |
| `name` | `string` | yes | — |
| `registry_id` | `string` | yes | — |
| `schema_version` | `string` | yes | — |
| `skill_id` | `string` | yes | — |
| `source` | `handlers.SupermarketSkillSource` | yes | — |
| `source_category` | `string` | no | — |
| `tags` | `array[] of string` | yes | — |

### `handlers.SupermarketCatalogSkillListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | `array[] of handlers.SupermarketCatalogSkill` | yes | — |
| `limit` | `integer` | yes | — |
| `page` | `integer` | yes | — |
| `total` | `integer` | yes | — |

### `handlers.SupermarketRegistry`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `adapter` | `string` | yes | — |
| `app_count` | `integer` | yes | — |
| `category_count` | `integer` | yes | — |
| `enabled` | `boolean` | yes | — |
| `id` | `string` | yes | — |
| `name` | `string` | yes | — |
| `priority` | `integer` | yes | — |
| `published_at` | `string` | no | — |
| `revision` | `string` | no | — |
| `skill_count` | `integer` | yes | — |
| `skipped_app_count` | `integer` | yes | — |

### `handlers.SupermarketRegistryListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `data` | `array[] of handlers.SupermarketRegistry` | yes | — |

### `handlers.SupermarketSkillArtifact`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `archive_size` | `integer` | yes | — |
| `content_type` | `string` | yes | — |
| `digest` | `string` | yes | — |
| `download_url` | `string` | yes | — |
| `file_count` | `integer` | yes | — |
| `format` | `string` | yes | — |
| `size` | `integer` | yes | — |
| `uncompressed_size` | `integer` | yes | — |

### `handlers.SupermarketSkillIcon`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `brand_color` | `string` | no | — |
| `card` | `handlers.SupermarketSkillIconAsset` | no | — |
| `dark` | `handlers.SupermarketSkillIconAsset` | no | — |
| `detail` | `handlers.SupermarketSkillIconAsset` | no | — |

### `handlers.SupermarketSkillIconAsset`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `content_type` | `string` | yes | — |
| `digest` | `string` | yes | — |
| `size` | `integer` | yes | — |

### `handlers.SupermarketSkillSource`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `path` | `string` | yes | — |
| `repository` | `string` | no | — |
| `revision` | `string` | yes | — |
| `type` | `string` | yes | — |

### `handlers.synthesizeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `text` | `string` | no | — |

### `handlers.synthesizeResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `content_type` | `string` | no | — |
| `size` | `integer` | no | — |
| `temp_id` | `string` | no | — |

### `handlers.terminalInfoResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `available` | `boolean` | no | — |
| `shell` | `string` | no | — |

### `handlers.TokenUsageRecord`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache_read_tokens` | `integer` | no | — |
| `created_at` | `string` | no | — |
| `harness` | `string` | no | — |
| `id` | `string` | no | — |
| `input_tokens` | `integer` | no | — |
| `model_id` | `string` | no | — |
| `model_name` | `string` | no | — |
| `model_slug` | `string` | no | — |
| `output_tokens` | `integer` | no | — |
| `provider_name` | `string` | no | — |
| `reasoning_tokens` | `integer` | no | — |
| `session_id` | `string` | no | — |
| `session_type` | `string` | no | — |

### `handlers.TokenUsageRecordsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of handlers.TokenUsageRecord` | no | — |
| `total` | `integer` | no | — |

### `handlers.TokenUsageResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_agent` | `array[] of handlers.DailyTokenUsage` | no | — |
| `by_model` | `array[] of handlers.ModelTokenUsage` | no | — |
| `chat` | `array[] of handlers.DailyTokenUsage` | no | — |
| `discuss` | `array[] of handlers.DailyTokenUsage` | no | — |
| `memory` | `array[] of handlers.DailyTokenUsage` | no | Memory is usage of the memory LLM (extract / decide / compact), which runs outside any session. |
| `schedule` | `array[] of handlers.DailyTokenUsage` | no | — |

### `handlers.ToolApprovalDecisionRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `control_id` | `string` | no | ControlID is the stable identity of one client mutation. New clients send it for exact retries; it remains optional for older Web/Desktop clients. |
| `option_id` | `string` | no | OptionID selects one of the agent-provided permission options carried on the approval request; empty keeps the plain binary decision. |
| `reason` | `string` | no | — |

### `handlers.ToolDefBucket`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `provider` | `string` | no | — |
| `token_estimate` | `integer` | no | — |
| `tools` | `integer` | no | — |

### `handlers.TriggerCompactResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `message_count` | `integer` | no | — |
| `status` | `string` | no | — |
| `summary` | `string` | no | — |

### `handlers.UILocateMessageResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of conversation.UITurn` | yes | — |
| `target_external_message_id` | `string` | yes | — |
| `target_id` | `string/uuid` | yes | — |

### `handlers.UIMessageListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of conversation.UITurn` | yes | — |

### `handlers.UpdateContainerMetricsRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `resource_limits` | `handlers.UpdateContainerResourceLimitsRequest` | no | — |

### `handlers.UpdateContainerResourceLimitsRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cpu_millicores` | `integer` | no | — |
| `memory_bytes` | `integer` | no | — |
| `storage_bytes` | `integer` | no | — |

### `handlers.updateQueueRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `text` | `string` | yes | — |

### `handlers.updateSessionRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_agent_id` | `string` | no | — |
| `expected_model_preference_revision` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `preferred_chat_model_id` | `string` | no | PreferredChatModelID / PreferredReasoningEffort are the picker pair (issue #879). The composer always patches the pair together; either one alone is reconciled against the model the session would actually use. Changing either requires ExpectedModelPreferenceRevision ("" when the session has none yet): picker writes are always compare-and-set. |
| `preferred_reasoning_effort` | `string` | no | — |
| `runtime_metadata` | `object map of any` | no | — |
| `runtime_type` | `string` | no | — |
| `session_mode` | `string` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |

### `handlers.WorkspaceDependencyCatalogItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `icon_url` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `requires` | `array[] of string` | no | Requires lists the dependency IDs installed first when missing. |
| `translations` | `object map of handlers.WorkspaceDependencyTranslation` | no | — |

### `handlers.WorkspaceDependencyCatalogResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `catalog_stale` | `boolean` | no | — |
| `items` | `array[] of handlers.WorkspaceDependencyCatalogItem` | no | — |

### `handlers.WorkspaceDependencyInstallRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `definition_revision` | `string` | no | — |
| `prerequisite_revisions` | `object map of string` | no | PrerequisiteRevisions are the definition revisions the confirmation showed for the dependency's prerequisites, keyed by dependency id. When present, a missing prerequisite installs only from its confirmed revision; one without an entry refuses the operation with workspace_dependency.prerequisites_changed. Omitted, prerequisites resolve when the operation starts, like an omitted definition_revision. |
| `session_id` | `string` | no | SessionID optionally routes operation progress to its originating conversation. |
| `version` | `string` | no | Version to install. Empty (or no body) installs the latest version the catalog script resolves, or the manifest pin when the dependency has one. The version recorded afterwards is the one the script reports. |

### `handlers.WorkspaceDependencyItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `actions` | `array[] of string enum(install, update, reinstall, remove, rollback, check_update)` | no | Actions lists what may be requested right now. |
| `category` | `string enum(agent, runtime, tool)` | no | Category is agent, runtime, or tool. |
| `definition_revision` | `string` | no | — |
| `description` | `string` | no | — |
| `icon` | `string` | no | — |
| `icon_url` | `string` | no | — |
| `id` | `string` | no | — |
| `image_version` | `string` | no | ImageVersion is the version of the workspace's toolkit copy, omitted when no toolkit copy remains. Native removal clears it as well. |
| `install_path` | `string` | no | InstallPath is the dependency home when a managed copy is in effect or can be installed, and the discovered command path when the image copy is in effect. |
| `installed_version` | `string` | no | InstalledVersion is the version of the copy in effect: the one the runtime launches and the one first on PATH (managed, then image, then PATH). |
| `last_checked_at` | `string` | no | — |
| `last_error` | `string` | no | LastError is text recorded by earlier servers, including the script output they used to keep. A failure recorded now carries LastErrorCode. |
| `last_error_code` | `string` | no | LastErrorCode is the catalog code of a recorded failure, which clients render as errors.<code>. |
| `latest_version` | `string` | no | LatestVersion is the last upstream check result, omitted until a check ran. |
| `name` | `string` | no | — |
| `overlay` | `boolean` | no | Overlay is set when the copy in effect is a managed one installed over an image copy. |
| `platform_reason` | `string enum(unsupported_platform)` | no | — |
| `platform_supported` | `boolean` | no | PlatformSupported is false when the probed workspace platform is not listed by the catalog manifest; PlatformReason then says why. |
| `previous_version` | `string` | no | PreviousVersion is the version rollback would switch back to. |
| `provides` | `array[] of string` | no | Provides lists the commands the dependency makes available. |
| `registry_id` | `string` | no | — |
| `requires` | `array[] of string` | no | Requires lists the dependency IDs installed first when missing. |
| `retired` | `boolean` | no | — |
| `source` | `string enum(image, managed)` | no | Source is image for dependencies shipped with the workspace image and managed for dependencies installed by catalog scripts. |
| `status` | `string enum(installed, installing, updating, removing, missing, failed)` | no | Status is omitted when the dependency has no record and was not found in the workspace. |
| `translations` | `object map of handlers.WorkspaceDependencyTranslation` | no | — |
| `update_available` | `boolean` | no | UpdateAvailable is set for installed dependencies whose last upstream check reported a version other than the one in effect. |

### `handlers.WorkspaceDependencyListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `catalog_fetched_at` | `string` | no | — |
| `catalog_refreshing` | `boolean` | no | CatalogRefreshing is set while the catalog is refreshed in the background; listing again shortly may show a newer catalog. |
| `catalog_stale` | `boolean` | no | — |
| `discovery_error` | `string` | no | DiscoveryError is set when the workspace is running but could not be inspected (the discovery command was killed or timed out). Items then reflect the installation records alone, without workspace facts or actions; a refresh retries discovery. |
| `items` | `array[] of handlers.WorkspaceDependencyItem` | no | — |
| `platform` | `handlers.WorkspaceDependencyPlatform` | no | — |
| `update_checks_incomplete` | `boolean` | no | UpdateChecksIncomplete is set by check-updates when some checks did not finish in time; those keep their last result. |
| `workspace_state` | `string enum(running, not_running, missing)` | no | — |

### `handlers.WorkspaceDependencyOperationResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string` | no | — |
| `definition_revision` | `string` | no | — |
| `dependency_id` | `string` | no | — |
| `entrypoints` | `object map of string` | no | — |
| `status` | `string` | no | — |
| `version` | `string` | no | — |

### `handlers.WorkspaceDependencyPlatform`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `arch` | `string` | no | — |
| `libc` | `string` | no | — |
| `os` | `string` | no | — |

### `handlers.WorkspaceDependencyPreflightItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dependency_id` | `string` | no | — |
| `installed_version` | `string` | no | — |
| `name` | `string` | no | — |
| `state` | `string enum(satisfied, missing, platform_unsupported, unknown_dependency)` | no | — |

### `handlers.WorkspaceDependencyPreflightRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dependency_ids` | `array[] of string` | no | — |

### `handlers.WorkspaceDependencyPreflightResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of handlers.WorkspaceDependencyPreflightItem` | no | — |
| `workspace_state` | `string enum(running, not_running, missing)` | no | — |

### `handlers.WorkspaceDependencyScriptEnv`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `key` | `string` | no | — |
| `secret` | `boolean` | no | — |
| `value` | `string` | no | Value is empty when Secret is set. |

### `handlers.WorkspaceDependencyScriptResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action` | `string enum(install, update, remove, reinstall, rollback)` | no | — |
| `definition_revision` | `string` | no | — |
| `dependency_id` | `string` | no | — |
| `digest` | `string` | no | — |
| `env` | `array[] of handlers.WorkspaceDependencyScriptEnv` | no | — |
| `exec` | `string` | no | — |
| `script` | `string` | no | — |
| `timeout_seconds` | `integer` | no | — |

### `handlers.WorkspaceDependencyStreamEvent`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `args` | `object map of string` | no | — |
| `code` | `string` | no | — |
| `data` | `string` | no | — |
| `definition_revision` | `string` | no | — |
| `dependency_id` | `string` | no | — |
| `detail` | `string` | no | — |
| `entrypoints` | `object map of string` | no | — |
| `fault` | `apperror.Fault` | no | — |
| `message` | `string` | no | — |
| `request_id` | `string` | no | — |
| `stream` | `string enum(stdout, stderr)` | no | — |
| `type` | `string enum(started, log, done, error)` | no | — |
| `version` | `string` | no | — |

### `handlers.WorkspaceDependencyTranslation`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `name` | `string` | no | — |

### `handlers.WorkspaceRemovalResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `preserve_data` | `boolean` | no | — |
| `state` | `string enum(removing, failed)` | no | — |
| `updated_at` | `string` | no | — |

### `hooks.ActionResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action_type` | `string` | no | — |
| `append_system_sections` | `array[] of hooks.SystemSectionOutput` | no | — |
| `decision` | `string` | no | — |
| `error` | `string` | no | — |
| `exit_code` | `integer` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `reason` | `string` | no | — |
| `result` | `any` | no | — |
| `stderr` | `string` | no | — |
| `stdout` | `string` | no | — |
| `warnings` | `array[] of hooks.OutputWarning` | no | — |

### `hooks.OutputWarning`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | `string` | no | — |
| `hook_name` | `string` | no | — |
| `message` | `string` | no | — |
| `section_id` | `string` | no | — |

### `hooks.Result`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `action_results` | `array[] of hooks.ActionResult` | no | — |
| `actions_run` | `integer` | no | — |
| `append_context` | `string` | no | — |
| `append_system_sections` | `array[] of hooks.SystemSectionOutput` | no | — |
| `decision` | `string` | no | — |
| `hooks_matched` | `integer` | no | — |
| `metadata` | `object map of any` | no | — |
| `reason` | `string` | no | — |
| `runtime_supported` | `boolean` | no | — |
| `warnings` | `array[] of hooks.OutputWarning` | no | — |

### `hooks.SystemSectionCache`

Schema: `string enum(dynamic, stable)`

### `hooks.SystemSectionOutput`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cache` | `hooks.SystemSectionCache` | no | — |
| `hook_name` | `string` | no | — |
| `id` | `string` | no | — |
| `retention` | `hooks.SystemSectionRetention` | no | — |
| `text` | `string` | no | — |

### `hooks.SystemSectionRetention`

Schema: `string enum(optional, preferred)`

### `hooks.ToolPayload`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `call_id` | `string` | no | — |
| `error` | `string` | no | — |
| `input` | `any` | no | — |
| `name` | `string` | no | — |
| `result` | `any` | no | — |

### `mcp.AuthorizeResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `authorization_url` | `string` | no | — |

### `mcp.DiscoveryResult`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `authorization_endpoint` | `string` | no | — |
| `authorization_server_url` | `string` | no | — |
| `registration_endpoint` | `string` | no | — |
| `resource_metadata_url` | `string` | no | — |
| `resource_uri` | `string` | no | — |
| `scopes_supported` | `array[] of string` | no | — |
| `token_endpoint` | `string` | no | — |

### `mcp.ExportResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `mcpServers` | `object map of mcp.MCPServerEntry` | no | — |

### `mcp.ImportRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `mcpServers` | `object map of mcp.MCPServerEntry` | no | — |

### `mcp.ListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of github_com_felinics_memoh_internal_mcp.Connection` | no | — |

### `mcp.MCPServerEntry`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `args` | `array[] of string` | no | — |
| `command` | `string` | no | — |
| `cwd` | `string` | no | — |
| `env` | `object map of string` | no | — |
| `headers` | `object map of string` | no | — |
| `transport` | `string` | no | — |
| `url` | `string` | no | — |

### `mcp.OAuthStatus`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_server` | `string` | no | — |
| `callback_url` | `string` | no | — |
| `configured` | `boolean` | no | — |
| `expired` | `boolean` | no | — |
| `expires_at` | `string` | no | — |
| `has_token` | `boolean` | no | — |
| `scopes` | `string` | no | — |

### `mcp.ToolDescriptor`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `inputSchema` | `object map of any` | no | — |
| `name` | `string` | no | — |

### `mcp.UpsertRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `args` | `array[] of string` | no | — |
| `auth_type` | `string` | no | — |
| `command` | `string` | no | — |
| `cwd` | `string` | no | — |
| `env` | `object map of string` | no | — |
| `headers` | `object map of string` | no | — |
| `is_active` | `boolean` | no | — |
| `name` | `string` | no | — |
| `transport` | `string` | no | — |
| `url` | `string` | no | — |

### `models.AddRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `models.ModelConfig` | no | — |
| `enable` | `boolean` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `type` | `models.ModelType` | no | — |

### `models.AddResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | `string` | no | — |
| `model_id` | `string` | no | — |

### `models.CountResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `count` | `integer` | no | — |

### `models.GetResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `models.ModelConfig` | no | — |
| `enable` | `boolean` | no | — |
| `id` | `string` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `reasoning` | `any & reasoning.Options` | no | Reasoning is the model's resolved thinking options, filled by the API layer (it depends on the provider's client type). Clients render this rather than deriving their own answer from ThinkingMode and ReasoningEfforts — the duplication that let the web picker and the wire disagree. |
| `type` | `models.ModelType` | no | — |

### `models.ModelConfig`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `catalog_available` | `boolean` | no | — |
| `compatibilities` | `array[] of string` | no | — |
| `context_window` | `integer` | no | — |
| `description` | `string` | no | — |
| `dimensions` | `integer` | no | — |
| `max_output_tokens` | `integer` | no | MaxOutputTokens is the most output tokens the model can produce in one response; nil means unknown. When set, every turn requests an explicit limit within it. |
| `reasoning_default_on` | `boolean` | no | ReasoningDefaultOn reports whether omitting the thinking field leaves the model thinking. Separate from off-ability: Claude 4.6 can be turned off *and* defaults to off, while Opus 5 can be turned off but defaults to on, so omitting the field there keeps thinking running — billed, and invisible to a user who believes they turned it off. nil means unknown. |
| `reasoning_dialect` | `string` | no | ReasoningDialect declares the wire shape of this model's thinking control, which cannot be inferred from the tiers it advertises: Gemini 2.5 takes a token budget while 3.x takes a named level, and the two are mutually exclusive on the same request. Declared per model because the alternative is sniffing the model id, and an id is not a capability. Empty leaves provider policy in charge; Google's adaptor deliberately sends no thinking control so pre-dialect rows retain their safe pre-upgrade request shape. |
| `reasoning_efforts` | `array[] of string` | no | — |
| `reasoning_off_support` | `string` | no | ReasoningOffSupport declares how the model answers an explicit request to stop thinking. Anthropic's per-model table splits models that share a thinking mode and an identical tier list, so this cannot be derived — see the reasoning package's OffSupport constants. |
| `thinking_budget_max` | `integer` | no | — |
| `thinking_budget_min` | `integer` | no | ThinkingBudgetMin/Max bound the budget dialect. The range is per model family, not per vendor: Gemini 2.5 Pro is 128..32768 and cannot be turned off, while Flash starts at 0 and can. |
| `thinking_mode` | `string` | no | — |

### `models.ModelType`

Schema: `string enum(chat, embedding, speech, transcription, video)`

### `models.TestResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | `string` | no | Code is the error catalog code for why the test did not pass. It is empty on success and when no catalog code describes the failure. |
| `latency_ms` | `integer` | no | — |
| `reachable` | `boolean` | no | — |
| `status` | `models.TestStatus` | no | — |

### `models.TestStatus`

Schema: `string enum(ok, auth_error, model_not_supported, error)`

### `models.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `models.ModelConfig` | no | — |
| `enable` | `boolean` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `type` | `models.ModelType` | no | — |

### `providers.CountResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `count` | `integer` | no | — |

### `providers.CreateFromTemplateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `domain` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `template_id` | `string` | yes | — |

### `providers.CreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `client_type` | `string` | yes | — |
| `config` | `object map of any` | no | — |
| `icon` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | yes | — |

### `providers.GetResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `client_type` | `string` | no | — |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `enable` | `boolean` | no | — |
| `icon` | `string` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `provider_template_id` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `providers.ImportModelsRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `default_compatibilities` | `array[] of string` | no | — |

### `providers.ImportModelsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `created` | `integer` | no | — |
| `models` | `array[] of string` | no | — |
| `skipped` | `integer` | no | — |
| `updated` | `integer` | no | — |

### `providers.OAuthAccount`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `avatar_url` | `string` | no | — |
| `email` | `string` | no | — |
| `label` | `string` | no | — |
| `login` | `string` | no | — |
| `name` | `string` | no | — |
| `profile_url` | `string` | no | — |

### `providers.OAuthAuthorizeResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `auth_url` | `string` | no | — |
| `device` | `providers.OAuthDeviceStatus` | no | — |
| `mode` | `string` | no | — |

### `providers.OAuthDeviceStatus`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `expires_at` | `string` | no | — |
| `interval_seconds` | `integer` | no | — |
| `pending` | `boolean` | no | — |
| `user_code` | `string` | no | — |
| `verification_uri` | `string` | no | — |

### `providers.OAuthStatus`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `account` | `providers.OAuthAccount` | no | — |
| `callback_url` | `string` | no | — |
| `configured` | `boolean` | no | — |
| `device` | `providers.OAuthDeviceStatus` | no | — |
| `expired` | `boolean` | no | — |
| `expires_at` | `string` | no | — |
| `has_token` | `boolean` | no | — |
| `mode` | `string` | no | — |

### `providers.TestResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | `string` | no | Code is the error catalog code for why the test did not pass. It is empty on success and when no catalog code describes the failure. |
| `latency_ms` | `integer` | no | — |
| `reachable` | `boolean` | no | — |
| `status` | `providers.TestStatus` | no | — |

### `providers.TestStatus`

Schema: `string enum(ok, auth_error, error, unverified)`

### `providers.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `client_type` | `string` | no | — |
| `config` | `object map of any` | no | — |
| `enable` | `boolean` | no | — |
| `icon` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `name` | `string` | no | — |

### `providertemplates.GetResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `object map of any` | no | — |
| `configured` | `boolean` | no | — |
| `created_at` | `string` | no | — |
| `default_config` | `object map of any` | no | — |
| `description` | `string` | no | — |
| `domain` | `string` | no | — |
| `driver` | `string` | no | — |
| `icon` | `string` | no | — |
| `id` | `string` | no | — |
| `key` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `models` | `array[] of providertemplates.ModelResponse` | no | — |
| `name` | `string` | no | — |
| `sort_order` | `integer` | no | — |
| `source` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `providertemplates.ModelResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `sort_order` | `integer` | no | — |
| `type` | `string` | no | — |

### `reasoning.Options`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `can_disable` | `boolean` | no | CanDisable reports whether picking "off" actually reaches the model. |
| `default_effort` | `string` | no | DefaultEffort is the tier to use when nothing is stored, and the tier to fall back to when a stored value is no longer offered by the model. |
| `efforts` | `array[] of string` | no | Efforts are the selectable active tiers, weakest to strongest as advertised. |
| `efforts_without_off` | `array[] of string` | no | EffortsWithoutOff are tiers that cannot be combined with off on this model. Opus 5 accepts an explicit disable only at effort high or below, so a client that lets a user hold both must know which tiers conflict. |
| `supported` | `boolean` | no | Supported is false when the model has no thinking concept; the other fields are then empty and no control should be rendered at all. |

### `schedule.CreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_agent_id` | `string` | no | ACPAgentID names the ACP agent when RuntimeType is acp_agent. |
| `acp_model_id` | `string` | no | ACPModelID is an agent-reported model identifier override for External Agent runs. Mutually exclusive with ModelID. |
| `bot_agent_id` | `string` | no | BotAgentID selects one persisted BotAgent for a new session. Empty means the built-in Native runtime (or the legacy ACP fields below). |
| `command` | `string` | no | — |
| `description` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `max_calls` | `schedule.NullableInt` | no | — |
| `max_run_seconds` | `integer` | no | MaxRunSeconds is the execution budget (300-86400 seconds); zero uses one hour. |
| `model_id` | `string` | no | ModelID is a native model UUID override (models.id). |
| `name` | `string` | no | — |
| `pattern` | `string` | no | — |
| `reasoning_effort` | `string` | no | ReasoningEffort overrides the reasoning effort for this schedule. |
| `run_target` | `string` | no | RunTarget is new_session (default) or existing_session. |
| `runtime_type` | `string` | no | RuntimeType selects the runtime for new sessions: "" or "model" for the native model runtime, "acp_agent" for an ACP agent. Must be empty in existing_session mode (inherited from the target session). |
| `target_session_id` | `string` | no | TargetSessionID names the session reused by existing_session mode. |
| `workdir_id` | `string` | no | WorkdirID binds new sessions to a bot workdir. Must be empty in existing_session mode (inherited from the target session). |

### `schedule.ExecutionConfig`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_agent_id` | `string` | no | ACPAgentID names the ACP agent when RuntimeType is acp_agent. |
| `acp_model_id` | `string` | no | ACPModelID is an agent-reported model identifier override for External Agent runs. Mutually exclusive with ModelID. |
| `bot_agent_id` | `string` | no | BotAgentID selects one persisted BotAgent for a new session. Empty means the built-in Native runtime (or the legacy ACP fields below). |
| `max_run_seconds` | `integer` | no | MaxRunSeconds is the execution budget (300-86400 seconds); zero uses one hour. |
| `model_id` | `string` | no | ModelID is a native model UUID override (models.id). |
| `reasoning_effort` | `string` | no | ReasoningEffort overrides the reasoning effort for this schedule. |
| `run_target` | `string` | no | RunTarget is new_session (default) or existing_session. |
| `runtime_type` | `string` | no | RuntimeType selects the runtime for new sessions: "" or "model" for the native model runtime, "acp_agent" for an ACP agent. Must be empty in existing_session mode (inherited from the target session). |
| `target_session_id` | `string` | no | TargetSessionID names the session reused by existing_session mode. |
| `workdir_id` | `string` | no | WorkdirID binds new sessions to a bot workdir. Must be empty in existing_session mode (inherited from the target session). |

### `schedule.ListLogsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of schedule.Log` | no | — |
| `total_count` | `integer` | no | — |

### `schedule.ListResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of schedule.Schedule` | no | — |

### `schedule.ListRunSessionsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `array[] of schedule.RunSession` | no | — |
| `next_cursor` | `string` | no | — |

### `schedule.Log`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_id` | `string` | no | — |
| `completed_at` | `string` | no | — |
| `error_code` | `string` | no | — |
| `error_message` | `string` | no | ErrorMessage is the text an earlier server stored; new failures set ErrorCode and leave it empty. |
| `id` | `string` | no | — |
| `result_text` | `string` | no | — |
| `schedule_id` | `string` | no | — |
| `session_id` | `string` | no | — |
| `started_at` | `string` | no | — |
| `status` | `string` | no | — |
| `usage` | `any` | no | — |

### `schedule.NullableInt`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `set` | `boolean` | no | — |
| `value` | `integer` | no | — |

### `schedule.RunSession`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `completed_at` | `string` | no | CompletedAt is null while the fire is still running. |
| `error_code` | `string` | no | — |
| `log_id` | `string` | no | LogID identifies the fire (schedule_logs.id). |
| `session_id` | `string` | no | SessionID is empty when the fire failed before any session existed. |
| `started_at` | `string` | no | — |
| `status` | `string enum(running, ok, error)` | no | Status is running while the fire has not completed, then ok or error. |
| `title` | `string` | no | Title is the session title, empty when there is no session. |

### `schedule.Schedule`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acp_agent_id` | `string` | no | ACPAgentID names the ACP agent when RuntimeType is acp_agent. |
| `acp_model_id` | `string` | no | ACPModelID is an agent-reported model identifier override for External Agent runs. Mutually exclusive with ModelID. |
| `bot_agent_id` | `string` | no | BotAgentID selects one persisted BotAgent for a new session. Empty means the built-in Native runtime (or the legacy ACP fields below). |
| `bot_id` | `string` | no | — |
| `command` | `string` | no | — |
| `created_at` | `string` | no | — |
| `current_calls` | `integer` | no | — |
| `description` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `id` | `string` | no | — |
| `max_calls` | `integer` | no | — |
| `max_run_seconds` | `integer` | no | MaxRunSeconds is the execution budget (300-86400 seconds); zero uses one hour. |
| `model_id` | `string` | no | ModelID is a native model UUID override (models.id). |
| `name` | `string` | no | — |
| `pattern` | `string` | no | — |
| `reasoning_effort` | `string` | no | ReasoningEffort overrides the reasoning effort for this schedule. |
| `run_target` | `string` | no | RunTarget is new_session (default) or existing_session. |
| `runtime_type` | `string` | no | RuntimeType selects the runtime for new sessions: "" or "model" for the native model runtime, "acp_agent" for an ACP agent. Must be empty in existing_session mode (inherited from the target session). |
| `target_session_id` | `string` | no | TargetSessionID names the session reused by existing_session mode. |
| `updated_at` | `string` | no | — |
| `workdir_id` | `string` | no | WorkdirID binds new sessions to a bot workdir. Must be empty in existing_session mode (inherited from the target session). |

### `schedule.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `command` | `string` | no | — |
| `description` | `string` | no | — |
| `enabled` | `boolean` | no | — |
| `execution` | `any & schedule.ExecutionConfig` | no | Execution replaces the whole execution parameter block when present. Field-level patching is deliberately not offered: the block carries cross-field constraints (run target vs runtime vs workdir), so callers send the full desired state and the service validates it as one unit. |
| `max_calls` | `schedule.NullableInt` | no | — |
| `name` | `string` | no | — |
| `pattern` | `string` | no | — |

### `searchproviders.CreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `name` | `string` | no | — |
| `provider` | `searchproviders.ProviderName` | no | — |

### `searchproviders.GetResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `enable` | `boolean` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `searchproviders.ProviderConfigSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fields` | `object map of searchproviders.ProviderFieldSchema` | no | — |

### `searchproviders.ProviderFieldSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `enum` | `array[] of string` | no | — |
| `example` | `any` | no | — |
| `required` | `boolean` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |

### `searchproviders.ProviderMeta`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `searchproviders.ProviderConfigSchema` | no | — |
| `display_name` | `string` | no | — |
| `provider` | `string` | no | — |

### `searchproviders.ProviderName`

Schema: `string enum(brave, bing, google, tavily, sogou, serper, searxng, jina, exa, bocha, duckduckgo, yandex)`

### `searchproviders.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `enable` | `boolean` | no | — |
| `name` | `string` | no | — |
| `provider` | `searchproviders.ProviderName` | no | — |

### `server.Problem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `args` | `object map of string` | yes | — |
| `code` | `string` | yes | — |
| `detail` | `string` | yes | — |
| `fault` | `apperror.Fault` | yes | — |
| `request_id` | `string` | no | — |
| `status` | `integer` | yes | — |
| `trace_id` | `string` | no | — |
| `type` | `string` | yes | — |

### `session.Session`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_agent_id` | `string` | no | — |
| `bot_id` | `string` | no | — |
| `channel_type` | `string` | no | — |
| `created_at` | `string` | no | — |
| `created_by_user_id` | `string` | no | — |
| `id` | `string` | no | — |
| `metadata` | `object map of any` | no | — |
| `model_preference_revision` | `string` | no | — |
| `parent_session_id` | `string` | no | — |
| `preferred_chat_model_id` | `string` | no | — |
| `preferred_external_model_id` | `string` | no | Preferred* is the session's persisted (model, effort) pair (issue #879). Empty means "no memory"; the composer reseeds from it on open/repoint. |
| `preferred_reasoning_effort` | `string` | no | — |
| `route_conversation_type` | `string` | no | — |
| `route_id` | `string` | no | — |
| `route_metadata` | `object map of any` | no | — |
| `runtime_metadata` | `object map of any` | no | — |
| `runtime_type` | `string` | no | — |
| `session_mode` | `string` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |
| `updated_at` | `string` | no | — |
| `workdir_id` | `string` | no | — |

### `sessionruntime.FollowUpPendingRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `item_id` | `string` | no | — |

### `sessionruntime.QueueStatus`

Schema: `string enum(accepted, claimed, applied, rejected, expired, canceled)`

### `sessionruntime.SteerPendingRef`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `item_id` | `string` | no | — |

### `settings.Settings`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acl_default_effect` | `string` | no | — |
| `chat_acp_agent_id` | `string` | no | — |
| `chat_acp_project_mode` | `string` | no | — |
| `chat_acp_project_path` | `string` | no | — |
| `chat_model_id` | `string` | no | — |
| `chat_runtime` | `string` | no | — |
| `command_ui_language` | `string` | no | — |
| `compaction_enabled` | `boolean` | no | — |
| `compaction_model_id` | `string` | no | — |
| `compaction_target_percent` | `integer` | no | — |
| `compaction_threshold` | `integer` | no | — |
| `default_bot_agent_id` | `string` | no | — |
| `discuss_probe_model_id` | `string` | no | — |
| `display_enabled` | `boolean` | no | — |
| `fetch_provider_id` | `string` | no | — |
| `image_model_id` | `string` | no | — |
| `memory_enabled` | `boolean` | no | MemoryEnabled reports whether the bot uses Built-in Memory. It is derived from MemoryProviderID, which stays internal to the server. |
| `memory_llm_model_id` | `string` | no | MemoryLLMModelID is the LLM behind memory extract / decide / compact. Empty falls back to the chat model. |
| `overlay_config` | `object map of any` | no | — |
| `overlay_enabled` | `boolean` | no | — |
| `overlay_provider` | `string` | no | — |
| `persist_full_tool_results` | `boolean` | no | — |
| `reasoning_effort` | `string` | no | ReasoningEffort is the single on/off source for reasoning: models.ReasoningEffortDisable means no reasoning, any other value is a tier. |
| `search_provider_id` | `string` | no | — |
| `show_tool_calls_in_im` | `boolean` | no | — |
| `timezone` | `string` | no | — |
| `tool_approval_config` | `settings.ToolApprovalConfig` | no | — |
| `transcription_model_id` | `string` | no | — |
| `tts_model_id` | `string` | no | — |
| `video_model_id` | `string` | no | — |

### `settings.ToolApprovalConfig`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | no | — |
| `exec` | `settings.ToolApprovalExecPolicy` | no | — |
| `read` | `settings.ToolApprovalFilePolicy` | no | — |
| `write` | `settings.ToolApprovalFilePolicy` | no | — |

### `settings.ToolApprovalExecPolicy`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bypass_commands` | `array[] of string` | no | — |
| `force_review_commands` | `array[] of string` | no | — |
| `mode` | `settings.ToolApprovalMode` | no | — |
| `require_approval` | `boolean` | no | — |

### `settings.ToolApprovalFilePolicy`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bypass_globs` | `array[] of string` | no | — |
| `force_review_globs` | `array[] of string` | no | — |
| `mode` | `settings.ToolApprovalMode` | no | — |
| `require_approval` | `boolean` | no | — |

### `settings.ToolApprovalMode`

Schema: `string enum(allow, ask, deny)`

### `settings.UpsertRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `acl_default_effect` | `string` | no | — |
| `chat_acp_agent_id` | `string` | no | — |
| `chat_acp_project_mode` | `string` | no | — |
| `chat_acp_project_path` | `string` | no | — |
| `chat_model_id` | `string` | no | Reference fields below use pointer semantics so autosaving clients can clear a selection: nil = keep current, "" = clear, value = set. The service mirrors each into a `<field>_set` SQL flag (same pattern as FetchProviderID / CompactionModelID); plain strings would make "" indistinguishable from "not sent". |
| `chat_runtime` | `string` | no | — |
| `command_ui_language` | `string` | no | — |
| `compaction_enabled` | `boolean` | no | — |
| `compaction_model_id` | `string` | no | — |
| `compaction_target_percent` | `integer` | no | — |
| `compaction_threshold` | `integer` | no | — |
| `default_bot_agent_id` | `string` | no | — |
| `discuss_probe_model_id` | `string` | no | — |
| `display_enabled` | `boolean` | no | — |
| `fetch_provider_id` | `string` | no | — |
| `image_model_id` | `string` | no | — |
| `memory_enabled` | `boolean` | no | MemoryEnabled turns Built-in Memory on or off; nil keeps the current state. |
| `memory_llm_model_id` | `string` | no | — |
| `overlay_config` | `object map of any` | no | — |
| `overlay_enabled` | `boolean` | no | — |
| `overlay_provider` | `string` | no | — |
| `persist_full_tool_results` | `boolean` | no | — |
| `reasoning_effort` | `string` | no | — |
| `search_provider_id` | `string` | no | — |
| `show_tool_calls_in_im` | `boolean` | no | — |
| `timezone` | `string` | no | — |
| `tool_approval_config` | `settings.ToolApprovalConfig` | no | — |
| `transcription_model_id` | `string` | no | — |
| `tts_model_id` | `string` | no | — |
| `video_model_id` | `string` | no | — |

### `skills.SafeCatalogItem`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `display_name` | `string` | no | — |
| `name` | `string` | no | — |
| `source_kind` | `string` | no | — |
| `state` | `string` | no | — |

### `turn.RuntimeCommand`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `completed_text` | `string` | no | — |
| `description` | `string` | no | — |
| `i18n_key` | `string` | no | I18nKey identifies host-owned copy. Native descriptions take precedence. |
| `input_hint` | `string` | no | — |
| `kind` | `turn.RuntimeCommandKind` | no | — |
| `name` | `string` | no | — |
| `running_text` | `string` | no | — |

### `turn.RuntimeCommandKind`

Schema: `string enum(turn, read, operation)`

### `turn.RuntimeControlCapabilities`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `compact` | `boolean` | no | — |
| `fork_latest_only` | `boolean` | no | — |
| `goal` | `boolean` | no | — |
| `permission_modes` | `boolean` | no | — |
| `plan_mode` | `boolean` | no | — |

### `turn.RuntimeMode`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `i18n_key` | `string` | no | — |
| `icon` | `string` | no | Icon is a presentation hint; clients may fall back for unknown values. |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `warning` | `boolean` | no | Warning marks a runtime-declared elevated permission option. |

### `turn.RuntimeModeState`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `apply_on_next_turn` | `boolean` | no | Direct runtimes save a preference and apply it when starting the next turn. |
| `available_modes` | `array[] of turn.RuntimeMode` | no | — |
| `current_mode_id` | `string` | no | — |
| `kind` | `string` | no | Kind distinguishes permission presets from arbitrary agent session modes. |
| `supported` | `boolean` | no | — |

### `userinput.UIAnswer`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `custom_text` | `string` | no | — |
| `question` | `string` | no | — |
| `question_id` | `string` | no | — |
| `selected` | `array[] of userinput.UIOption` | no | — |
| `skipped` | `boolean` | no | — |
| `text` | `string` | no | — |

### `userinput.UIOption`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `label` | `string` | no | — |
| `label_key` | `string` | no | — |

### `userinput.UIQuestion`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `allow_custom` | `boolean` | no | — |
| `custom_exclusive` | `boolean` | no | — |
| `id` | `string` | no | — |
| `kind` | `string` | no | — |
| `options` | `array[] of userinput.UIOption` | no | — |
| `placeholder` | `string` | no | — |
| `required` | `boolean` | no | Required is tri-state for compatibility. Legacy/native ask_user payloads omit it and keep their existing surface semantics; ACP forms set it explicitly so false means an optional schema property. |
| `text` | `string` | no | — |

### `userruntime.CreateRuntimeRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | `string` | no | — |

### `userruntime.Runtime`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `arch` | `string` | no | — |
| `capabilities` | `array[] of string` | no | — |
| `client_version` | `string` | no | — |
| `created_at` | `string` | no | — |
| `hostname` | `string` | no | — |
| `id` | `string` | no | — |
| `key` | `string` | no | — |
| `name` | `string` | no | — |
| `online` | `boolean` | no | — |
| `os` | `string` | no | — |
| `team_id` | `string` | no | — |
| `workspace_base` | `string` | no | — |

### `video.ConfigSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fields` | `array[] of video.FieldSchema` | no | — |

### `video.FieldSchema`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `advanced` | `boolean` | no | — |
| `description` | `string` | no | — |
| `enum` | `array[] of string` | no | — |
| `example` | `any` | no | — |
| `key` | `string` | no | — |
| `order` | `integer` | no | — |
| `required` | `boolean` | no | — |
| `title` | `string` | no | — |
| `type` | `string` | no | — |

### `video.ImportModelsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `created` | `integer` | no | — |
| `models` | `array[] of string` | no | — |
| `skipped` | `integer` | no | — |

### `video.ModelInfo`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `video.ConfigSchema` | no | — |
| `description` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |

### `video.ModelResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `id` | `string` | no | — |
| `model_id` | `string` | no | — |
| `name` | `string` | no | — |
| `provider_id` | `string` | no | — |
| `provider_type` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `video.ProviderMetaResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config_schema` | `video.ConfigSchema` | no | — |
| `default_model` | `string` | no | — |
| `description` | `string` | no | — |
| `display_name` | `string` | no | — |
| `models` | `array[] of video.ModelInfo` | no | — |
| `provider` | `string` | no | — |
| `supports_list` | `boolean` | no | — |

### `video.ProviderResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `client_type` | `string` | no | — |
| `config` | `object map of any` | no | — |
| `created_at` | `string` | no | — |
| `enable` | `boolean` | no | — |
| `icon` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `updated_at` | `string` | no | — |

### `video.UpdateModelRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `config` | `object map of any` | no | — |
| `name` | `string` | no | — |

### `webhooktunnel.Status`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | no | — |
| `error` | `string` | no | — |
| `mode` | `string` | no | — |
| `public_base_url` | `string` | no | — |
| `status` | `string` | no | — |

### `workdir.CreateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | `string` | yes | — |
| `path` | `string` | yes | — |
| `workspace_target_id` | `string` | no | — |

### `workdir.DirectoriesResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `directories` | `array[] of workdir.Directory` | no | — |
| `path` | `string` | no | — |
| `workspace_target_id` | `string` | no | — |

### `workdir.Directory`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | `string` | no | — |
| `path` | `string` | no | — |

### `workdir.GitBranchResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `branch` | `string` | no | — |
| `branches` | `array[] of string` | no | — |
| `busy` | `boolean` | no | — |

### `workdir.SwitchGitBranchRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `branch` | `string` | yes | — |

### `workdir.UpdateRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | `string` | yes | — |

### `workdir.Workdir`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `archived` | `boolean` | no | — |
| `bot_id` | `string` | no | — |
| `created_at` | `string` | no | — |
| `created_by_user_id` | `string` | no | — |
| `id` | `string` | no | — |
| `name` | `string` | no | — |
| `path` | `string` | no | — |
| `target_kind` | `string` | no | — |
| `updated_at` | `string` | no | — |
| `workspace_target_id` | `string` | no | — |

### `workdir.WorkdirsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `workdirs` | `array[] of workdir.Workdir` | no | — |

### `workspace.SetPrimaryWorkspaceTargetRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `target_id` | `string` | yes | — |

### `workspace.UpdateWorkspaceTargetToolApprovalRequest`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | no | — |
| `exec` | `settings.ToolApprovalMode` | no | — |
| `read` | `settings.ToolApprovalMode` | no | — |
| `tool_approval_config` | `settings.ToolApprovalConfig` | no | — |
| `write` | `settings.ToolApprovalMode` | no | — |

### `workspace.WorkspaceTarget`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `kind` | `string` | no | — |
| `name` | `string` | no | — |
| `online` | `boolean` | no | — |
| `primary` | `boolean` | no | — |
| `runtime_id` | `string` | no | — |
| `status` | `string` | no | — |
| `target_id` | `string` | no | — |
| `tool_approval` | `workspace.WorkspaceTargetToolApproval` | no | — |
| `tool_approval_config` | `settings.ToolApprovalConfig` | no | — |

### `workspace.WorkspaceTargetGrant`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `bot_id` | `string` | no | — |
| `primary` | `boolean` | no | — |
| `runtime_id` | `string` | no | — |
| `target_id` | `string` | no | — |

### `workspace.WorkspaceTargetGrantsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `grants` | `array[] of workspace.WorkspaceTargetGrant` | no | — |

### `workspace.WorkspaceTargetsResponse`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `targets` | `array[] of workspace.WorkspaceTarget` | no | — |

### `workspace.WorkspaceTargetToolApproval`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `exec` | `settings.ToolApprovalMode` | no | — |
| `read` | `settings.ToolApprovalMode` | no | — |
| `write` | `settings.ToolApprovalMode` | no | — |
