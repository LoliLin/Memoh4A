package com.newoether.agora.memoh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the sign-in screen has typed into it. Kept out of [MemohViewModel] because none of it
 * affects a signed-in session, and the ViewModel has a hard 800-line budget.
 */
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

    /** Bumped whenever the session generation changes so a late response cannot sign in. */
    internal var revision = 0
        private set

    fun beginAttempt(): Int = ++revision

    fun isCurrent(attempt: Int): Boolean = attempt == revision

    fun resetAfterSignOut() {
        revision++
        codeSent = false
        code = ""
        password = ""
    }
}
