package com.newoether.agora.memoh

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Memoh splits its HTTP surface in two, and the split is not guessable from the product docs.
 * Auth lives under `{origin}/api/v1` (auth, users/me, ws-tickets); the business API lives under
 * `{origin}/api/memoh` (bots, sessions, ...).
 *
 * Verified against https://app.memoh.net: `/api/v1/bots` is 404 while `/api/memoh/bots` answers
 * 401 INVALID_CREDENTIALS without a session, and `POST /api/v1/ws-tickets` is the only way the
 * web client obtains a WebSocket ticket.
 *
 * Self-hosted deployments that expose the API at the root (no `/api` segment) keep working:
 * when the entered URL has no path, the same two suffixes are appended to it.
 */
internal data class MemohEndpoints(val auth: String, val api: String, val origin: String) {

    companion object {
        /** The Cloud product. Its web UI signs in with an emailed code, not a password. */
        const val CLOUD_ORIGIN = "https://app.memoh.net"

        private const val AUTH_SUFFIX = "/api/v1"
        private const val API_SUFFIX = "/api/memoh"

        /**
         * Resolves what the user typed into the address field.
         *
         * Accepts an origin (`https://app.memoh.net`), a pasted API root
         * (`https://app.memoh.net/api`, `https://app.memoh.net/api/memoh`) or a self-hosted root.
         */
        fun fromInput(input: String): MemohEndpoints {
            val trimmed = input.trim().trimEnd('/')
            require(trimmed.isNotEmpty()) { "Enter the Memoh server address" }
            val parsed = trimmed.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid http(s) URL")
            require(parsed.scheme == "https" || (parsed.scheme == "http" && isLocalhost(parsed.host))) {
                "Use HTTPS for remote servers. HTTP is supported only for localhost."
            }
            require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
                "Credentials must not be part of the URL"
            }
            require(parsed.query == null && parsed.fragment == null) {
                "Use an API base URL without a query or fragment"
            }
            val defaultPort = if (parsed.scheme == "https") 443 else 80
            val origin = buildString {
                append(parsed.scheme).append("://").append(parsed.host)
                if (parsed.port != defaultPort) append(':').append(parsed.port)
            }
            val path = parsed.encodedPath.trimEnd('/')
            // A pasted /api or /api/memoh root already names the business API; the auth API always
            // lives at /api/v1 on the same origin.
            val apiBase = when {
                path.isEmpty() || path == "/" -> origin + API_SUFFIX
                path.endsWith("/api") -> "$trimmed/memoh"
                else -> trimmed
            }
            return MemohEndpoints(auth = origin + AUTH_SUFFIX, api = apiBase, origin = origin)
        }

        fun isLocalhost(host: String): Boolean =
            host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"
    }
}

/** How this server expects a user to prove who they are. */
internal enum class MemohLoginMethod { EmailCode, Password, Unknown }

internal data class MemohAuthConfig(
    val method: MemohLoginMethod,
    val oauthProviders: List<String> = emptyList(),
)
