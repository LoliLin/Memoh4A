package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

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
    val metadata: JSONObject = JSONObject(),
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
            metadata = JSONObject((item.optJSONObject("metadata") ?: JSONObject()).toString()),
        )
    }
}

/**
 * One entry of `/provider-templates`: the server's own preset for a provider vendor. Creating a
 * provider by hand asks the user to guess `client_type` and `base_url`; the template fills both.
 */
internal data class MemohProviderTemplate(
    val id: String,
    val key: String,
    val name: String,
    val description: String,
    val domain: String,
    val driver: String,
    val icon: String,
    val defaultConfig: JSONObject,
    val fields: List<MemohProviderConfigField>,
    val sortOrder: Int,
) {
    val baseUrl: String get() = defaultConfig.optString("base_url")
}

internal fun decodeProviderTemplates(response: JSONObject): List<MemohProviderTemplate> {
    // The endpoint declares a bare array, which the transport wraps as `items`.
    val array = response.optJSONArray("items")
        ?: response.optJSONArray("data")
        ?: response.optJSONArray("templates")
        ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) return@mapNotNull null
        MemohProviderTemplate(
            id = id,
            key = item.optString("key"),
            name = item.optString("name").ifBlank { item.optString("key") },
            description = item.optString("description"),
            domain = item.optString("domain"),
            driver = item.optString("driver"),
            icon = item.optString("icon"),
            defaultConfig = item.optJSONObject("default_config") ?: JSONObject(),
            fields = decodeProviderConfigFields(item.optJSONObject("config_schema")),
            sortOrder = item.optInt("sort_order", Int.MAX_VALUE),
        )
    }.sortedBy { it.sortOrder }
}

internal enum class MemohConnectivityStatus { PASSED, FAILED, UNVERIFIED }

/** A successful HTTP response can still report an authentication or provider failure. */
internal fun memohConnectivityStatus(response: JSONObject): MemohConnectivityStatus =
    when (response.optString("status").lowercase()) {
        "ok" -> MemohConnectivityStatus.PASSED
        "auth_error", "model_not_supported", "error" -> MemohConnectivityStatus.FAILED
        else -> MemohConnectivityStatus.UNVERIFIED
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

/**
 * Share of the team's **chat** models one provider serves, for the row's gradient bar.
 *
 * The bar sits next to the provider's chat-model count, so the denominator must be chat models
 * too. Dividing by every configured model (embeddings, search, image...) made a provider that
 * owns two of three chat models draw a fifth of the bar. Zero chat models means no bar: an
 * empty catalog is not the same claim as "this provider serves nothing".
 */
internal fun chatModelShare(providerId: String, models: List<MemohAdminModel>): Float {
    val chatModels = models.filter { it.type == "chat" }
    if (chatModels.isEmpty()) return 0f
    return chatModels.count { it.providerId == providerId }.toFloat() / chatModels.size.toFloat()
}

internal fun chatModelCount(models: List<MemohAdminModel>): Int = models.count { it.type == "chat" }

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
            reasoningEfforts = item.reasoningEfforts().filterNot { it == "disable" },
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

/** The server accepts UTC date-only boundaries, with an exclusive upper bound. */
internal data class MemohUsageRange(val from: String, val to: String)

internal fun memohUsageRange(days: Int, now: Instant = Instant.now()): MemohUsageRange {
    require(days > 0)
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    return MemohUsageRange(
        from = today.minusDays(days.toLong() - 1).toString(),
        to = today.plusDays(1).toString(),
    )
}

/** Cache reads and reasoning are breakdowns, not extra tokens to add to input/output. */
internal data class MemohTokenUsageDay(
    val date: String,
    val channels: Map<String, MemohChannelUsage>,
) {
    val total: Long get() = channels.values.sumOf { it.total }
}

internal data class MemohChannelUsage(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val reasoning: Long = 0,
) {
    val total: Long get() = input + output
}

internal fun decodeTokenUsage(response: JSONObject): List<MemohTokenUsageDay> {
    val byDate = sortedMapOf<String, MutableMap<String, MemohChannelUsage>>()
    listOf("chat", "discuss", "schedule", "memory", "acp_agent").forEach { channel ->
        val rows = response.optJSONArray(channel) ?: return@forEach
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val day = row.optString("day").ifBlank { row.optString("date") }
            if (day.isBlank()) continue
            byDate.getOrPut(day) { linkedMapOf() }[channel] = MemohChannelUsage(
                input = row.optLong("input_tokens"),
                output = row.optLong("output_tokens"),
                cacheRead = row.optLong("cache_read_tokens"),
                reasoning = row.optLong("reasoning_tokens"),
            )
        }
    }
    return byDate.entries.sortedByDescending { it.key }
        .map { (date, values) -> MemohTokenUsageDay(date, values.toMap()) }
}

/** Per-model totals for the same window; the web renders this as the breakdown table. */
internal data class MemohModelUsage(
    val modelName: String,
    val providerName: String,
    val input: Long,
    val output: Long,
) {
    val total: Long get() = input + output
}

internal fun decodeModelUsage(response: JSONObject): List<MemohModelUsage> {
    val rows = response.optJSONArray("by_model") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val name = row.optString("model_name").ifBlank { row.optString("model_slug") }
        if (name.isBlank()) return@mapNotNull null
        MemohModelUsage(
            modelName = name,
            providerName = row.optString("provider_name"),
            input = row.optLong("input_tokens"),
            output = row.optLong("output_tokens"),
        )
    }.sortedByDescending { it.total }
}
