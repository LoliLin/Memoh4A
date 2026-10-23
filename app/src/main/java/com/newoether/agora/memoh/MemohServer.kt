package com.newoether.agora.memoh

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Official Cloud has separate platform/business roots; OSS preserves its configured API base. */
internal data class MemohEndpoints(val auth: String, val api: String, val origin: String) {

    companion object {
        const val CLOUD_ORIGIN = "https://app.memoh.net"
        private const val AUTH_SUFFIX = "/api/v1"
        private const val API_SUFFIX = "/api/memoh"

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
            val origin = parsed.newBuilder().encodedPath("/").build().toString().trimEnd('/')
            val path = parsed.encodedPath.trimEnd('/')
            // An explicitly pasted business gateway root opts into the same split (also testable locally).
            if (origin == CLOUD_ORIGIN || path == API_SUFFIX) {
                require(path in listOf("", "/api", API_SUFFIX, AUTH_SUFFIX)) { "Enter the Memoh API root" }
                return MemohEndpoints(origin + AUTH_SUFFIX, origin + API_SUFFIX, origin)
            }
            val apiRoot = parsed.toString().trimEnd('/')
            return MemohEndpoints(auth = apiRoot, api = apiRoot, origin = origin)
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
