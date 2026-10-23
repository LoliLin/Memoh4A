# Memoh Android client

This patch adds a **native Android Memoh chat and management client** to the existing Agora codebase. It deliberately does not turn Memoh into an OpenAI-compatible model provider, intercept Agora's local Room conversation graph, or reimplement Memoh's server-side agent runtime.

## Install

Build the integrated source from `LoliLin/Memoh4A`:

```sh
./gradlew :app:assembleFdroidDebug
```

The fork has one Memoh4A launcher and its own Android application ID `in.kawaiis.memoh`. Its dedicated `MemohApplication` installs crash reporting without starting Agora's local database, tokenizer, llama runtime or automation services. The manifest registers only the Memoh launcher; the upstream Agora entry points remain source references. Building needs an Android SDK and Java 21, with no NDK, CMake, PRoot or llama submodule checkout.

The current target is **Memoh Cloud at `https://app.memoh.net`**. Enter that origin in the login form; the client resolves platform requests to `/api/v1` and team business requests to `/api/memoh`. Remote connections require HTTPS.

Cloud sign-in uses an emailed code, then confirms the platform identity and loads team memberships before requesting Bots. The picker preserves a valid team per account, supports switching and an empty-membership state, and scopes JSON, file and socket-ticket requests consistently. Cookies currently remain in memory, so a new process requires sign-in again. A 401 during Bot loading signs out the client. Google browser-to-app handoff, MFA, registration and password reset are not implemented.

## Implemented

- Native Compose chat surface and multi-bot picker; paged conversation drawer and on-demand older history
- Per-bot Memory manager: list/search/add/edit/delete, confirmed bulk/delete-all, graph, status/usage, ingest/rebuild/compact; team-wide embedding configuration is clearly separated and permission-aware
- Rename and delete server-side chat sessions (explicit deletion confirmation)
- Per-bot workspace file browser: listing and navigation, viewing, revision-fenced text edits, system-picker upload, plus **folder creation, rename, confirmed recursive delete, single-file download, selected-folder tar.gz download and ZIP/TAR.GZ/TGZ extraction**. Long-press any entry for the action sheet; extraction uses the server-selected destination and does not overwrite existing directories.
- Server-side schedules: list, create and **edit** (cron, instruction, description), enable/disable, confirmed delete, plus **run history** (per-schedule logs with status/errors/result text, and confirmed clearing). Both run targets are supported — `new_session` with an optional model override, or `existing_session` continuing a chosen conversation (picking a session inherits its runtime, so model/agent overrides are refused there). Run budget is validated to the server's 300–86400s range.
- Schedule history supports per-task and bot-wide paginated runs. Open a run's conversation using the server's full session detail, preserving Agent/runtime metadata and rejecting stale navigation responses.
- Bot backup summary and binary export through Android's system save-document picker; server-previewed import through the open-document picker, with explicit confirmation and server result reporting
- In-band first-message session creation; client-minted `invocation_id` for replay-safe sends
- Cloud WebSocket `/bots/{bot_id}/web/ws`: request a team-scoped short-lived ticket, include `team_id` and `ticket` in the URL, and preserve runtime subscribe/unsubscribe, reconnect and admission replay
- Runtime snapshot/delta text, reasoning, tool input/output/diff details, stop action and permission choices
- Structured Agent user-input responses (text, single-select, multi-select); control acknowledgement tracking and reconnect fencing
- Server-side follow-up and steer queues for active runs, plus queue viewing and removal
- Server-backed JSON history and session list, Markdown replies, optimistic pending user text
- Original Agora conversations/settings unchanged

## Gaps before daily use

**2026-10-23 verification:** the APK compiles and packages; 118 targeted tests in 15 suites pass with zero failures, errors or skips. The Kotlin 800-line gate passes for 1,352 files. The 11 Node probe tests pass. Public Cloud HTTP checks confirm email sign-in configuration, enabled Google/GitHub providers and OAuth-start callback behavior. Local HTTP tests reproduce the missing-team error once and observe zero such errors through the corrected login/team/Bot/ticket/file flow. These targeted results do not establish a passing full-project suite or an authenticated Cloud message round trip. See [protocol evidence](MEMOH_CLOUD_PROTOCOL.md) and the [local probe](../tools/memoh-probe/README.md). Remaining gaps:

- Chat attachments use base64 with an 8 MiB per-file cap, a 16 MiB total draft cap and at most 10 files. There is no chunked message upload path; the workspace uploader handles larger standalone files.
- Workspace file browser has no media preview and no folder upload (single file at a time, into the current directory).
- Backup import uses the server default section set; per-section import selection is not exposed. Backup export supports selecting documented sections and an optional encryption passphrase. Encrypted imports can enter a passphrase when the server preview requests one.
- Schedule workdir binding and per-schedule agent/ACP runtime selection are not exposed (the fields are plumbed for new-session schedules but there is no bot-workdir picker). Terminal, audio, slash commands, queue reorder/edit, and external-agent setup remain incomplete.
- The runtime reducer handles the common snapshot/delta text and tool paths, but not the entire Memoh Web client's event/cursor/resync semantics. Do not use it for high-impact Agent operations without verifying reconnection and approval flows.
- This fork launches directly into Memoh4A. The upstream Agora UI is not surfaced in the new navigation.
- Email-code Cloud sign-in is implemented; Google browser-to-app credential handoff and MFA remain unimplemented.
- Tool details provide bounded raw input/output/diff text, not syntax-highlighted code/diff editors.
- An authenticated Cloud send, reconnect, approval and history round trip remains to be exercised; public endpoints and local protocol tests are verified separately.

## Contract / next slice

The client treats Memoh as a remote runtime and server-owned session history as the source of truth. On a disconnected socket, **never** infer that a run stopped. `invocation_id` is an idempotency key; `run_id` addresses stop/decisions. A runtime subscription merely observes work and closing it must not abort the work.

The full API reference for the audited backend snapshot is [MEMOH_API_COVERAGE.md](MEMOH_API_COVERAGE.md): 338 documented operations, 260 paths, and all 475 Swagger schema definitions, pinned to `felinics/Memoh` commit `f3e8199560d42109be4b63bfc82ac62f260a66e2`. Routes registered in Go but absent from Swagger are listed separately in [MEMOH_UNDOCUMENTED_ROUTES.md](MEMOH_UNDOCUMENTED_ROUTES.md). The inventory is the backend surface; it does not claim every server/admin endpoint is a mobile user feature. Use it as the coverage ledger while native user flows continue to be implemented.

The Memory manager now calls all 12 bot-scoped Memory operations plus the two team-wide Memory configuration operations directly. Destructive operations require confirmation, and team configuration permissions do not block bot-scoped Memory access.

This is an experimental fork of [newo-ether/Agora](https://github.com/newo-ether/Agora) under GPL-3.0. The existing LICENSE and attribution are preserved. Local builds, targeted tests and public Cloud HTTP probes have passed. Authenticated Cloud message-flow checks remain outstanding and can be driven with browser/HTTP/WebSocket probes without a physical Android device. Note that unit tests must run on a Java 21 JVM — Robolectric cannot read newer class files, and `app/build.gradle.kts` pins the test launcher accordingly.
