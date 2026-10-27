package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** android.org.json is a stub outside Robolectric, so every JSON row test runs on the runner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MemohMediaAndMarketTest {

    @Test fun userPickedAttachmentIsSentWithADataUrl() {
        val attachment = MemohAttachment(mime = "image/png", base64 = "QUJD")
        assertEquals("data:image/png;base64,QUJD", attachment.wireBase64())
    }

    @Test fun attachmentThatAlreadyCarriesADataUrlIsNotWrappedTwice() {
        val attachment = MemohAttachment(
            mime = "image/jpeg",
            base64 = "data:image/jpeg;base64,QUJD",
        )
        assertEquals("data:image/jpeg;base64,QUJD", attachment.wireBase64())
    }

    @Test fun inlineBytesDecodeBothRawAndDataUrlPayloads() {
        val raw = MemohAttachment(base64 = "QUJD")
        val dataUrl = MemohAttachment(base64 = "data:text/plain;base64,QUJD")
        assertEquals("ABC", raw.inlineBytes()?.toString(Charsets.UTF_8))
        assertEquals("ABC", dataUrl.inlineBytes()?.toString(Charsets.UTF_8))
    }

    @Test fun corruptInlinePayloadIsRejectedInsteadOfDecodingGarbage() {
        assertNull(MemohAttachment(base64 = "!!!not-base64!!!").inlineBytes())
        assertNull(MemohAttachment(url = "data:text/plain;base64").inlineBytes())
        assertNull(MemohAttachment().inlineBytes())
    }

    @Test fun historyAttachmentWithoutBase64StillNeedsADownload() {
        val remote = MemohAttachment(
            name = "shot.png", mime = "image/png", contentHash = "a".repeat(64),
            botId = "bot-1", size = 10,
        )
        assertTrue(remote.needsDownload())
        assertNull(remote.inlineBytes())
    }

    @Test fun cachedFileSlotSeparatesAttachmentsThatShareAnEmptyId() {
        val dir = kotlin.io.path.createTempDirectory("memoh-media-slot").toFile()
        try {
            val first = MemohAttachment(id = "", name = "a.png", path = "/data/a.png")
            val second = MemohAttachment(id = "", name = "a.png", path = "/data/b.png")
            val third = MemohAttachment(id = "", name = "a.png", path = "/data/a.png")
            assertTrue(first.cachedTarget(dir) != second.cachedTarget(dir))
            assertEquals(first.cachedTarget(dir), third.cachedTarget(dir))
            // Nothing was written: the lookup is read-only during composition.
            assertNull(first.cachedFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun decodeAttachmentsReadsContentHashAndOwningBot() {
        val row = JSONObject()
            .put("id", "att-1")
            .put("name", "photo.png")
            .put("mime", "image/png")
            .put("content_hash", "f".repeat(64))
            .put("bot_id", "bot-9")
            .put("storage_key", "media/photo.png")
        val decoded = decodeAttachments(JSONArray().put(row)).single()
        assertEquals("f".repeat(64), decoded.contentHash)
        assertEquals("bot-9", decoded.botId)
        assertEquals("media/photo.png", decoded.storageKey)
        assertTrue(decoded.needsDownload())
    }

    @Test fun decodeAttachmentsFallsBackToMetadataSourcePath() {
        val row = JSONObject()
            .put("id", "att-2")
            .put("mime", "text/plain")
            .put("metadata", JSONObject().put("source_path", "/data/notes.txt"))
        val decoded = decodeAttachments(JSONArray().put(row)).single()
        assertEquals("/data/notes.txt", decoded.path)
    }

    @Test fun reasoningEffortsFollowTheModelsOwnDeclaration() {
        val model = JSONObject().put(
            "config",
            JSONObject().put("reasoning_efforts", JSONArray(listOf("low", "high", "max", "bogus"))),
        )
        assertEquals(listOf("low", "high", "max"), model.reasoningEfforts())
    }

    @Test fun resolvedReasoningOptionsTakePrecedenceAndKeepTheServerDefault() {
        val model = JSONObject("""{
            "config":{"reasoning_efforts":["low","high"]},
            "reasoning":{"supported":true,"can_disable":true,"default_off":true,
                         "default_effort":"high","efforts":["minimal","high"]}
        }""")
        assertEquals(listOf("disable", "minimal", "high"), model.reasoningEfforts())
        assertEquals("disable", model.reasoningDefault())
        model.getJSONObject("reasoning").put("default_off", false)
        assertEquals("high", model.reasoningDefault())
        model.getJSONObject("reasoning").put("supported", false)
        assertTrue(model.reasoningEfforts().isEmpty())
        assertEquals("", model.reasoningDefault())
    }

    @Test fun toggleOnlyAndUnknownServerReasoningOptionsRemainSelectable() {
        val model = JSONObject("""{"reasoning":{"supported":true,"can_disable":true,
                         "efforts":["enabled","future-option"],"default_effort":"enabled"}}""")
        assertEquals(listOf("disable", "enabled", "future-option"), model.reasoningEfforts())
        assertEquals("enabled", model.reasoningDefault())
        val nested = JSONObject("""{"config":{"reasoning":{"toggle":true,"efforts":["minimal"]}}}""")
        assertTrue(nested.reasoningThinkingToggle())
        assertEquals(listOf("minimal"), nested.reasoningEfforts())
    }

    @Test fun modelWithoutThinkingSupportOffersNoEffortChoices() {
        assertEquals(emptyList<String>(), JSONObject().put("config", JSONObject()).reasoningEfforts())
        assertEquals(emptyList<String>(), JSONObject().reasoningEfforts())
    }

    @Test fun marketEntriesKeepTheIdentifiersTheInstallCallNeeds() {
        val response = JSONObject().put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("registry_id", "reg-1")
                    .put("app_id", "app-1")
                    .put("name", "Notes")
                    .put("version", "1.2.3")
                    .put("revision", "rev-7"),
            ),
        )
        val app = decodeMarketApps(response).single()
        assertEquals("reg-1", app.registryId)
        assertEquals("app-1", app.appId)
        assertEquals("rev-7", app.revision)
        assertEquals("Notes", app.name)
    }

    @Test fun marketEntriesWithoutAnOwnerRegistryAreDropped() {
        val response = JSONObject().put(
            "data",
            JSONArray().put(JSONObject().put("app_id", "app-1")).put(JSONObject().put("registry_id", "reg-1")),
        )
        assertTrue(decodeMarketApps(response).isEmpty())
    }

    @Test fun sendStateBelongsToExactlyItsBotAndConversation() {
        val pending = MemohSendState("bot-a", "chat-1", "hello")
        assertTrue(pending.belongsTo("bot-a", "chat-1"))
        assertFalse(pending.belongsTo("bot-b", "chat-1"))
        assertFalse(pending.belongsTo("bot-a", "chat-2"))
        // A brand-new conversation has no server id yet; it must not match an unrelated chat.
        assertFalse(MemohSendState("bot-a", null, "hello").belongsTo("bot-a", "chat-2"))
    }
}
