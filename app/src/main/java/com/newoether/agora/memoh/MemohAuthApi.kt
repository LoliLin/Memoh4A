package com.newoether.agora.memoh

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

internal suspend fun MemohApi.authConfig(): MemohAuthConfig {
    suspend fun optional(path: String): JSONObject = try { authRequest(path) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { JSONObject() }
    val method = optional("/auth/config").optString("login_method")
    val payload = optional("/auth/oauth-providers?origin=${Uri.encode(MemohEndpoints.CLOUD_ORIGIN)}")
    val array = payload.optJSONArray("providers") ?: payload.optJSONArray("items")
    val providers = array?.let { entries -> (0 until entries.length()).mapNotNull { index ->
        when (val entry = entries.opt(index)) {
            is String -> entry.takeIf(String::isNotBlank)
            is JSONObject -> entry.optString("provider").takeIf(String::isNotBlank)
            else -> null
        }
    } }.orEmpty()
    return MemohAuthConfig(when (method) {
        "email" -> MemohLoginMethod.EmailCode
        "password" -> MemohLoginMethod.Password
        else -> MemohLoginMethod.Unknown
    }, providers)
}

internal suspend fun MemohApi.sendEmailCode(email: String) {
    val address = email.trim()
    require(address.contains('@')) { "Enter the email address for your Memoh account" }
    authRequest("/auth/email-code/send", JSONObject().put("email", address).put("preferred_locale", ""))
}

internal suspend fun MemohApi.verifyEmailCode(email: String, code: String) {
    val captured = scope
    require(code.trim().isNotEmpty()) { "Enter the code from the email" }
    authRequest("/auth/email-code/verify", JSONObject().put("email", email.trim()).put("code", code.trim()))
    acceptBearer(captured, "")
    if (!hasSession(captured)) throw IOException("Memoh accepted the code but returned no session. Try signing in again.")
    val profile = platformProfile()
    check(platformUserId(profile).isNotBlank()) { "Memoh returned no authenticated platform identity" }
    acceptCookieSession(captured)
}

internal suspend fun MemohApi.login(username: String, password: String): String {
    val captured = scope
    val accountKey = if (captured.identity.endpoints.auth != captured.identity.endpoints.api && username.contains('@')) "email" else "username"
    val body = JSONObject().put(accountKey, username.trim())
        .put("password", password)
    val result = authRequest("/auth/login", body).optString("access_token")
    if (result.isBlank()) throw IOException("Memoh did not return an access token")
    acceptBearer(captured, result)
    return result
}

/** Local invalidation is synchronous; the old logout can only use the old identity's cookies. */
internal fun MemohApi.endSession(): suspend () -> Unit {
    val old = scope.identity
    clearIdentity()
    return {
        withContext(Dispatchers.IO) {
            val client = old.client.newBuilder().apply { interceptors().clear() }.build()
            try {
                client.newCall(Request.Builder().url(old.endpoints.auth + "/auth/logout")
                    .post(ByteArray(0).toRequestBody()).build()).execute().close()
            } catch (_: IOException) { /* Local credentials are already gone. */ }
        }
    }
}

internal suspend fun MemohApi.logoutSession() = endSession().invoke()

/** True when a cookie survived from the previous process and has not expired locally. */
internal fun MemohApi.hasSessionCookie(): Boolean = scope.identity.cookies.hasSession()

/**
 * Confirms the restored cookie is still accepted. Any failure (401, transport) means the caller
 * must sign in again rather than assume the session is alive.
 */
internal suspend fun MemohApi.probeIdentity(): Boolean = runCatching { account() }.isSuccess

/** Drops a cookie the server no longer accepts so the next start does not retry it. */
internal fun MemohApi.clearSession() {
    scope.identity.cookies.clear()
    scope.identity.cookieAuthenticated = false
}
