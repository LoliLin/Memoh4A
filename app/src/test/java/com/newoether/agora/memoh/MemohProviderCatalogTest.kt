package com.newoether.agora.memoh

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemohProviderCatalogTest {
    private fun sources(): List<MemohProviderPreset> =
        ApplicationProvider.getApplicationContext<Application>().assets.open("memoh_model_sources.json")
            .bufferedReader().use { decodeMemohModelSources(JSONObject(it.readText())) }

    @Test fun webCatalogHasPinnedSourcesAndExactEndpoints() {
        val sources = sources()
        assertEquals(187, sources.size)
        assertEquals("https://api.deepseek.com", sources.single { it.id == "deepseek" }.baseUrl)
        assertEquals("https://api.anthropic.com/v1", sources.single { it.id == "anthropic" }.baseUrl)
        assertEquals("https://router.huggingface.co/v1", sources.single { it.id == "huggingface" }.baseUrl)
        assertFalse(sources.single { it.id == "ollama" }.requiresApiKey)
        assertFalse(sources.single { it.id == "github-copilot" }.requiresApiKey)
        assertFalse(sources.single { it.id == "github-copilot" }.showBaseUrl)
        assertTrue(sources.single { it.id == "openai" }.requiresApiKey)
        assertTrue(sources.single { it.id == "ollama" }.copy(baseUrl = "http://localhost.example.test/v1").requiresApiKey)
    }

    @Test fun successfulImportsShowWhenTheCatalogIsStale() {
        val resources = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources
        val result = JSONObject("""{"created":2,"updated":1,"skipped":3,"catalog":{"stale":true}}""")
        val stale = memohModelImportSummary(resources, result)
        assertTrue(stale.contains(resources.getString(com.newoether.agora.R.string.memoh_ui_model_catalog_stale)))
        result.getJSONObject("catalog").put("stale", false)
        assertFalse(memohModelImportSummary(resources, result).contains(resources.getString(com.newoether.agora.R.string.memoh_ui_model_catalog_stale)))
    }

    @Test fun deviceAuthorizationStatusAndVerificationUrlsFollowServerContract() {
        val status = decodeProviderOAuthStatus(JSONObject("""
            {"configured":true,"has_token":false,"device":{"pending":true,"user_code":"ABCD-1234",
             "verification_uri":"https://example.test/device","interval_seconds":0}}
        """))
        assertTrue(status.configured)
        assertEquals("ABCD-1234", status.device?.code)
        assertEquals(1L, status.device?.intervalSeconds)
        assertTrue(memohProviderOAuthVerificationUrl(status.device!!.verificationUri))
        assertFalse(memohProviderOAuthVerificationUrl("javascript:alert(1)"))
        assertFalse(memohProviderOAuthVerificationUrl("https:///missing-host"))
        val connected = decodeProviderOAuthStatus(JSONObject("""
            {"configured":true,"has_token":true,"account":{"email":"user@example.test","login":"person"}}
        """))
        assertEquals("user@example.test · @person", connected.account)
        assertTrue(connected.hasToken)
    }

    @Test fun onlyLlmTemplatesAppearAlongsideCatalogAndSchemaControlsRequirements() {
        val templates = decodeProviderTemplates(JSONObject().put("items", JSONArray("""
            [
              {"id":"one","key":"custom-llm","name":"Vendor","domain":"llm","driver":"openai-completions",
               "default_config":{"base_url":"https://vendor.test"},
               "config_schema":{"fields":{"base_url":{"required":true},"api_key":{"required":false}}}},
              {"id":"two","key":"other","name":"Voice","domain":"speech","driver":"openai-speech"},
              {"id":"three","key":"openai","name":"OpenAI old template","domain":"llm","driver":"openai-responses"}
            ]
        """)))
        val choices = memohProviderChoices(sources(), templates)
        val custom = choices.single { it.templateId == "one" }
        assertEquals("https://vendor.test", custom.baseUrl)
        assertTrue(custom.showApiKey)
        assertTrue(custom.requiresBaseUrl)
        assertFalse(custom.requiresApiKey)
        assertFalse(choices.any { it.templateId == "two" || it.templateId == "three" })
        assertTrue(choices.any { it.id == "openai" })
        val arrayFields = JSONObject("""{"fields":[{"key":"api_key","required":true},{"key":"region"}]}""")
        assertEquals(listOf("api_key" to true, "region" to false), decodeProviderConfigFields(arrayFields).map { it.key to it.required })
    }

    @Test fun templateFieldsAreValidatedAndSentUsingTheirDeclaredTypes() {
        val template = decodeProviderTemplates(JSONObject().put("items", JSONArray("""
            [{"id":"custom","key":"custom-llm","name":"Custom","domain":"llm","driver":"openai-completions",
              "default_config":{"region":"east"},
              "config_schema":{"fields":[
                {"key":"quota","type":"integer","required":true,"order":2},
                {"key":"region","title":"Region","enum":["east","west"],"required":true,"order":1},
                {"key":"token","type":"password","required":true},
                {"key":"stream","type":"boolean"},
                {"key":"options","type":"object"},
                {"key":"tags","type":"array"}
              ]}}]
        """))).single()
        val preset = memohProviderChoices(emptyList(), listOf(template)).single()
        assertEquals("Region", preset.extraTemplateFields().first().title)
        assertTrue(preset.extraTemplateFields().single { it.key == "token" }.secret)
        val missing = memohTemplateExtraConfig(preset, emptyMap())
        assertEquals(setOf("quota", "token"), missing.invalidKeys)
        val bad = memohTemplateExtraConfig(preset, mapOf("quota" to "1.2", "token" to "abc", "region" to "elsewhere", "stream" to "yes"))
        assertEquals(setOf("quota", "region", "stream"), bad.invalidKeys)
        val valid = memohTemplateExtraConfig(preset, mapOf(
            "quota" to "42", "token" to "abc", "region" to "west", "stream" to "false",
            "options" to "{\"depth\":2}", "tags" to "[\"a\"]",
        ))
        assertTrue(valid.invalidKeys.isEmpty())
        val config = memohTemplateProviderBody("custom", "Vendor", "", "", valid.config).getJSONObject("config")
        assertEquals(42L, config.getLong("quota"))
        assertEquals(false, config.getBoolean("stream"))
        assertEquals("west", config.getString("region"))
        assertEquals(2, config.getJSONObject("options").getInt("depth"))
        assertEquals("a", config.getJSONArray("tags").getString(0))
        assertFalse(memohProviderClientTypes.map { it.id }.contains("openai-speech"))
        assertTrue(memohProviderClientTypes.map { it.id }.contains("github-copilot"))
    }

    @Test fun catalogCreationPreservesSourceAndTemplateCreationUsesServerDefaults() {
        val preset = sources().single { it.id == "deepseek" }
        val body = memohCatalogProviderBody(preset, "DeepSeek 2", preset.clientType, preset.baseUrl, " key ")
        assertEquals("deepseek", body.getJSONObject("metadata").getString("catalog"))
        assertEquals("deepseek", body.getJSONObject("metadata").getString("source"))
        assertEquals("key", body.getJSONObject("config").getString("api_key"))
        assertEquals("DeepSeek 3", suggestMemohProviderName(" DeepSeek ", listOf("DeepSeek", "DeepSeek 2")))
        assertEquals("deepseek", memohProviderCatalogSource(body.getJSONObject("metadata"), sources()))
        val templateBody = memohTemplateProviderBody("template-id", "Vendor", "https://example.test", "secret")
        assertEquals("llm", templateBody.getString("domain"))
        assertEquals("template-id", templateBody.getString("template_id"))
        assertFalse(templateBody.has("client_type"))
        assertEquals("https://example.test", templateBody.getJSONObject("config").getString("base_url"))
    }

    @Test fun templateListAndModelImportsUseRealPathsAndTeamScope() = runBlocking {
        data class Seen(val method: String, val path: String, val query: String?, val team: String?, val body: String)
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += Seen(exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.query,
                exchange.requestHeaders.getFirst("X-Team-ID"), exchange.requestBody.bufferedReader().readText())
            val response = (if (exchange.requestURI.path.endsWith("provider-templates"))
                """[{"id":"template-id","name":"Vendor","domain":"llm","driver":"openai-completions"}]"""
            else """{"id":"provider-1","created":2,"updated":1,"skipped":0}""").toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val api = MemohApi()
        try {
            api.configure("http://127.0.0.1:${server.address.port}/api", "test-token")
            api.selectTeam("team-1")
            val admin = MemohAdminApi(api)
            assertEquals("template-id", decodeProviderTemplates(admin.providerTemplates()).single().id)
            admin.createProviderFromTemplate(memohTemplateProviderBody("template-id", "Vendor", "", ""))
            admin.importProviderCatalogModels("provider-1")
            admin.importProviderModels("provider-1", memohSuggestedModelCompatibilities("openai-completions"))
            assertEquals(listOf("/api/provider-templates", "/api/providers/from-template", "/api/providers/provider-1/import-catalog-models", "/api/providers/provider-1/import-models"), seen.map { it.path })
            assertEquals("domain=llm", seen[0].query)
            assertTrue(seen.all { it.team == "team-1" })
            assertEquals("llm", JSONObject(seen[1].body).getString("domain"))
            assertEquals(listOf("tool-call", "reasoning"), JSONObject(seen[3].body).getJSONArray("default_compatibilities").let { (0 until it.length()).map(it::getString) })
        } finally {
            api.clearIdentity()
            server.stop(0)
        }
    }
}
