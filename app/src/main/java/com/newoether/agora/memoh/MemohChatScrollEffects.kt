package com.newoether.agora.memoh

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.ui.chat.ChatScrollCoordinator
import com.newoether.agora.ui.chat.isActive
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import com.newoether.agora.viewmodel.ScrollRequestCoordinator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Memoh events drive Agora's scroll actor; search and a reader's drag retain priority. */
@Composable
internal fun MemohChatScrollEffects(
    coordinator: ChatScrollCoordinator,
    sessionId: String?,
    loadedHistorySessionId: String?,
    messages: State<List<ChatMessage>>,
    pendingUserText: String?,
    running: Boolean,
    searchActive: Boolean,
    stickToBottom: Boolean,
    density: Density,
    bottomBarHeight: Dp,
    motion: AgoraMotionPolicy,
) {
    val requests = remember(sessionId) { ScrollRequestCoordinator() }
    val request by requests.request.collectAsState()
    coordinator.BindRequestEffects(
        currentConversationId = sessionId,
        isNewChatMode = sessionId == null,
        isLoading = running,
        isStopping = false,
        isSwitching = sessionId != null && loadedHistorySessionId != sessionId,
        conversationSearchActive = searchActive,
        shareSelectionActive = false,
        regenerationTransition = null,
        animatedScrollRequest = request,
        messages = messages,
        density = density,
        motionPolicy = motion,
        bottomBarHeight = bottomBarHeight,
        shareSelectionBarSpace = 0.dp,
        onAnimatedScrollFinished = requests::complete,
    )
    LaunchedEffect(searchActive) {
        if (searchActive) requests.clear()
    }
    LaunchedEffect(sessionId) {
        coordinator.listState.interactionSource.interactions
            .filterIsInstance<DragInteraction.Start>().collect { requests.clear() }
    }

    var opened by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(sessionId, loadedHistorySessionId, searchActive) {
        if (sessionId == null || loadedHistorySessionId != sessionId ||
            opened || searchActive || messages.value.isEmpty()
        ) return@LaunchedEffect
        opened = true
        // Opening history settles at the real bottom sentinel, without animating through every
        // turn. A finger taking over cancels the settling loop rather than fighting it.
        coroutineScope {
            val positioning = launch { coordinator.settleOpenedConversation(messages) }
            val userTakeover = launch {
                coordinator.listState.interactionSource.interactions
                    .filterIsInstance<DragInteraction.Start>().first()
                positioning.cancel()
            }
            try {
                positioning.join()
            } finally {
                userTakeover.cancel()
            }
        }
    }

    LaunchedEffect(sessionId, loadedHistorySessionId, searchActive, stickToBottom) {
        if (sessionId == null || loadedHistorySessionId != sessionId || searchActive || !stickToBottom) {
            return@LaunchedEffect
        }
        var previousTailId = messages.value.lastOrNull()?.id
        var wasAttached = coordinator.isWithinAbsoluteBottomAttachThreshold
        snapshotFlow {
            Triple(
                messages.value.lastOrNull()?.id,
                coordinator.isWithinAbsoluteBottomAttachThreshold,
                coordinator.absoluteBottomScrollPhase.isActive || request != null,
            )
        }.collect { (tailId, attached, busy) ->
            if (tailId != previousTailId) {
                val pendingSendHandoff = request?.targetMessageId == MEMOH_PENDING_MESSAGE_ID
                if (previousTailId != null && tailId != null && tailId != MEMOH_PENDING_MESSAGE_ID &&
                    opened && (wasAttached || pendingSendHandoff)
                ) {
                    // Keep the last settled attachment decision when a new turn changes layout.
                    // Requests wait for its committed row; a drag cancels that wait and the actor.
                    requests.requestAbsoluteBottomAfter(sessionId, tailId)
                }
                previousTailId = tailId
            } else if (!busy) {
                wasAttached = attached
            }
        }
    }
    LaunchedEffect(sessionId, pendingUserText) {
        if (sessionId != null && pendingUserText != null && !searchActive) {
            requests.requestAbsoluteBottomAfter(sessionId, MEMOH_PENDING_MESSAGE_ID)
        }
    }
}
