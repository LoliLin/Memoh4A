package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One installable registry entry; keeps the identifiers the install endpoint needs. */
internal data class MemohMarketApp(
    val registryId: String,
    val appId: String,
    val name: String,
    val description: String,
    val version: String,
    val revision: String,
)

internal fun decodeMarketApps(response: JSONObject): List<MemohMarketApp> {
    val array = response.optJSONArray("data") ?: response.optJSONArray("items") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val appId = item.optString("app_id")
        val registryId = item.optString("registry_id")
        if (appId.isBlank() || registryId.isBlank()) return@mapNotNull null
        MemohMarketApp(
            registryId = registryId,
            appId = appId,
            name = item.optString("name").ifBlank { appId },
            description = item.optString("description"),
            version = item.optString("version"),
            revision = item.optString("revision"),
        )
    }
}

private fun JSONObject.nameOf(): String =
    optString("display_name").ifBlank { optString("name").ifBlank { optString("app_id") } }

/**
 * Market + installed Apps, the two web-only admin surfaces a phone client still needs:
 * browsing what a Bot can install and removing an installation that broke.
 */
@Composable
internal fun MemohMarketDialog(api: MemohApi, botId: String, onDismiss: () -> Unit) {
    val uiResources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val admin = remember(api) { MemohAdminApi(api) }
    var installed by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var available by remember { mutableStateOf<List<MemohMarketApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        error = ""
        try {
            val rowList = admin.installedApps(botId).optJSONArray("items") ?: org.json.JSONArray()
            installed = (0 until rowList.length()).mapNotNull { rowList.optJSONObject(it) }
            available = decodeMarketApps(admin.supermarketApps())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(botId) { reload() }

    AlertDialog(
        onDismissRequest = { if (busy.isBlank()) onDismiss() },
        title = { Text(uiResources.getString(R.string.memoh_ui_market)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Text(
                            uiResources.getString(R.string.memoh_ui_installed_apps),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (installed.isEmpty() && !loading) {
                        item {
                            Text(
                                uiResources.getString(R.string.memoh_ui_no_apps_installed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(installed, key = { it.optString("installation_id").ifBlank { it.nameOf() } }) { row ->
                        val installationId = row.optString("installation_id")
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.nameOf(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(row.optString("status"), row.optString("version"))
                                        .filter(String::isNotBlank).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (installationId.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        busy = installationId
                                        scope.launch {
                                            try {
                                                admin.removeApp(botId, installationId)
                                                reload()
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
                                            } finally {
                                                busy = ""
                                            }
                                        }
                                    },
                                    enabled = busy.isBlank(),
                                ) {
                                    Text(uiResources.getString(R.string.memoh_ui_remove))
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            uiResources.getString(R.string.memoh_ui_available_in_market),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(available, key = { "${it.registryId}/${it.appId}" }) { app ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (app.description.isNotBlank()) {
                                    Text(
                                        app.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val installedAlready = installed.any { it.optString("app_id") == app.appId }
                            TextButton(
                                onClick = {
                                    busy = app.appId
                                    scope.launch {
                                        try {
                                            admin.installApp(botId, app.registryId, app.appId, app.revision)
                                            reload()
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error = failure.message ?: uiResources.getString(R.string.memoh_ui_unknown_error)
                                        } finally {
                                            busy = ""
                                        }
                                    }
                                },
                                enabled = busy.isBlank() && !installedAlready,
                            ) {
                                Text(
                                    if (installedAlready) uiResources.getString(R.string.memoh_ui_installed)
                                    else uiResources.getString(R.string.memoh_ui_install),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = busy.isBlank()) {
                Text(uiResources.getString(R.string.memoh_ui_close))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { scope.launch { reload() } },
                enabled = busy.isBlank() && !loading,
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(uiResources.getString(R.string.memoh_ui_refresh))
            }
        },
    )
}
