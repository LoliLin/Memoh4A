package com.newoether.agora.memoh

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Resolve server-owned metadata before opening a chat hidden from the paged sidebar. */
internal fun MemohViewModel.openSessionById(sessionId: String): Job? {
    if (sessionId.isBlank()) return null
    val botId = bot?.id ?: return null
    val revision = historyRevision
    val request = ++navigationRevision
    return viewModelScope.launch {
        try {
            val target = workspaceApi.getSession(botId, sessionId)
            if (signedIn && bot?.id == botId && historyRevision == revision && navigationRevision == request) selectSession(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (bot?.id == botId && historyRevision == revision && navigationRevision == request) {
                reportAttachmentError(e.message ?: "Unable to open conversation")
            }
        }
    }
}
