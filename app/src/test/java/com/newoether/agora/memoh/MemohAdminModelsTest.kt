package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MemohAdminModelsTest {

    private fun providerRow(): JSONObject = JSONObject()
        .put("id", "prov-1")
        .put("name", "OpenCode Go")
        .put("client_type", "opencode-go")
        .put("enable", true)
        .put("managed", false)
        .put("config", JSONObject().put("api_key", "sk-secret").put("base_url", "https://opencode.ai/zen/go/v1"))

    @Test fun providersDecodeWithTypeBaseUrlAndKeyPresence() {
        val provider = decodeProviders(JSONObject().put("data", JSONArray().put(providerRow()))).single()
        assertEquals("prov-1", provider.id)
        assertEquals("OpenCode Go", provider.name)
        assertEquals("opencode-go", provider.displayType)
        assertEquals("https://opencode.ai/zen/go/v1", provider.baseUrl)
        assertTrue(provider.hasApiKey)
    }

    @Test fun providerWithoutKeyIsReportedSoTheScreenCanWarn() {
        val row = providerRow().put("config", JSONObject().put("base_url", "https://x.test"))
        assertFalse(decodeProviders(JSONObject().put("data", JSONArray().put(row))).single().hasApiKey)
    }

    @Test fun providerRowsWithoutIdAreDroppedInsteadOfRenderingBlankCards() {
        val row = providerRow().remove("id") ?: providerRow()
        assertTrue(decodeProviders(JSONObject().put("data", JSONArray().put(row))).isEmpty())
    }

    @Test fun editingAProviderWithoutTypingAKeyKeepsTheStoredSecret() {
        val existing = decodeProviders(JSONObject().put("data", JSONArray().put(providerRow()))).single()
        val body = providerRequestBody("Renamed", existing.clientType, "", "", existing, create = false)
        val config = body.getJSONObject("config")
        assertEquals("sk-secret", config.getString("api_key"))
        assertEquals("https://opencode.ai/zen/go/v1", config.getString("base_url"))
        assertEquals("Renamed", body.getString("name"))
    }

    @Test fun editingAProviderCanReplaceTheKeyAndBaseUrl() {
        val existing = decodeProviders(JSONObject().put("data", JSONArray().put(providerRow()))).single()
        val body = providerRequestBody("P", existing.clientType, "https://new.test/v1", "sk-new", existing, create = false)
        val config = body.getJSONObject("config")
        assertEquals("sk-new", config.getString("api_key"))
        assertEquals("https://new.test/v1", config.getString("base_url"))
    }

    @Test fun creatingAProviderSendsTheClientTypeTheServerRequires() {
        val body = providerRequestBody("Fresh", "openai", "https://api.test/v1", "sk-a", null, create = true)
        assertEquals("openai", body.getString("client_type"))
        assertTrue(body.getJSONObject("config").has("api_key"))
    }

    @Test fun connectivityTestsUseTheServerStatusRatherThanHttpSuccessAlone() {
        assertEquals(MemohConnectivityStatus.PASSED, memohConnectivityStatus(JSONObject("""{"status":"ok"}""")))
        assertEquals(MemohConnectivityStatus.FAILED, memohConnectivityStatus(JSONObject("""{"status":"auth_error"}""")))
        assertEquals(MemohConnectivityStatus.FAILED, memohConnectivityStatus(JSONObject("""{"status":"model_not_supported"}""")))
        assertEquals(MemohConnectivityStatus.FAILED, memohConnectivityStatus(JSONObject("""{"status":"error"}""")))
        assertEquals(MemohConnectivityStatus.UNVERIFIED, memohConnectivityStatus(JSONObject("""{"status":"unverified"}""")))
        assertEquals(MemohConnectivityStatus.UNVERIFIED, memohConnectivityStatus(JSONObject()))
    }

    @Test fun modelRowsDecodeTheirProviderAndReasoningLadder() {
        val row = JSONObject()
            .put("id", "model-rec")
            .put("model_id", "deepseek-v4.1-flash")
            .put("name", "DeepSeek V4.1 Flash")
            .put("provider_id", "prov-1")
            .put("type", "chat")
            .put("enable", true)
            .put("config", JSONObject()
                .put("context_window", 1_000_000)
                .put("reasoning_efforts", JSONArray(listOf("low", "high", "max"))))
        val model = decodeAdminModels(JSONObject().put("data", JSONArray().put(row))).single()
        assertEquals("prov-1", model.providerId)
        assertEquals(listOf("low", "high", "max"), model.reasoningEfforts)
        assertEquals(1_000_000L, model.contextWindow)
        assertEquals("DeepSeek V4.1 Flash", model.label)
    }

    @Test fun modelWithoutANameFallsBackToItsProviderCode() {
        val row = JSONObject().put("id", "m").put("model_id", "gpt-x").put("enable", true)
        assertEquals("gpt-x", decodeAdminModels(JSONObject().put("data", JSONArray().put(row))).single().label)
    }

    @Test fun newModelRequestCarriesTypeAndOnlyTheConfigItWasGiven() {
        val body = modelRequestBody("prov-1", "gpt-x", "GPT X", true, listOf("low", "high"), 0, create = true)
        assertEquals("chat", body.getString("type"))
        assertEquals("prov-1", body.getString("provider_id"))
        assertTrue(body.getBoolean("enable"))
        val config = body.getJSONObject("config")
        assertEquals(2, config.getJSONArray("reasoning_efforts").length())
        assertFalse(config.has("context_window"))
    }

    @Test fun usageDecodesTheChannelArraysTheServerActuallyReturns() {
        val response = JSONObject()
            .put("chat", JSONArray().put(
                JSONObject()
                    .put("day", "2026-10-25")
                    .put("input_tokens", 12_858_479)
                    .put("output_tokens", 51_072)
                    .put("cache_read_tokens", 12_164_224)
                    .put("reasoning_tokens", 23_880),
            ))
            .put("memory", JSONArray().put(
                JSONObject().put("day", "2026-10-25").put("input_tokens", 300),
            ))
        val day = decodeTokenUsage(response).single()
        assertEquals("2026-10-25", day.date)
        val chat = requireNotNull(day.channels["chat"])
        assertEquals(12_858_479L, chat.input)
        assertEquals(51_072L, chat.output)
        assertEquals(12_164_224L, chat.cacheRead)
        assertEquals(23_880L, chat.reasoning)
        assertEquals(12_858_479L + 51_072, chat.total)
        assertEquals(chat.total + 300, day.total)
    }

    @Test fun usageRangeUsesUtcAndIncludesTodayAcrossLeapDay() {
        val now = java.time.Instant.parse("2024-03-01T23:50:00Z")
        assertEquals(MemohUsageRange("2024-02-24", "2024-03-02"), memohUsageRange(7, now))
        assertEquals(MemohUsageRange("2024-03-01", "2024-03-02"), memohUsageRange(1, now))
    }

    @Test fun usageDaysAreNewestFirstAndEmptyResponsesYieldNothing() {
        val response = JSONObject().put("chat", JSONArray()
            .put(JSONObject().put("day", "2026-10-20").put("input_tokens", 1))
            .put(JSONObject().put("day", "2026-10-25").put("input_tokens", 2)))
        assertEquals(listOf("2026-10-25", "2026-10-20"), decodeTokenUsage(response).map { it.date })
        assertTrue(decodeTokenUsage(JSONObject()).isEmpty())
    }

    @Test fun modelBreakdownIsSortedByTotalAndKeepsProviderNames() {
        val response = JSONObject().put("by_model", JSONArray()
            .put(JSONObject().put("model_name", "Small").put("provider_name", "A")
                .put("input_tokens", 10).put("output_tokens", 1))
            .put(JSONObject().put("model_name", "Large").put("provider_name", "B")
                .put("input_tokens", 1000).put("output_tokens", 500)))
        val models = decodeModelUsage(response)
        assertEquals(listOf("Large", "Small"), models.map { it.modelName })
        assertEquals(1500L, models.first().total)
        assertEquals("B", models.first().providerName)
    }

    @Test fun modelBreakdownFallsBackToTheSlugWhenNoNameIsGiven() {
        val response = JSONObject().put("by_model", JSONArray()
            .put(JSONObject().put("model_slug", "deepseek-v4").put("input_tokens", 5)))
        assertEquals("deepseek-v4", decodeModelUsage(response).single().modelName)
    }

    @Test fun tokenFormattingStaysReadableAtEveryMagnitude() {
        assertEquals("0", formatTokens(0))
        assertEquals("999", formatTokens(999))
        assertEquals("1.2k", formatTokens(1234))
        assertEquals("1.50M", formatTokens(1_500_000))
    }
}
