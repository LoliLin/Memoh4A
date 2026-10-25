package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject

/** One LLM provider the team has configured (server shape: providers.GetResponse). */
internal data class MemohProvider(
    val id: String,
    val name: String,
    val clientType: String,
    val icon: String,
    val enabled: Boolean,
    val managed: Boolean,
    val config: JSONObject,
    val baseUrl: String,
    val hasApiKey: Boolean,
) {
    val displayType: String get() = clientType.ifBlank { "custom" }
}

internal fun decodeProviders(response: JSONObject): List<MemohProvider> {
    val array = response.optJSONArray("data") ?: response.optJSONArray("items") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) return@mapNotNull null
        val config = item.optJSONObject("config") ?: JSONObject()
        MemohProvider(
            id = id,
            name = item.optString("name").ifBlank { item.optString("client_type") },
            clientType = item.optString("client_type"),
            icon = item.optString("icon"),
            enabled = item.optBoolean("enable", true),
            managed = item.optBoolean("managed", false),
            config = JSONObject(config.toString()),
            baseUrl = config.optString("base_url"),
            hasApiKey = config.optString("api_key").isNotBlank(),
        )
    }
}

/** One configured model (server shape: models.AddResponse / list row). */
internal data class MemohAdminModel(
    val id: String,
    val modelId: String,
    val name: String,
    val providerId: String,
    val type: String,
    val enabled: Boolean,
    val contextWindow: Long,
    val reasoningEfforts: List<String>,
) {
    val label: String get() = name.ifBlank { modelId }
}

internal fun decodeAdminModels(response: JSONObject): List<MemohAdminModel> {
    val array = response.optJSONArray("data") ?: response.optJSONArray("items")
        ?: response.optJSONArray("models") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) return@mapNotNull null
        MemohAdminModel(
            id = id,
            modelId = item.optString("model_id"),
            name = item.optString("name"),
            providerId = item.optString("provider_id"),
            type = item.optString("type").ifBlank { "chat" },
            enabled = item.optBoolean("enable", true),
            contextWindow = item.optJSONObject("config")?.optLong("context_window") ?: 0L,
            reasoningEfforts = item.optJSONObject("config")?.optJSONArray("reasoning_efforts")
                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) } }
                .orEmpty(),
        )
    }
}

/**
 * 新增/编辑供应商时只发送改动过的键。服务器把 `config` 当作整体对象，
 * 传空对象会清掉已存的 api_key，所以编辑时先带原值再覆盖用户改动的字段。
 */
internal fun providerRequestBody(
    name: String,
    clientType: String,
    baseUrl: String,
    apiKey: String,
    existing: MemohProvider?,
    create: Boolean,
): JSONObject {
    val body = JSONObject()
    name.trim().takeIf(String::isNotBlank)?.let { body.put("name", it) }
    if (create || existing == null || clientType != existing.clientType) {
        clientType.trim().takeIf(String::isNotBlank)?.let { body.put("client_type", it) }
    }
    val config = JSONObject(existing?.config?.toString() ?: "{}")
    baseUrl.trim().takeIf(String::isNotBlank)?.let { config.put("base_url", it) }
    // An untouched password field must not erase the stored secret.
    apiKey.trim().takeIf(String::isNotBlank)?.let { config.put("api_key", it) }
    if (config.length() > 0) body.put("config", config)
    return body
}

internal fun modelRequestBody(
    providerId: String,
    modelId: String,
    name: String,
    enabled: Boolean,
    reasoningEfforts: List<String>,
    contextWindow: Long,
    create: Boolean,
): JSONObject {
    val body = JSONObject()
    providerId.takeIf(String::isNotBlank)?.let { body.put("provider_id", it) }
    modelId.trim().takeIf(String::isNotBlank)?.let { body.put("model_id", it) }
    name.trim().takeIf(String::isNotBlank)?.let { body.put("name", it) }
    body.put("enable", enabled)
    if (create) body.put("type", "chat")
    val config = JSONObject()
    if (reasoningEfforts.isNotEmpty()) config.put("reasoning_efforts", JSONArray(reasoningEfforts))
    if (contextWindow > 0) config.put("context_window", contextWindow)
    if (config.length() > 0) body.put("config", config)
    return body
}

/** Daily usage as the web renders it: one row per day, per channel. */
internal data class MemohTokenUsageDay(
    val date: String,
    val channels: Map<String, Long>,
) {
    val total: Long get() = channels.values.sum()
}

internal fun decodeTokenUsage(response: JSONObject): List<MemohTokenUsageDay> {
    val channels = linkedMapOf<String, JSONObject>()
    listOf("chat", "discuss", "schedule", "memory", "acp_agent").forEach { key ->
        response.optJSONObject(key)?.let { channels[key] = it }
    }
    // Some builds return channel arrays instead of objects keyed by channel.
    val byDate = linkedMapOf<String, MutableMap<String, Long>>()
    channels.forEach { (channel, value) ->
        val rows = value.optJSONArray("items") ?: value.optJSONArray("data")
        if (rows == null) {
            val date = value.optString("date").ifBlank { value.optString("day") }
            if (date.isNotBlank()) {
                byDate.getOrPut(date) { linkedMapOf() }[channel] =
                    value.optLong("total_tokens").let { if (it > 0) it else value.optLong("tokens") }
            }
        } else {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val date = row.optString("date").ifBlank { row.optString("day") }
                if (date.isBlank()) continue
                val tokens = row.optLong("total_tokens").let { if (it > 0) it else row.optLong("tokens") }
                byDate.getOrPut(date) { linkedMapOf() }[channel] = tokens
            }
        }
    }
    return byDate.entries.sortedByDescending { it.key }
        .map { (date, values) -> MemohTokenUsageDay(date, values.toMap()) }
}

internal fun MemohTokenUsageDay.channelTotal(): Long = channels.values.sum()
