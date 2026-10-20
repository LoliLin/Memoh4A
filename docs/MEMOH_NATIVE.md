# Memoh Android client

This patch adds a **native Android Memoh chat and management client** to the existing Agora codebase. It deliberately does not turn Memoh into an OpenAI-compatible model provider, intercept Agora's local Room conversation graph, or reimplement Memoh's server-side agent runtime.

## Install

Build the integrated source from `LoliLin/Memoh4A`:

```sh
./gradlew :app:assembleFdroidDebug
```

The fork has one Memoh4A launcher and its own Android application ID `in.kawaiis.memoh`. The upstream Agora MainActivity and local conversation store remain untouched and internal. No signed APK is included.

Use a Memoh API base URL, not just its website URL. For Memoh Cloud the default is `https://app.memoh.net/api`; if your self-hosted server exposes API at its root (for example on port 8080), use that direct root URL instead of appending `/api`. Remote connections require HTTPS. Localhost may use HTTP.

Log in using the Memoh server's username/password API. The Bearer JWT is stored using an AndroidKeyStore AES-GCM envelope; the password isn't persisted. A 401 during initial bot loading signs out the client. Password reset, registration, and Cloud-specific SSO are not implemented here.

## Implemented

- Native Compose chat surface and multi-bot picker; paged conversation drawer and on-demand older history
- Per-bot Memory manager: list/search/add/edit/delete, confirmed bulk/delete-all, graph, status/usage, ingest/rebuild/compact; team-wide embedding configuration is clearly separated and permission-aware
- Rename and delete server-side chat sessions (explicit deletion confirmation)
- Per-bot workspace file browser for listing, viewing, revision-fenced text edits and system picker uploads
- Server-side schedule list, creation, enable/disable and deletion (basic cron/new-session creation only)
- Bot backup summary and binary export through Android's system save-document picker; server-previewed import through the open-document picker, with explicit confirmation and server result reporting
- In-band first-message session creation; client-minted `invocation_id` for replay-safe sends
- WebSocket `/bots/{bot_id}/web/ws` with a bearer-authenticated handshake, runtime subscribe/unsubscribe, reconnect and admission replay
- Runtime snapshot/delta text, reasoning, tool input/output/diff details, stop action and permission choices
- Structured Agent user-input responses (text, single-select, multi-select); control acknowledgement tracking and reconnect fencing
- Server-side follow-up and steer queues for active runs, plus queue viewing and removal
- Server-backed JSON history and session list, Markdown replies, optimistic pending user text
- Original Agora conversations/settings unchanged

## Gaps before daily use

This is **source-only and not build-verified or live-tested against an authenticated Memoh deployment**. It is a first integration slice, not a polished replacement for the full Memoh Web UI. In particular:

- Chat-composer attachments and inline image previews are not implemented; the separate workspace file uploader is not a chat attachment API.
- Workspace file browser currently supports single-file upload and text editing, not folder uploads, bulk actions or media preview.
- Backup import uses the server default section set; per-section import selection is not exposed. Backup export supports selecting documented sections and an optional encryption passphrase. Encrypted imports can enter a passphrase when the server preview requests one.
- Advanced schedules (existing-session targets, cron editing, runs/logs), terminal, audio, slash commands, queue reorder/edit, and external-agent setup remain incomplete.
- The runtime reducer handles the common snapshot/delta text and tool paths, but not the entire Memoh Web client's event/cursor/resync semantics. Do not use it for high-impact Agent operations without verifying reconnection and approval flows.
- This fork launches directly into Memoh4A. The upstream Agora UI is not surfaced in the new navigation.
- Supports username/password auth only; Cloud may require a different sign-in surface.
- Tool details provide bounded raw input/output/diff text, not syntax-highlighted code/diff editors.
- Cloud account-specific identity/login flows have not been confirmed against a live server.

## Contract / next slice

The client treats Memoh as a remote runtime and server-owned session history as the source of truth. On a disconnected socket, **never** infer that a run stopped. `invocation_id` is an idempotency key; `run_id` addresses stop/decisions. A runtime subscription merely observes work and closing it must not abort the work.

The full API reference for the audited backend snapshot is [MEMOH_API_COVERAGE.md](MEMOH_API_COVERAGE.md): 338 documented operations, 260 paths, and all 475 Swagger schema definitions, pinned to `felinics/Memoh` commit `f3e8199560d42109be4b63bfc82ac62f260a66e2`. Routes registered in Go but absent from Swagger are listed separately in [MEMOH_UNDOCUMENTED_ROUTES.md](MEMOH_UNDOCUMENTED_ROUTES.md). The inventory is the backend surface; it does not claim every server/admin endpoint is a mobile user feature. Use it as the coverage ledger while native user flows continue to be implemented.

The Memory manager now calls all 12 bot-scoped Memory operations plus the two team-wide Memory configuration operations directly. Destructive operations require confirmation, and team configuration permissions do not block bot-scoped Memory access.

This is an experimental fork of [newo-ether/Agora](https://github.com/newo-ether/Agora) under GPL-3.0. The existing LICENSE and attribution are preserved. The current environment has no configured Android SDK, so the latest client changes have not been build-verified or live-tested against an authenticated Memoh deployment; complete CI build and live-device authentication/WebSocket verification before release.
