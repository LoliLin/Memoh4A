# Memoh API probe

A read-only local probe that answers what the Android client cannot answer without a live
signed-in session: **where the team list comes from, and what the business API expects for team
scoping** (`X-Team-ID` vs `team_id` vs nothing).

Nothing is written to disk. Nothing is mutated: every request is a `GET`, plus
`POST /api/v1/ws-tickets`, which only mints a short-lived socket credential.

## Why this exists

The client reaches Memoh Cloud but the business API rejects bot listing with
`X-Team-Id header or team_id query is required`. The Cloud web app stores the chosen team in
`localStorage` (`memoh.current_team_id`) and sends it as a header — but *which* header, and which
endpoint yields the team list, is not documented anywhere. The open-source backend does not read
this header at all, so it lives in the closed Cloud gateway. Measuring is the only way to know.

## Getting a session

1. Sign in at <https://app.memoh.net> in Chrome.
2. `F12` → **Network** → click any request to `app.memoh.net`.
3. Right-click → **Copy** → **Copy as cURL**.
4. Save it as `curl.txt` in this folder.

Or skip the file and pass the cookie directly:

```sh
node memoh-probe.mjs --cookie "session=…; other=…"
```

## Running it

```sh
node memoh-probe.mjs
```

## Reading the output

| Line | Meaning |
| --- | --- |
| `auth users/me` | Who the session belongs to. A 401 means the copied cookie expired — copy again. |
| `auth teams` / `business teams` | Which prefix serves the team list, and the shape of a team object (`team_id`, `name`, `role`). |
| `bots …` | The same call under three scoping variants. The one that returns `items` is the contract. |
| `ws ticket` | Whether the socket ticket endpoint accepts this session, and the ticket length. |

Paste the whole output back into the chat; the client is wired to match what it reports.

## Notes

- `curl.txt` contains a live session cookie. It is git-ignored — never commit it.
- The probe does not attempt OAuth. Cloud only accepts same-origin OAuth returns, so a native
  app cannot receive that session; email-code sign-in is the supported native path.
