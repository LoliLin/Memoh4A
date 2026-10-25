# Memoh Android client

This patch adds a **native Android Memoh chat and management client** to the existing Agora codebase. It deliberately does not turn Memoh into an OpenAI-compatible model provider, intercept Agora's local Room conversation graph, or reimplement Memoh's server-side agent runtime.

## Install

Build the integrated source from `LoliLin/Memoh4A`:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

When updating an existing checkout from the former flavored layout, run `:app:clean` once first. Kotlin/AGP output can retain classes for deleted sources across that layout change; both unit tests and the packaged APK must be verified from fresh outputs.

The fork has one Memoh4A launcher and its own Android application ID `in.kawaiis.memoh`. Its dedicated `MemohApplication` installs crash reporting without starting Agora's local database, tokenizer, llama runtime or automation services. The manifest registers only the Memoh launcher; the upstream Agora entry points remain source references. Building needs an Android SDK and Java 21, with no NDK, CMake, PRoot or llama submodule checkout.

The current target is **Memoh Cloud at `https://app.memoh.net`**. Enter that origin in the login form; the client resolves platform requests to `/api/v1` and team business requests to `/api/memoh`. Remote connections require HTTPS.

Cloud sign-in uses an emailed code, then confirms the platform identity and loads team memberships before requesting Bots. The picker preserves a valid team per account, supports switching and an empty-membership state, and scopes JSON, file and socket-ticket requests consistently. Cookies are sealed with AndroidKeyStore AES-GCM and partitioned by scheme, host and port. The former unscoped cookie is migrated only for the official Cloud origin. Only an explicit 401 rejects a restored credential; an offline restore keeps it and offers a retry. Logout removes the stored cookie synchronously and retires its jar, so late responses cannot overwrite a subsequent login. Google browser-to-app handoff, MFA, registration and password reset are not implemented.

## Architecture boundaries (2026-10-24)

- `MemohApplication`/`MemohActivity` define the client bootstrap and launcher. Memoh renders through Agora's shared drawer, top bar, message list, composer, theme and attachment components. The shared Appearance page reads the lightweight settings repository directly, so Memoh can reuse Agora preferences without starting Room or the rest of its local runtime.
- The app has a single `debug`/`release` variant pair. Play/F-Droid source sets, PRoot implementation/tests, embedded llama ownership/JNI wrappers and the unused talloc sources are removed. Optional sandbox interfaces remain for shared settings. Imported local-provider requests fail explicitly instead of loading missing libraries. The 800-line plugin, baseline and policy are removed.
- `MemohConnection` owns ticket creation, the current socket, callback generations, cancellation and capped reconnect delay. Ticket failures and socket failures follow the same path. Temporary I/O, 408, 429 and server errors retry; other 4xx errors wait for explicit Reconnect. A 401 ends the session. Old socket callbacks cannot mutate the new bot/team/account. Observation loss never means a server run stopped.
- `MemohTransport` owns identity/team scopes and cancellable JSON requests. Cookie-authenticated requests and logout declare the server origin. JSON requests are not automatically replayed by application code. Only unacknowledged message invocations retain their existing `invocation_id` for socket recovery.
- Pagination state resets on navigation. A short page or a cursor without progress ends older-message loading. Older history stays visible on request errors and prepending a page does not trigger a jump to the newest message. Admission timeout bounds offline pending sends and restores text plus attachments, while explicitly leaving server acceptance uncertain.

## Implemented

- Native Compose chat surface and multi-bot picker; paged conversation drawer and on-demand older history
- Per-bot Memory manager: list/search/add/edit/delete, confirmed bulk/delete-all, graph, status/usage, ingest/rebuild/compact; team-wide embedding configuration is clearly separated and permission-aware
- Rename and delete server-side chat sessions (explicit deletion confirmation)
- Per-bot workspace file browser: listing and navigation, viewing, revision-fenced text edits, system-picker upload, plus **folder creation, rename, confirmed recursive delete, single-file download, selected-folder tar.gz download and ZIP/TAR.GZ/TGZ extraction**. Long-press any entry for the action sheet; extraction uses the server-selected destination and does not overwrite existing directories.
- Server-side schedules: list, create and **edit** (cron, instruction, description), enable/disable, confirmed delete, plus **run history** (per-schedule logs with status/errors/result text, and confirmed clearing). Both run targets are supported — `new_session` with an optional model override, or `existing_session` continuing a chosen conversation (picking a session inherits its runtime, so model/agent overrides are refused there). Run budget is validated to the server's 300–86400s range.
- Schedule history supports per-task and bot-wide paginated runs. Open a run's conversation using the server's full session detail, preserving Agent/runtime metadata and rejecting stale navigation responses.
- Bot backup summary and binary export through Android's system save-document picker; server-previewed import through the open-document picker, with explicit confirmation and server result reporting
- In-band first-message session creation; client-minted `invocation_id` for replay-safe sends
- Interactive workspace shell per bot: bundled xterm.js VT/ANSI emulator, scoped/cookie-ticket WebSocket, binary input/output, resize frames, shortcut-key strip, explicit reconnect and foreground lifecycle handling
- Cloud chat WebSocket `/bots/{bot_id}/web/ws`: team-scoped short-lived ticket, runtime subscribe/unsubscribe, reconnect and admission replay
- Runtime snapshot/delta text, reasoning, tool input/output/diff details, stop action and permission choices
- Structured Agent user-input responses (text, single-select, multi-select); control acknowledgement tracking and reconnect fencing
- Server-side follow-up and steer queues for active runs: viewing, editing, reordering, promoting to steer, and removal
- Session runtime controls: permission/plan mode switching, runtime-owned goal (pause/clear with usage), runtime commands, and immediate context compaction
- Chat-time model preference: Agent model catalog picker with reasoning effort, welcome-composer model seed, `model_id`/`reasoning_effort` send parameters, and `model_preference_settled` tracking
- Server-backed JSON history and session list, Markdown replies, optimistic pending user text
- Shared Agora appearance preferences now apply to Memoh: theme, color, font, blur, reduced motion, haptics, message rendering and stick-to-bottom behavior. Native voice input uses Android's speech recognizer and inserts recognized text into the composer.

## Verification (2026-10-24, Asia/Shanghai)

- A clean `:app:clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --continue` run executed 3,863 tests in 585 suites: 2 failures, 0 errors and 3 skips. All 124 Memoh tests in that run passed. The failures were a Settings source contract coupled to the already-missing optional screenshot script, and a WebUI image test whose symbolic-link setup lacked Windows privileges. Release vital lint also caught the retained foreground worker's missing `dataSync` manifest declaration.
- The Settings and screenshot-tool contracts are now separate. Only the absent optional tool is skipped. The symbolic-link boundary assertion remains enabled on hosts that can create links; this Windows account records an explicit assumption skip. The shared foreground worker now declares its service type and required permissions while WorkManager initialization stays lazy.
- Follow-up validation of `com.newoether.agora.memoh.*`, `ApplicationUiSourceContractTest`, `CurrentApprovedFeatureSourceContractTest` and `WebUiToolImagesTest` executed **160 tests in 25 suites: 158 passed, 0 failures/errors, 2 explicit environment/tooling skips**. The **125 Memoh tests all passed without skips**, including the added merged-manifest/startup check. This is a full-project initial run followed by focused regression checks, rather than a claim of a second all-green full-project run.
- `:app:assembleDebug` and `:app:assembleRelease`, including release vital lint, completed successfully. Both APKs pass `apksigner verify`, launch `MemohActivity` through `MemohApplication`, and declare the merged `dataSync` service type. Native-library scans for llama/ggml/PRoot/talloc and DEX scans for the deleted runtime/factory types found zero matches in either APK.
- Cookie persistence tests inject a reversible sealing codec because Robolectric has no AndroidKeyStore. They verify ownership, migration, retirement and envelope boundaries; they do not prove hardware-backed encryption on a device.

| Artifact | Application ID | Size | SHA-256 |
|---|---|---|---|
| Debug, built 12:55:58 | `in.kawaiis.memoh.screenshots` | 62.90 MiB | `59565CFD104192BEF0A3D4A6DC996AAA080ED611300F983188916266110D1811` |
| Release, built 13:01:13 | `in.kawaiis.memoh` | 23.91 MiB | `B3D30B707D1D031A4F1D72784697C41F6651578A7E46EC9293C662E9B45C0A94` |

Earlier public HTTP/OAuth and local team-protocol evidence is recorded in [protocol evidence](MEMOH_CLOUD_PROTOCOL.md) and the [local probe](../tools/memoh-probe/README.md). Those checks are separate from authenticated Cloud messaging and from this build's local fixture tests.

## API parity ledger (2026-10-25)

A literal audit of all 338 documented Swagger operations against Android source path literals: **330 operations are wired through `MemohApi`/`MemohAdminApi`**, 8 remain intentionally unwired. The audit script matches static path segments in order inside one Kotlin string literal; a hit proves a call wrapper exists, not a dedicated screen.

The 8 unwired operations and why:

- `POST /bots/{bot_id}/mcp-stdio`, `POST /bots/{bot_id}/mcp-stdio/{connection_id}` — MCP stdio proxy bridging; server-side administration path.
- `POST /bots/{bot_id}/tools` — unified MCP tools gateway used by the web tool runner.
- `POST /bots/{bot_id}/web/messages`, `GET /bots/{bot_id}/web/stream` — local-channel message/SSE path superseded by the realtime WebSocket.
- `GET /bots/{bot_id}/sessions/events` — SSE stream confirmed dead code in the current web bundle.
- `GET /oauth/mcp/callback`, `GET /providers/oauth/callback` — server-rendered OAuth callback routes; a native client completes OAuth through the browser handoff instead.

## Terminal slice verification (2026-10-25)

- `:app:compileDebugKotlin` and `:app:assembleDebug` succeeded. The 67.35 MiB debug APK includes the bundled xterm.js 5.5.0 and fit addon 0.10.0 assets with both MIT license files, and launches `MemohActivity`.
- Node syntax checks passed for xterm.js, the fit addon and the embedded terminal host script.
- Unit tests, authenticated server terminal connection, keyboard interaction and a device screenshot were not run for this slice; the APK/build checks do not establish those behaviors.

## Shared settings and voice-input verification (2026-10-25)

- `:app:compileDebugKotlin`, the filtered Memoh and settings suite (**155 tests**), and `:app:assembleDebug` succeeded. The six `MemohRecoveryTest` cases also pass, including the team-scope restore-generation regression.
- Debug APK built at 08:25:23: 67.35 MiB, SHA-256 `4468C0BB44136F28EE3CA461F91E4798534E2CE29FE3311F7B1A2797A92F8B7F`.
- No Android device was connected. Theme/motion/haptics behavior and the system speech-recognition result still need on-device review; the microphone action currently inserts speech-to-text into the composer.


## Release build verification (2026-10-25)

- `:app:assembleRelease` completed successfully (exit 0; 14m 22s). `apksigner verify --verbose` also passed using the configured Java 21 runtime; the APK verifies with APK Signature Scheme v2 and has one signer.
- Release APK: `in.kawaiis.memoh`, version `2.1.0`, 26,034,065 bytes (24.83 MiB), SHA-256 `A8450A0241CBF778536351AA5EA4B58CEDC128A69CE8243F6F2B2C343E17B3A3`.
- Release Compose mapping emitted a warning while tokenizing the legacy `MainActivity` coroutine stack trace. Packaging and signature verification succeeded; device behavior remains unverified.

## Agora bridge follow-up (2026-10-25)

- The conversation list and composer now share Agora's centered 840 dp width cap. `MessageList` receives the same loaded transcript for its visible, authoritative and `allMessages` projections; stream auto-follow respects the shared stick-to-bottom preference.
- Memoh now uses Agora's `ChatScrollCoordinator` for viewport measurement and keyboard anchoring. A session becomes eligible only after its history response succeeds (or a new empty session is created); switching sessions, bots or identity clears that marker. Stream following pauses during search, reduced-motion restrictions and IME anchoring.
- `:app:compileDebugKotlin` and `:app:assembleDebug` both succeeded for this follow-up. `apksigner verify` passed.
- Current Debug APK built at 09:04:32: `in.kawaiis.memoh.screenshots`, version `2.1.0-screenshots`, 67.35 MiB, SHA-256 `8725EA818383714BB417C3E14B00FFD5C71D16FF83D939AE39F45297E1BF6732`.
- No Android device was connected, so visual and keyboard behavior still need device review.

## Message action bridge (2026-10-25)

- Memoh search now passes its active query and selected match into Agora's `MessageList`, enabling message highlighting and match navigation.
- Recompact is connected to Memoh's session compaction request and disabled while runtime controls are busy. Per-message and compaction-record deletion are hidden in Memoh because its API only deletes whole sessions or bot history; Agora's shared component keeps deletion enabled by default for its existing callers.
- Historical PDF tiles remain non-clickable: Memoh returns the PDF file, while Agora's shared pager expects rendered page images. `:app:compileDebugKotlin` passed after these changes; no tests, packaging or device checks were run for this slice.

## Gaps before daily use

- Chat attachments use base64 with an 8 MiB per-file cap, a 16 MiB total raw draft cap and at most 10 files. Base64/JSON overhead also consumes the WebSocket queue budget, so a raw draft near that total cap may still be refused. There is no chunked message upload path; the workspace uploader handles larger standalone files.
- Workspace file browser has no media preview and no folder upload (single file at a time, into the current directory).
- Backup import uses the server default section set; per-section import selection is not exposed. Backup export supports selecting documented sections and an optional encryption passphrase. Encrypted imports can enter a passphrase when the server preview requests one.
- Schedule workdir binding and per-schedule agent/ACP runtime selection are not exposed (the fields are plumbed for new-session schedules but there is no bot-workdir picker). Sending recorded audio and slash commands remain incomplete; the new microphone action is speech-to-text input only.
- RFB/VNC desktop has no route or WebSocket in the current backend inventory, including the undocumented route list; a native viewer needs a server endpoint before it can connect.
- Runtime and composer coverage includes snapshot/delta merging, edit/retry actions, model preference settlement, and user/steer/progress updates. Slash-command dispatch and `command_result`/`command_error` events are not surfaced.
- This fork launches into the Memoh workspace through Agora's shared chat shell. The Appearance page is connected; the broader upstream provider, runtime and data-control settings screens still depend on Agora's full view model/runtime and are not exposed in Memoh.
- Email-code Cloud sign-in is implemented; Google browser-to-app credential handoff and MFA remain unimplemented.
- Tool details provide bounded raw input/output/diff text, not syntax-highlighted code/diff editors.
- Native bitmap capture and visual comparison against the intended Agora worktree remain unverified. Shell render tests cover projected chat rows/top bar and the launcher tests exercise startup/login UI.
- An authenticated Cloud send, reconnect, approval and history round trip remains to be exercised; public endpoints and local protocol tests are verified separately.

## Contract / next slice

The client treats Memoh as a remote runtime and server-owned session history as the source of truth. On a disconnected socket, **never** infer that a run stopped. `invocation_id` is an idempotency key; `run_id` addresses stop/decisions. A runtime subscription merely observes work and closing it must not abort the work.

The full API reference for the audited backend snapshot is [MEMOH_API_COVERAGE.md](MEMOH_API_COVERAGE.md): 338 documented operations, 260 paths, and all 475 Swagger schema definitions, pinned to `felinics/Memoh` commit `f3e8199560d42109be4b63bfc82ac62f260a66e2`. Routes registered in Go but absent from Swagger are listed separately in [MEMOH_UNDOCUMENTED_ROUTES.md](MEMOH_UNDOCUMENTED_ROUTES.md). The inventory is the backend surface; it does not claim every server/admin endpoint is a mobile user feature. Use it as the coverage ledger while native user flows continue to be implemented.

The Memory manager now calls all 12 bot-scoped Memory operations plus the two team-wide Memory configuration operations directly. Destructive operations require confirmation, and team configuration permissions do not block bot-scoped Memory access.

This is an experimental fork of [newo-ether/Agora](https://github.com/newo-ether/Agora) under GPL-3.0. The existing LICENSE and attribution are preserved. Local builds, targeted tests and public Cloud HTTP probes have passed. Authenticated Cloud message-flow checks remain outstanding and can be driven with browser/HTTP/WebSocket probes without a physical Android device. Note that unit tests must run on a Java 21 JVM — Robolectric cannot read newer class files, and `app/build.gradle.kts` pins the test launcher accordingly.
