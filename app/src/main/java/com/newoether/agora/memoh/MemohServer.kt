package com.newoether.agora.memoh

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Official Cloud has separate platform/business roots; OSS preserves its configured API base. */
internal data class MemohEndpoints(val auth: String, val api: String, val origin: String) {

    companion object {
        const val CLOUD_ORIGIN = "https://app.memoh.net"
        private const val AUTH_SUFFIX = "/api/v1"
        private const val API_SUFFIX = "/api/memoh"

        /**
         * [messages] supplies the localized validation text; this class has no Context. The
         * default keeps the type usable from plain unit tests.
         */
        fun fromInput(
            input: String,
            messages: MemohServerMessages = MemohServerMessages.English,
        ): MemohEndpoints {
            val trimmed = input.trim().trimEnd('/')
            require(trimmed.isNotEmpty()) { messages.emptyAddress }
            val parsed = trimmed.toHttpUrlOrNull()
                ?: throw IllegalArgumentException(messages.invalidUrl)
            require(parsed.scheme == "https" || (parsed.scheme == "http" && isLocalhost(parsed.host))) {
                messages.httpsRequired
            }
            require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
                messages.credentialsInUrl
            }
            require(parsed.query == null && parsed.fragment == null) {
                messages.urlShape
            }
            val origin = parsed.newBuilder().encodedPath("/").build().toString().trimEnd('/')
            val path = parsed.encodedPath.trimEnd('/')
            // An explicitly pasted business gateway root opts into the same split (also testable locally).
            if (origin == CLOUD_ORIGIN || path == API_SUFFIX) {
                require(path in listOf("", "/api", API_SUFFIX, AUTH_SUFFIX)) { messages.apiRoot }
                return MemohEndpoints(origin + AUTH_SUFFIX, origin + API_SUFFIX, origin)
            }
            val apiRoot = parsed.toString().trimEnd('/')
            return MemohEndpoints(auth = apiRoot, api = apiRoot, origin = origin)
        }

        fun isLocalhost(host: String): Boolean =
            host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"
    }
}

/**
 * Validation copy for [MemohEndpoints.fromInput]. The parsing lives outside the Compose tree, so
 * the caller injects localized text instead of this file reaching for a Context.
 */
internal data class MemohServerMessages(
    val emptyAddress: String,
    val invalidUrl: String,
    val httpsRequired: String,
    val credentialsInUrl: String,
    val urlShape: String,
    val apiRoot: String,
) {
    companion object {
        val English = MemohServerMessages(
            emptyAddress = "Enter the Memoh server address",
            invalidUrl = "Enter a valid http(s) URL",
            httpsRequired = "Use HTTPS for remote servers. HTTP is supported only for localhost.",
            credentialsInUrl = "Credentials must not be part of the URL",
            urlShape = "Use an API base URL without a query or fragment",
            apiRoot = "Enter the Memoh API root",
        )

        /** Built from resources; [MemohViewModel] passes this so users see their own language. */
        fun from(text: (Int) -> String) = MemohServerMessages(
            emptyAddress = text(com.newoether.agora.R.string.memoh_error_server_address),
            invalidUrl = text(com.newoether.agora.R.string.memoh_error_invalid_url),
            httpsRequired = text(com.newoether.agora.R.string.memoh_error_url_https),
            credentialsInUrl = text(com.newoether.agora.R.string.memoh_error_url_credentials),
            urlShape = text(com.newoether.agora.R.string.memoh_error_url_shape),
            apiRoot = text(com.newoether.agora.R.string.memoh_error_api_root),
        )
    }
}

/** How this server expects a user to prove who they are. */
internal enum class MemohLoginMethod { EmailCode, Password, Unknown }

internal data class MemohAuthConfig(
    val method: MemohLoginMethod,
    val oauthProviders: List<String> = emptyList(),
)
