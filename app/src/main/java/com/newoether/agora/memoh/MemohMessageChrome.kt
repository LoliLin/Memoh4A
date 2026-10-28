package com.newoether.agora.memoh

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import com.newoether.agora.R

/**
 * Bot 头像复用 Agora 共享头像组件的圆角形态；私有头像 URL 只在服务器源内加载，
 * 未配置头像时回退 Bot 名首字母。
 */
@Composable
internal fun MemohBotAvatar(bot: MemohBot?, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val title = bot?.title.orEmpty().ifBlank { "M" }
    val showLetter = bot?.avatarUrl.isNullOrBlank()
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!showLetter) {
            // The initial stays underneath the image: a failed load then degrades to it
            // instead of leaving an empty circle.
            MemohAvatarImage(
                url = bot.avatarUrl,
                contentDescription = title,
                modifier = Modifier.size(size),
            )
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The signed-in client, so avatar requests can carry the session that owns them. */
internal val LocalMemohApi = androidx.compose.runtime.staticCompositionLocalOf<MemohApi?> { null }

/**
 * Server avatar URLs can be same-origin private files, so the request must carry the session
 * allowed to read them. Coil's default client sends none, which is why a configured avatar
 * rendered as blank while the same URL opened in a browser.
 */
@Composable
internal fun MemohAvatarImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    api: MemohApi? = LocalMemohApi.current,
) {
    val context = LocalContext.current
    // Absolute URLs keep their own origin; a path is resolved against the configured server.
    val resolved = remember(url, api?.authUrl) {
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else api?.authUrl?.trimEnd('/')?.let { it + "/" + url.trimStart('/') } ?: url
    }
    // The process image loader resolves the active identity's OkHttp client per request, so
    // this carries the same cookie and bearer the API does. See MemohApplication.
    if (api != null) {
        remember(api) {
            MemohImageHttp.install { runCatching { api.scope.identity.client }.getOrNull() }
            true
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolved)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
internal fun MemohBotAvatarHeader(bot: MemohBot?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MemohBotAvatar(bot, size = 22.dp)
    }
}

/**
 * Bot 的 ask 表单与工具审批属于产生它的那一行：从服务器 turn 里找回交互块，
 * 挂在回答下方而不是聊天输入框，避免切走会话后表单仍在屏幕上。
 */
@Composable
internal fun MemohInteractiveBlocksFooter(vm: MemohViewModel, message: com.newoether.agora.model.ChatMessage) {
    val blocks = remember(message.id, vm.turns, vm.liveBlocks) {
        val history = vm.turns.firstOrNull { memohMessageId(it, 0) == message.id }?.blocks.orEmpty()
        val live = if (message.id == MEMOH_LIVE_MESSAGE_ID) vm.liveBlocks else emptyList()
        (history + live).filter {
            (it.canApprove && it.approvalOptions.isNotEmpty()) || (it.canRespond && it.questions.isNotEmpty())
        }
    }
    if (blocks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        blocks.forEach { block ->
            if (block.canApprove && block.approvalOptions.isNotEmpty()) MemohApprovalActions(block, vm)
            if (block.canRespond && block.questions.isNotEmpty()) MemohAskCard(block, vm)
        }
    }
}

/**
 * 进行中的 run 不在列表里（只有无载荷存根），所以它的 ask/审批必须单独渲染，
 * 否则用户会看到「等待你的回答」却没有任何可点的控件。
 */
@Composable
internal fun MemohLiveInteractions(vm: MemohViewModel) {
    val blocks = remember(vm.liveBlocks) {
        vm.liveBlocks.filter {
            (it.canApprove && it.approvalOptions.isNotEmpty()) || (it.canRespond && it.questions.isNotEmpty())
        }
    }
    if (blocks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        blocks.forEach { block ->
            if (block.canApprove && block.approvalOptions.isNotEmpty()) MemohApprovalActions(block, vm)
            if (block.canRespond && block.questions.isNotEmpty()) MemohAskCard(block, vm)
        }
    }
}
