package com.newoether.agora.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator as CircularProgressIndicator

private class DrawerPointerPosition {
    var value: Offset = Offset.Zero
}

/** Agora's shared 44 dp conversation row, including selection, activity and long-press behavior. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AgoraDrawerConversationRow(
    title: String,
    selected: Boolean,
    indicator: DrawerConversationIndicator,
    enabled: Boolean = true,
    contextMenuVisible: Boolean = false,
    unreadDescription: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (DpOffset) -> Unit,
) {
    val haptics = LocalAgoraHaptics.current
    val density = LocalDensity.current
    val pointerPosition = remember { DrawerPointerPosition() }
    val selectionContainerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "drawerConversationSelectionContainer",
    )
    val selectionContentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 250),
        label = "drawerConversationSelectionContent",
    )
    val selectionIndicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 250),
        label = "drawerConversationSelectionIndicator",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(vertical = 2.dp)
            .clip(CircleShape)
            .then(
                Modifier.pointerInput(contextMenuVisible) {
                    if (!contextMenuVisible) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.firstOrNull()?.position?.let { pointerPosition.value = it }
                            }
                        }
                    }
                }
            )
            .combinedClickable(
                enabled = enabled,
                hapticFeedbackEnabled = false,
                onClick = onClick,
                onLongClick = {
                    haptics.longPress()
                    val position = pointerPosition.value
                    onLongClick(
                        with(density) {
                            DpOffset(
                                x = position.x.toDp().coerceIn(16.dp, 200.dp),
                                y = position.y.toDp() - 28.dp,
                            )
                        },
                    )
                },
            ),
        color = selectionContainerColor,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(
                targetState = title,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                modifier = Modifier.weight(1f),
                label = "DrawerConversationTitleCrossfade",
            ) { visibleTitle ->
                Text(
                    text = visibleTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = selectionContentColor,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = indicator == DrawerConversationIndicator.GENERATING,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = selectionIndicatorColor,
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = indicator == DrawerConversationIndicator.UNREAD,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    val dotModifier = Modifier.size(8.dp).background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
                    Box(
                        modifier = if (unreadDescription == null) dotModifier else {
                            dotModifier.semantics { contentDescription = unreadDescription }
                        },
                    )
                }
            }
        }
    }
}
