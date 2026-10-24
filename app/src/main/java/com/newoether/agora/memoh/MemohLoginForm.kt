package com.newoether.agora.memoh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Sign-in form and attempt revisions, independent of the authenticated workspace. */
internal class MemohLoginForm(initialServer: String) {
    var server by mutableStateOf(initialServer)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var email by mutableStateOf("")
    var code by mutableStateOf("")

    val emailFlow: Boolean get() = method == MemohLoginMethod.EmailCode

    var method by mutableStateOf(MemohLoginMethod.Unknown)
        internal set
    var oauthProviders by mutableStateOf<List<String>>(emptyList())
        internal set
    var codeSent by mutableStateOf(false)
        internal set
    var sendingCode by mutableStateOf(false)
        internal set

    var signingIn by mutableStateOf(false)
        internal set
    /** Bumped whenever the session generation changes so a late response cannot sign in. */
    internal var revision = 0
        private set

    fun beginAttempt(): Int = ++revision

    fun isCurrent(attempt: Int): Boolean = attempt == revision

    fun resetAfterSignOut() {
        revision++
        signingIn = false; sendingCode = false
        codeSent = false
        code = ""
        password = ""
    }
}
