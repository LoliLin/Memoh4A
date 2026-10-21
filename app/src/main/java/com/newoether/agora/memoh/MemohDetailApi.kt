package com.newoether.agora.memoh

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

// =================== MCP Models & APIs ===================
internal data class MemohMcpTool(
    val name: String,
    val description: String,
)

internal data class MemohMcpConnection(
    val id: String,
    val name: String,
    val transport: String,
    val url: String,
    val command: String,
    val isActive: Boolean,
    val status: String,
    val statusMessage: String,
    val lastProbedAt: String,
    val tools: List<MemohMcpTool>,
)

internal suspend fun MemohApi.mcpList(botId: String): List<MemohMcpConnection> {
    val items = request("/bots/${Uri.encode(botId)}/mcp").optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        val config = obj.optJSONObject("config") ?: JSONObject()
        val toolsArr = obj.optJSONArray("tools_cache") ?: JSONArray()
        val tools = (0 until toolsArr.length()).mapNotNull { t ->
            val toolObj = toolsArr.optJSONObject(t) ?: return@mapNotNull null
            MemohMcpTool(
                name = toolObj.optString("name"),
                description = toolObj.optString("description")
            )
        }
        MemohMcpConnection(
            id = obj.optString("id"),
            name = obj.optString("name"),
            transport = obj.optString("type").ifBlank { config.optString("transport", "sse") },
            url = config.optString("url"),
            command = config.optString("command"),
            isActive = obj.optBoolean("is_active", true),
            status = obj.optString("status", "ready"),
            statusMessage = obj.optString("status_message"),
            lastProbedAt = obj.optString("last_probed_at"),
            tools = tools
        )
    }
}

internal suspend fun MemohApi.createMcpConnection(
    botId: String,
    name: String,
    transport: String,
    url: String,
    command: String,
    isActive: Boolean = true
): JSONObject {
    val payload = JSONObject()
        .put("name", name.trim())
        .put("transport", transport)
        .put("is_active", isActive)
    if (transport == "sse") {
        payload.put("url", url.trim())
    } else {
        payload.put("command", command.trim())
    }
    return request("/bots/${Uri.encode(botId)}/mcp", payload, method = "POST")
}

internal suspend fun MemohApi.updateMcpConnection(
    botId: String,
    id: String,
    name: String,
    transport: String,
    url: String,
    command: String,
    isActive: Boolean
): JSONObject {
    val payload = JSONObject()
        .put("name", name.trim())
        .put("transport", transport)
        .put("is_active", isActive)
    if (transport == "sse") {
        payload.put("url", url.trim())
    } else {
        payload.put("command", command.trim())
    }
    return request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}", payload, method = "PUT")
}

internal suspend fun MemohApi.deleteMcpConnection(botId: String, id: String) {
    request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}", method = "DELETE")
}

internal suspend fun MemohApi.probeMcpConnection(botId: String, id: String): JSONObject {
    return request("/bots/${Uri.encode(botId)}/mcp/${Uri.encode(id)}/probe", JSONObject(), method = "POST")
}

// =================== Apps & Skills Models & APIs ===================
internal data class MemohAppItem(
    val appId: String,
    val installationId: String,
    val name: String,
    val version: String,
    val description: String,
    val status: String,
    val skillsCount: Int,
    val skillNames: List<String>,
    val dependenciesCount: Int,
    val connectorsCount: Int,
    val lastError: String,
)

internal data class MemohSkillItem(
    val name: String,
    val description: String,
    val sourcePath: String,
    val sourceKind: String,
    val state: String,
    val managed: Boolean,
)

internal suspend fun MemohApi.botApps(botId: String, refresh: Boolean = false): List<MemohAppItem> {
    val path = if (refresh) "/bots/${Uri.encode(botId)}/apps?refresh=true" else "/bots/${Uri.encode(botId)}/apps"
    val items = request(path).optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        val skillsArr = obj.optJSONArray("skills") ?: JSONArray()
        val skillNames = (0 until skillsArr.length()).mapNotNull { s ->
            skillsArr.optJSONObject(s)?.optString("name")?.takeIf { it.isNotBlank() }
        }
        val depsArr = obj.optJSONArray("dependencies") ?: JSONArray()
        val connectorsArr = obj.optJSONArray("connectors") ?: JSONArray()
        MemohAppItem(
            appId = obj.optString("app_id"),
            installationId = obj.optString("installation_id"),
            name = obj.optString("name").ifBlank { obj.optString("app_id") },
            version = obj.optString("version"),
            description = obj.optString("description"),
            status = obj.optString("status", "installed"),
            skillsCount = skillsArr.length(),
            skillNames = skillNames,
            dependenciesCount = depsArr.length(),
            connectorsCount = connectorsArr.length(),
            lastError = obj.optString("last_error")
        )
    }
}

internal suspend fun MemohApi.checkAppUpdates(botId: String): List<MemohAppItem> {
    val res = request("/bots/${Uri.encode(botId)}/apps/check-updates", JSONObject(), method = "POST")
    val items = res.optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        val skillsArr = obj.optJSONArray("skills") ?: JSONArray()
        val skillNames = (0 until skillsArr.length()).mapNotNull { s ->
            skillsArr.optJSONObject(s)?.optString("name")?.takeIf { it.isNotBlank() }
        }
        val depsArr = obj.optJSONArray("dependencies") ?: JSONArray()
        val connectorsArr = obj.optJSONArray("connectors") ?: JSONArray()
        MemohAppItem(
            appId = obj.optString("app_id"),
            installationId = obj.optString("installation_id"),
            name = obj.optString("name").ifBlank { obj.optString("app_id") },
            version = obj.optString("version"),
            description = obj.optString("description"),
            status = obj.optString("status", "installed"),
            skillsCount = skillsArr.length(),
            skillNames = skillNames,
            dependenciesCount = depsArr.length(),
            connectorsCount = connectorsArr.length(),
            lastError = obj.optString("last_error")
        )
    }
}

internal suspend fun MemohApi.deleteApp(botId: String, installationId: String) {
    request("/bots/${Uri.encode(botId)}/apps/${Uri.encode(installationId)}", method = "DELETE")
}

internal suspend fun MemohApi.botSkills(botId: String): List<MemohSkillItem> {
    val items = request("/bots/${Uri.encode(botId)}/container/skills").optJSONArray("skills") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        MemohSkillItem(
            name = obj.optString("name"),
            description = obj.optString("description"),
            sourcePath = obj.optString("source_path"),
            sourceKind = obj.optString("source_kind"),
            state = obj.optString("state"),
            managed = obj.optBoolean("managed", false)
        )
    }
}

// =================== Advanced Settings Models & APIs ===================
internal data class MemohBotCheck(
    val id: String,
    val summary: String,
    val subtitle: String,
    val status: String,
    val type: String,
    val detail: String,
)

internal data class MemohStarterPrompt(
    val title: String,
    val prompt: String,
)

internal suspend fun MemohApi.botChecks(botId: String): List<MemohBotCheck> {
    val items = request("/bots/${Uri.encode(botId)}/checks").optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        MemohBotCheck(
            id = obj.optString("id"),
            summary = obj.optString("summary"),
            subtitle = obj.optString("subtitle"),
            status = obj.optString("status", "ok"),
            type = obj.optString("type"),
            detail = obj.optString("detail")
        )
    }
}

internal suspend fun MemohApi.botStarterPrompts(botId: String): List<MemohStarterPrompt> {
    val items = request("/bots/${Uri.encode(botId)}/starter-prompts").optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        MemohStarterPrompt(
            title = obj.optString("title"),
            prompt = obj.optString("prompt")
        )
    }
}

internal suspend fun MemohApi.refreshStarterPrompts(botId: String, currentPrompts: List<String> = emptyList()): List<MemohStarterPrompt> {
    val payload = JSONObject().put("avoid", JSONArray(currentPrompts))
    val items = request("/bots/${Uri.encode(botId)}/starter-prompts/refresh", payload, method = "POST").optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        MemohStarterPrompt(
            title = obj.optString("title"),
            prompt = obj.optString("prompt")
        )
    }
}

internal suspend fun MemohApi.botAclDefaultEffect(botId: String): String {
    val res = request("/bots/${Uri.encode(botId)}/acl/default-effect")
    return res.optString("default_effect", "allow").ifBlank { "allow" }
}

internal suspend fun MemohApi.setBotAclDefaultEffect(botId: String, effect: String) {
    val payload = JSONObject().put("default_effect", effect)
    request("/bots/${Uri.encode(botId)}/acl/default-effect", payload, method = "PUT")
}

internal suspend fun MemohApi.clearCompactionLogs(botId: String) {
    request("/bots/${Uri.encode(botId)}/compaction/logs", method = "DELETE")
}

// =================== Remote Runtime Models & APIs ===================
internal data class MemohRemoteRuntime(
    val id: String,
    val name: String,
    val online: Boolean,
    val hostname: String,
    val os: String,
    val arch: String,
    val clientVersion: String,
    val workspaceBase: String,
    val key: String,
    val createdAt: String,
)

internal suspend fun MemohApi.remoteRuntimes(): List<MemohRemoteRuntime> {
    val items = request("/users/me/runtimes").optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).mapNotNull { i ->
        val obj = items.optJSONObject(i) ?: return@mapNotNull null
        MemohRemoteRuntime(
            id = obj.optString("id"),
            name = obj.optString("name"),
            online = obj.optBoolean("online", false),
            hostname = obj.optString("hostname"),
            os = obj.optString("os"),
            arch = obj.optString("arch"),
            clientVersion = obj.optString("client_version"),
            workspaceBase = obj.optString("workspace_base"),
            key = obj.optString("key"),
            createdAt = obj.optString("created_at")
        )
    }
}

internal suspend fun MemohApi.createRemoteRuntime(name: String): MemohRemoteRuntime {
    val payload = JSONObject().put("name", name.trim())
    val obj = request("/users/me/runtimes", payload, method = "POST")
    return MemohRemoteRuntime(
        id = obj.optString("id"),
        name = obj.optString("name"),
        online = obj.optBoolean("online", false),
        hostname = obj.optString("hostname"),
        os = obj.optString("os"),
        arch = obj.optString("arch"),
        clientVersion = obj.optString("client_version"),
        workspaceBase = obj.optString("workspace_base"),
        key = obj.optString("key"),
        createdAt = obj.optString("created_at")
    )
}

internal suspend fun MemohApi.deleteRemoteRuntime(id: String) {
    request("/users/me/runtimes/${Uri.encode(id)}", method = "DELETE")
}
