package com.newoether.agora.memoh

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sign-in flows kept out of [MemohViewModel] so that file stays under the project's 800-line
 * limit. None of this touches conversation state; the caller decides what happens once a
 * session exists.
 */
internal class MemohSignIn(
    private val api: MemohApi,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val form: MemohLoginForm,
    private val onError: (String) -> Unit,
    private val onSignedIn: () -> Unit,
) {
    /**
     * Reads the server's sign-in method so the form matches what that deployment accepts.
     *
     * Cloud answers "email"; a password deployment answers "password". A failure here is silent:
     * the user sees the real error when they attempt the sign-in.
     */
    fun probe(server: String) {
        val address = server.trim()
        if (address.isEmpty()) return
        scope.launch {
            runCatching {
                api.configure(address)
                api.authConfig()
            }.onSuccess { config ->
                form.method = config.method
                form.oauthProviders = config.oauthProviders
            }
        }
    }

    fun sendCode(email: String) {
        if (form.sendingCode) return
        val address = email.trim()
        if (address.isEmpty()) {
            onError("Enter the Memoh account email")
            return
        }
        form.sendingCode = true
        scope.launch {
            try {
                api.configure(form.server.trim())
                api.sendEmailCode(address)
                form.codeSent = true
            } catch (t: Exception) {
                form.codeSent = false
                onError(t.message ?: "Could not send the code")
            } finally {
                form.sendingCode = false
            }
        }
    }

    fun signInWithCode(code: String) {
        val address = form.email.trim()
        val entered = code.trim()
        if (address.isEmpty() || entered.isEmpty()) {
            onError("Enter the emailed code")
            return
        }
        val attempt = form.beginAttempt()
        scope.launch {
            try {
                api.configure(form.server.trim())
                api.verifyEmailCode(address, entered)
                if (!form.isCurrent(attempt)) return@launch
                withContext(Dispatchers.IO) {
                    prefs.edit().putString("server", form.server.trim()).apply()
                }
                form.method = MemohLoginMethod.EmailCode
                form.codeSent = true
                onSignedIn()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                if (form.isCurrent(attempt)) onError(t.message ?: "Sign-in failed")
            }
        }
    }

    fun signInWithPassword(account: String, password: String) {
        if (account.isEmpty() || password.isEmpty()) {
            onError("Enter the account email or username and password")
            return
        }
        val attempt = form.beginAttempt()
        scope.launch {
            try {
                api.configure(form.server.trim())
                val jwt = api.login(account, password)
                if (!form.isCurrent(attempt)) return@launch
                withContext(Dispatchers.IO) {
                    prefs.edit().putString("server", form.server.trim())
                        .putString("jwt", MemohTokenStore.seal(jwt)).apply()
                }
                form.password = ""
                onSignedIn()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                if (form.isCurrent(attempt)) onError(t.message ?: "Connection failed")
            }
        }
    }

    /** Drops the local session, then best-effort ends a cookie session on the server. */
    fun signOut() {
        form.resetAfterSignOut()
        scope.launch {
            runCatching { api.logoutSession() }
            withContext(Dispatchers.IO) { prefs.edit().remove("jwt").apply() }
        }
    }
}
