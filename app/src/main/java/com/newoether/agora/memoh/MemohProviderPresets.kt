package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject

/** A model source from Memoh Web's model-sources.ts, pinned in memoh_model_sources.json. */
internal data class MemohProviderPreset(
    val id: String,
    val name: String,
    val clientType: String,
    val baseUrl: String,
    val source: String? = null,
    val logo: String? = null,
    val subscription: Boolean = false,
    val templateId: String? = null,
    val templateFields: List<MemohProviderConfigField>? = null,
    val templateDefaults: JSONObject? = null,
) {
    val managedOAuth: Boolean get() = clientType == "openai-codex" || clientType == "github-copilot"
    val showApiKey: Boolean get() = !managedOAuth && (templateFields == null || templateFields.any { it.key == "api_key" })
    val showBaseUrl: Boolean get() = clientType != "github-copilot" &&
        (templateFields == null || templateFields.any { it.key == "base_url" })
    val requiresApiKey: Boolean get() = showApiKey &&
        (templateFields?.firstOrNull { it.key == "api_key" }?.required
            ?: !runCatching { java.net.URI(baseUrl).host?.lowercase() in setOf("localhost", "127.0.0.1") }.getOrDefault(false))
    val requiresBaseUrl: Boolean get() = showBaseUrl &&
        (templateFields?.firstOrNull { it.key == "base_url" }?.required ?: true)
}

internal data class MemohProviderConfigField(
    val key: String,
    val required: Boolean,
    val type: String = "string",
    val title: String = key,
    val description: String = "",
    val secret: Boolean = false,
    val options: List<String> = emptyList(),
    val order: Int = 0,
)

/** Web supports both object and array forms of config_schema.fields. */
internal fun decodeProviderConfigFields(schema: JSONObject?): List<MemohProviderConfigField> {
    fun field(key: String, value: JSONObject?, index: Int): MemohProviderConfigField {
        val type = value?.optString("type")?.takeIf(String::isNotBlank) ?: "string"
        val options = value?.optJSONArray("enum")
        return MemohProviderConfigField(
            key = key,
            required = value?.optBoolean("required") == true,
            type = type,
            title = value?.optString("title")?.takeIf(String::isNotBlank) ?: key,
            description = value?.optString("description").orEmpty(),
            secret = value?.optBoolean("secret") == true || type in setOf("secret", "password"),
            options = if (options == null) emptyList() else (0 until options.length()).mapNotNull {
                (options.opt(it) as? String)?.takeIf(String::isNotBlank)
            },
            order = value?.optInt("order", index) ?: index,
        )
    }
    val fields = schema?.opt("fields")
    val result = when (fields) {
        is JSONArray -> (0 until fields.length()).mapNotNull { index ->
            val value = fields.optJSONObject(index) ?: return@mapNotNull null
            value.optString("key").takeIf(String::isNotBlank)?.let { field(it, value, index) }
        }
        is JSONObject -> fields.keys().asSequence().withIndex().map { (index, key) ->
            field(key, fields.optJSONObject(key), index)
        }.toList()
        else -> emptyList()
    }
    return result.sortedBy { it.order }
}

/** Fields besides the two common credentials remain owned by the selected server template. */
internal fun MemohProviderPreset.extraTemplateFields(): List<MemohProviderConfigField> =
    templateFields.orEmpty().filterNot { it.key == "base_url" || it.key == "api_key" }

internal fun memohTemplateFieldValue(field: MemohProviderConfigField, input: String): Any? {
    val value = input.trim()
    if (value.isEmpty() || (field.options.isNotEmpty() && value !in field.options)) return null
    return when (field.type.lowercase()) {
        "integer", "int" -> value.toLongOrNull()
        "number", "float", "double" -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
        "boolean", "bool" -> when (value) { "true" -> true; "false" -> false; else -> null }
        "object" -> runCatching { JSONObject(value) }.getOrNull()
        "array" -> runCatching { JSONArray(value) }.getOrNull()
        else -> value
    }
}

internal data class MemohTemplateExtraConfig(val config: JSONObject, val invalidKeys: Set<String>)

internal fun memohTemplateExtraConfig(preset: MemohProviderPreset?, values: Map<String, String>): MemohTemplateExtraConfig {
    val config = JSONObject()
    val invalid = mutableSetOf<String>()
    preset?.extraTemplateFields().orEmpty().forEach { field ->
        val input = values[field.key].orEmpty()
        if (input.isBlank()) {
            val default = preset?.templateDefaults?.opt(field.key)
            val hasDefault = default != null && default != JSONObject.NULL && (default !is String || default.isNotBlank())
            if (field.required && !hasDefault) invalid += field.key
        } else {
            val value = memohTemplateFieldValue(field, input)
            if (value == null) invalid += field.key else config.put(field.key, value)
        }
    }
    return MemohTemplateExtraConfig(config, invalid)
}

internal data class MemohProviderClientType(val id: String, val label: String)

/** LLM client types offered by the pinned upstream Web release. */
internal val memohProviderClientTypes = listOf(
    MemohProviderClientType("opencode-go", "OpenCode Go"),
    MemohProviderClientType("openai-responses", "OpenAI Responses"),
    MemohProviderClientType("openai-completions", "OpenAI Completions"),
    MemohProviderClientType("openai-codex", "OpenAI Codex"),
    MemohProviderClientType("github-copilot", "GitHub Copilot"),
    MemohProviderClientType("anthropic-messages", "Anthropic Messages"),
    MemohProviderClientType("google-generative-ai", "Google Generative AI"),
)

internal fun decodeMemohModelSources(json: JSONObject): List<MemohProviderPreset> {
    val items = json.optJSONArray("sources") ?: return emptyList()
    return (0 until items.length()).mapNotNull { index ->
        val row = items.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val name = row.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val clientType = row.optString("client_type").takeIf(String::isNotBlank) ?: return@mapNotNull null
        MemohProviderPreset(
            id = id,
            name = name,
            clientType = clientType,
            baseUrl = row.optString("base_url"),
            source = row.optString("source").takeIf(String::isNotBlank),
            logo = row.optString("logo").takeIf(String::isNotBlank),
            subscription = row.optBoolean("subscription"),
        )
    }.distinctBy { it.id }
}

/** Template keys which Web replaces with the corresponding model-source entry. */
internal val memohTemplateSources = mapOf(
    "anthropic" to "anthropic", "azure-openai" to "azure-openai", "cerebras" to "cerebras",
    "cloudflare" to "cloudflare", "codex" to "codex", "deepseek" to "deepseek",
    "fireworks" to "fireworks-ai", "github-copilot" to "github-copilot", "google" to "google",
    "groq" to "groq", "huggingface" to "huggingface", "lmstudio" to "lmstudio",
    "minimax" to "minimax", "mistral" to "mistral", "moonshot" to "moonshotai-cn",
    "newapi" to "newapi", "ollama" to "ollama", "openai" to "openai",
    "opencode-go" to "opencode-go", "openrouter" to "openrouter", "perplexity" to "perplexity",
    "qwen" to "alibaba-cn", "together" to "togetherai", "xai" to "xai",
    "zai" to "zai", "zhipu" to "zhipuai",
)

internal fun memohProviderChoices(
    sources: List<MemohProviderPreset>, templates: List<MemohProviderTemplate>,
): List<MemohProviderPreset> {
    val sourceIds = sources.mapTo(mutableSetOf()) { it.id }
    val remainingTemplates = templates.asSequence()
        .filter { it.domain == "llm" && it.id.isNotBlank() && it.driver.isNotBlank() }
        .filterNot { memohTemplateSources[it.key] in sourceIds }
        .map { template ->
            MemohProviderPreset(
                id = "template:${template.id}", name = template.name, clientType = template.driver,
                baseUrl = template.baseUrl, templateId = template.id, templateFields = template.fields,
                templateDefaults = template.defaultConfig,
            )
        }
        .toList()
    return (remainingTemplates + sources).sortedWith(compareByDescending<MemohProviderPreset> { it.subscription }
        .thenBy { it.name.lowercase() })
}

internal fun memohProviderCatalogSource(metadata: JSONObject, sources: List<MemohProviderPreset>): String? {
    metadata.optString("source").takeIf(String::isNotBlank)?.let { return it }
    val recorded = metadata.optString("catalog")
    sources.firstOrNull { it.id == recorded }?.source?.let { return it }
    for (section in listOf("preset", "registry")) {
        val legacy = metadata.optJSONObject(section)?.optString("source").orEmpty().removeSuffix(".yaml")
        val mapped = memohTemplateSources[legacy]
        sources.firstOrNull { it.id == mapped }?.source?.let { return it }
    }
    return null
}

internal fun memohSuggestedModelCompatibilities(clientType: String): List<String> =
    if (clientType in setOf("openai-responses", "openai-completions", "anthropic-messages", "google-generative-ai")) {
        listOf("tool-call", "reasoning")
    } else emptyList()

internal fun suggestMemohProviderName(base: String, existingNames: Collection<String>): String {
    val trimmed = base.trim()
    if (trimmed.isEmpty()) return ""
    val existing = existingNames.map { it.trim() }.toSet()
    if (trimmed !in existing) return trimmed
    var index = 2
    while ("$trimmed $index" in existing) index++
    return "$trimmed $index"
}

/** Catalog imports can succeed from a cached, stale snapshot; surface that distinction. */
internal fun memohModelImportSummary(resources: android.content.res.Resources, result: JSONObject): String {
    val summary = resources.getString(
        com.newoether.agora.R.string.memoh_ui_model_import_complete,
        result.optInt("created"), result.optInt("updated"), result.optInt("skipped"),
    )
    return if (result.optJSONObject("catalog")?.optBoolean("stale") == true)
        summary + "\n" + resources.getString(com.newoether.agora.R.string.memoh_ui_model_catalog_stale)
    else summary
}

internal fun memohTemplateProviderBody(
    id: String, name: String, baseUrl: String, apiKey: String, extraConfig: JSONObject = JSONObject(),
): JSONObject {
    val config = JSONObject(extraConfig.toString())
    if (baseUrl.isNotBlank()) config.put("base_url", baseUrl.trim())
    if (apiKey.isNotBlank()) config.put("api_key", apiKey.trim())
    return JSONObject().put("template_id", id).put("domain", "llm").put("name", name.trim()).put("config", config)
}

internal fun memohCatalogProviderBody(
    preset: MemohProviderPreset?, name: String, clientType: String, baseUrl: String, apiKey: String,
): JSONObject {
    val body = providerRequestBody(name, clientType, baseUrl, apiKey, null, create = true)
    if (preset != null) {
        val logo = preset.logo ?: preset.source ?: preset.id
        body.put("icon", "https://models.dev/logos/${android.net.Uri.encode(logo)}.svg")
        body.put("metadata", JSONObject().put("catalog", preset.id).apply {
            preset.source?.let { put("source", it) }
        })
    }
    return body
}
