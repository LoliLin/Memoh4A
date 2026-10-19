# Memoh4A: native Memoh client (preview)

This patch adds a **separate Android-native Memoh chat entry** to the existing Agora application. It deliberately does not turn Memoh into an OpenAI-compatible model provider, intercept Agora's local Room conversation graph, or reimplement Memoh's server-side agent runtime.

## Install

Build the integrated source from `LoliLin/Memoh4A`:

```sh
./gradlew :app:assembleFdroidDebug
```

The fork has one Memoh4A launcher and its own Android application ID `dev.lolilin.memoh4a`. The upstream Agora MainActivity and local conversation store remain untouched and internal. No signed APK is included.

Use a Memoh API base URL, not just its website URL. For Memoh Cloud the default is `https://app.memoh.net/api`; if your self-hosted server exposes API at its root (for example on port 8080), use that direct root URL instead of appending `/api`. Remote connections require HTTPS. Localhost may use HTTP.

Log in using the Memoh server's username/password API. The Bearer JWT is stored using an AndroidKeyStore AES-GCM envelope; the password isn't persisted. A 401 during initial bot loading signs out the client. Password reset, registration, and Cloud-specific SSO are not implemented here.

## Implemented

- Native Compose chat surface and multi-bot picker; conversation drawer with remote session history
- In-band first-message session creation; client-minted `invocation_id` for replay-safe sends
- WebSocket `/bots/{bot_id}/web/ws` with a bearer-authenticated handshake, runtime subscribe/unsubscribe, reconnect and admission replay
- Runtime snapshot/delta text, reasoning and tool-status rendering, stop action and explicit permission choices
- Server-backed JSON history and session list, Markdown replies, optimistic pending user text
- Original Agora conversations/settings unchanged

## Gaps before daily use

This is **source-only and not build-verified or live-tested against an authenticated Memoh deployment**. It is a first integration slice, not a polished replacement for the full Memoh Web UI. In particular:

- Message history is limited to the newest 100 API rows (no older-page pagination yet).
- Uploads, images, structured attachments, workspace file explorer, terminal, audio, schedules, slash commands, steering/follow-up queues, external-agent setup and user-input forms are not implemented.
- The runtime reducer handles the common snapshot/delta text and tool paths, but not the entire Memoh Web client's event/cursor/resync semantics. Do not use it for high-impact Agent operations without verifying reconnection and approval flows.
- This fork launches directly into Memoh4A. The upstream Agora UI is not surfaced in the new navigation.
- Supports username/password auth only; Cloud may require a different sign-in surface.
- Tool output detail and code/diff viewers are intentionally deferred.

## Contract / next slice

Memoh API endpoints were checked against `felinics/Memoh` handlers and `apps/web/src/composables/api/useChat.ws.ts`, as observed on 2026-10-19. Remote server sessions are the source of truth; on a disconnected socket, **never** infer that a run stopped. `invocation_id` is an idempotency key; `run_id` addresses stop/decisions. A runtime subscription merely observes work and closing it must not abort the work.

This is an experimental fork of [newo-ether/Agora](https://github.com/newo-ether/Agora) under GPL-3.0. The existing LICENSE and attribution are preserved. Complete CI build verification and live-device authentication/WebSocket testing before any release.
