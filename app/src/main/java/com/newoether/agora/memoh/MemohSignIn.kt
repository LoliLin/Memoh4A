package com.newoether.agora.memoh

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Probes have their own client and cannot reconfigure a live or pending login. */
internal class MemohSignIn(
    private val api: MemohApi,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val form: MemohLoginForm,
    private val onError: (String) -> Unit,
    private val onSignedIn: () -> Unit,
) {
    private var probeJob: Job? = null
    private var probeRevision = 0
    private var authenticated = false

    fun probe(server: String) {
        val address = server.trim()
        if (address.isEmpty() || form.signingIn || form.sendingCode || authenticated) return
        val revision = ++probeRevision
        val attempt = form.revision
        probeJob?.cancel()
        probeJob = scope.launch {
            val probeApi = MemohApi()
            try {
                probeApi.configure(address)
                val config = probeApi.authConfig()
                if (revision == probeRevision && form.isCurrent(attempt) && form.server.trim() == address &&
                    !form.signingIn && !authenticated) {
                    form.method = config.method
                    form.oauthProviders = config.oauthProviders
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Sign-in itself reports connection errors. */ }
            finally { probeApi.clearIdentity() }
        }
    }
    private fun stopProbe() { probeRevision++; probeJob?.cancel(); probeJob = null }

    fun sendCode(email: String) {
        if (form.sendingCode || form.signingIn || authenticated) return
        val address = email.trim()
        if (address.isEmpty()) { onError("Enter the Memoh account email"); return }
        stopProbe()
        val attempt = form.beginAttempt()
        val server = form.server.trim()
        form.sendingCode = true
        scope.launch {
            try {
                api.configure(server)
                api.sendEmailCode(address)
                if (form.isCurrent(attempt)) form.codeSent = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (form.isCurrent(attempt)) onError(e.message ?: "Could not send the code") }
            finally { if (form.isCurrent(attempt)) form.sendingCode = false }
        }
    }
    fun signInWithCode(code: String) {
        val email = form.email.trim()
        val entered = code.trim()
        if (email.isEmpty() || entered.isEmpty()) { onError("Enter the emailed code"); return }
        signIn { api.verifyEmailCode(email, entered); null }
    }
    fun signInWithPassword(account: String, password: String) {
        if (account.isEmpty() || password.isEmpty()) {
            onError("Enter the account email or username and password"); return
        }
        signIn { api.login(account, password) }
    }
    private fun signIn(authenticate: suspend () -> String?) {
        if (form.signingIn || form.sendingCode || authenticated) return
        stopProbe()
        val attempt = form.beginAttempt()
        val server = form.server.trim()
        form.signingIn = true
        onError("")
        scope.launch {
            try {
                val endpoints = MemohEndpoints.fromInput(server)
                if (!form.codeSent || api.scope.identity.endpoints != endpoints) api.configure(endpoints)
                val jwt = authenticate()
                if (!form.isCurrent(attempt)) return@launch
                val edit = prefs.edit().putString("server", server)
                if (jwt == null) edit.remove("jwt") else edit.putString("jwt", MemohTokenStore.seal(jwt))
                edit.apply()
                authenticated = true
                form.password = ""
                if (jwt == null) { form.method = MemohLoginMethod.EmailCode; form.codeSent = true }
                onSignedIn()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (form.isCurrent(attempt)) onError(e.message ?: "Sign-in failed") }
            finally { if (form.isCurrent(attempt)) form.signingIn = false }
        }
    }
    fun signOut() {
        stopProbe(); authenticated = false; form.resetAfterSignOut()
        val finishLogout = api.endSession()
        prefs.edit().remove("jwt").apply()
        scope.launch { finishLogout() }
    }
}
