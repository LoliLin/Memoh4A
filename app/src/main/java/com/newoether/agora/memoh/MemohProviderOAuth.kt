package com.newoether.agora.memoh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsItem
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class MemohProviderOAuthDevice(
    val pending: Boolean,
    val code: String,
    val verificationUri: String,
    val expiresAt: String,
    val intervalSeconds: Long,
)

internal data class MemohProviderOAuthStatus(
    val configured: Boolean,
    val hasToken: Boolean,
    val expired: Boolean,
    val device: MemohProviderOAuthDevice?,
    val account: String,
)

internal fun decodeProviderOAuthDevice(obj: JSONObject?): MemohProviderOAuthDevice? {
    if (obj == null) return null
    return MemohProviderOAuthDevice(
        pending = obj.optBoolean("pending"),
        code = obj.optString("user_code"),
        verificationUri = obj.optString("verification_uri"),
        expiresAt = obj.optString("expires_at").takeUnless { it == "null" }.orEmpty(),
        intervalSeconds = obj.optLong("interval_seconds", 5).coerceIn(1, 86400),
    )
}

internal fun decodeProviderOAuthStatus(obj: JSONObject): MemohProviderOAuthStatus {
    val account = obj.optJSONObject("account")
    val label = listOf("email", "label", "name").firstNotNullOfOrNull { key ->
        account?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
    }.orEmpty()
    val login = account?.optString("login")?.takeIf { it.isNotBlank() && it != "null" }
    return MemohProviderOAuthStatus(
        configured = obj.optBoolean("configured"),
        hasToken = obj.optBoolean("has_token"),
        expired = obj.optBoolean("expired"),
        device = decodeProviderOAuthDevice(obj.optJSONObject("device")),
        account = listOfNotNull(label.takeIf(String::isNotBlank), login?.let { "@$it" }).joinToString(" · "),
    )
}

internal fun memohProviderOAuthVerificationUrl(value: String): Boolean = runCatching {
    val url = URI(value)
    url.scheme?.lowercase() in setOf("http", "https") && !url.host.isNullOrBlank()
}.getOrDefault(false)

@Composable
internal fun MemohProviderOAuthSection(
    admin: MemohAdminApi,
    provider: MemohProvider,
    canManage: Boolean,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = context.resources
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var status by remember(provider.id, admin) { mutableStateOf<MemohProviderOAuthStatus?>(null) }
    var loading by remember(provider.id, admin) { mutableStateOf(true) }
    var busy by remember(provider.id, admin) { mutableStateOf(false) }
    var error by remember(provider.id, admin) { mutableStateOf("") }
    var message by remember(provider.id, admin) { mutableStateOf("") }
    var ignorePending by remember(provider.id, admin) { mutableStateOf(false) }
    var retryLoad by remember(provider.id, admin) { mutableIntStateOf(0) }
    var retryPoll by remember(provider.id, admin) { mutableIntStateOf(0) }
    var confirmRevoke by remember(provider.id, admin) { mutableStateOf(false) }
    val device = status?.takeUnless { it.hasToken }?.device?.takeIf {
        it.pending && it.code.isNotBlank() && it.verificationUri.isNotBlank() && !ignorePending
    }
    val connected = status?.let { it.hasToken && !it.expired } == true

    LaunchedEffect(admin, provider.id, retryLoad) {
        loading = true
        error = ""
        try {
            status = decodeProviderOAuthStatus(admin.providerOAuthStatus(provider.id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: resources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(admin, provider.id, device?.code, ignorePending, retryPoll) {
        var current = device ?: return@LaunchedEffect
        if (connected) return@LaunchedEffect
        while (current.pending) {
            delay(current.intervalSeconds * 1000)
            try {
                val next = decodeProviderOAuthStatus(admin.providerOAuthPoll(provider.id))
                status = next
                if (next.hasToken && !next.expired) {
                    error = ""
                    message = resources.getString(R.string.memoh_ui_oauth_connected)
                    // Authorization is already complete. A failed catalog refresh must never restart it.
                    scope.launch {
                        try {
                            val imported = admin.importProviderModels(provider.id)
                            message = resources.getString(R.string.memoh_ui_model_import_complete,
                                imported.optInt("created"), imported.optInt("updated"), imported.optInt("skipped"))
                            onChanged()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            message = resources.getString(R.string.memoh_ui_oauth_import_failed,
                                failure.message ?: resources.getString(R.string.memoh_ui_unknown_error))
                        }
                    }
                    return@LaunchedEffect
                }
                current = next.device ?: run {
                    error = resources.getString(R.string.memoh_ui_oauth_ended)
                    return@LaunchedEffect
                }
                if (!current.pending) {
                    error = resources.getString(R.string.memoh_ui_oauth_ended)
                    return@LaunchedEffect
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: resources.getString(R.string.memoh_ui_unknown_error)
                return@LaunchedEffect
            }
        }
    }

    var remaining by remember(device?.expiresAt) { mutableStateOf<Long?>(null) }
    LaunchedEffect(device?.expiresAt) {
        val expiry = runCatching { Instant.parse(device?.expiresAt.orEmpty()).toEpochMilli() }.getOrNull()
            ?: return@LaunchedEffect
        while (true) {
            val seconds = ((expiry - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            remaining = seconds
            if (seconds == 0L) break
            delay(1000)
        }
    }

    SettingsGroup(
        title = resources.getString(R.string.memoh_ui_oauth_account),
        items = buildList {
            add {
                SettingsItem(
                    headlineContent = {
                        Text(if (provider.clientType == "github-copilot") "GitHub" else "ChatGPT")
                    },
                    supportingContent = {
                        val description = when {
                            loading -> resources.getString(R.string.memoh_ui_loading)
                            status == null -> error
                            status?.configured == false -> resources.getString(R.string.memoh_ui_oauth_not_configured)
                            connected -> status?.account?.ifBlank { resources.getString(R.string.memoh_ui_oauth_connected) }.orEmpty()
                            status?.expired == true -> resources.getString(R.string.memoh_ui_oauth_expired)
                            else -> resources.getString(R.string.memoh_ui_oauth_connect_hint)
                        }
                        Text(description)
                    },
                    trailingContent = {
                        when {
                            loading || busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            status == null -> TextButton(onClick = { retryLoad++ }) { Text(resources.getString(R.string.memoh_ui_retry)) }
                            canManage && connected -> TextButton(onClick = { confirmRevoke = true }) {
                                Text(resources.getString(R.string.memoh_ui_oauth_disconnect))
                            }
                            canManage && status?.configured == true -> TextButton(onClick = {
                                if (device != null) {
                                    ignorePending = true
                                    error = ""
                                } else {
                                    busy = true
                                    error = ""
                                    message = ""
                                    scope.launch {
                                        try {
                                            val response = admin.providerOAuthAuthorize(provider.id)
                                            val authorizedDevice = decodeProviderOAuthDevice(response.optJSONObject("device"))
                                            if (response.optString("mode") != "device" || authorizedDevice == null ||
                                                !authorizedDevice.pending || authorizedDevice.code.isBlank() ||
                                                !memohProviderOAuthVerificationUrl(authorizedDevice.verificationUri)) {
                                                error = resources.getString(R.string.memoh_ui_oauth_invalid_response)
                                            } else {
                                                ignorePending = false
                                                status = MemohProviderOAuthStatus(true, false, false, authorizedDevice, "")
                                                retryPoll++
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error = failure.message ?: resources.getString(R.string.memoh_ui_unknown_error)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }) {
                                Text(resources.getString(if (device != null) R.string.memoh_ui_cancel else R.string.memoh_ui_oauth_connect))
                            }
                        }
                    },
                )
            }
            if (device != null) add {
                SettingsItem(
                    headlineContent = { Text(device.code, style = MaterialTheme.typography.titleLarge) },
                    supportingContent = {
                        Column {
                            Text(device.verificationUri)
                            remaining?.let {
                                Text(resources.getString(R.string.memoh_ui_oauth_time_left, it / 60, it % 60))
                            }
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            try {
                                require(memohProviderOAuthVerificationUrl(device.verificationUri))
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Code", device.code))
                                uriHandler.openUri(device.verificationUri)
                            } catch (_: Exception) {
                                error = resources.getString(R.string.memoh_ui_oauth_open_failed)
                            }
                        }) { Text(resources.getString(R.string.memoh_ui_oauth_copy_open)) }
                    },
                )
            }
            if (message.isNotBlank() || (error.isNotBlank() && status != null)) add {
                SettingsItem(
                    headlineContent = {
                        Text(error.ifBlank { message }, color = if (error.isNotBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        if (error.isNotBlank() && device != null) {
                            TextButton(onClick = { error = ""; retryPoll++ }) {
                                Text(resources.getString(R.string.memoh_ui_retry))
                            }
                        }
                    },
                )
            }
        },
    )
    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmRevoke = false },
            title = { Text(resources.getString(R.string.memoh_ui_oauth_disconnect)) },
            text = { Text(resources.getString(R.string.memoh_ui_oauth_disconnect_confirm)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    error = ""
                    scope.launch {
                        try {
                            admin.revokeProviderTokens(provider.id)
                            status = decodeProviderOAuthStatus(admin.providerOAuthStatus(provider.id))
                            message = ""
                            confirmRevoke = false
                            onChanged()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            confirmRevoke = false
                            error = failure.message ?: resources.getString(R.string.memoh_ui_unknown_error)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(resources.getString(R.string.memoh_ui_oauth_disconnect)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmRevoke = false }) {
                    Text(resources.getString(R.string.memoh_ui_cancel))
                }
            },
        )
    }
}
