package com.newoether.agora.memoh

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.newoether.agora.model.Participant
import com.newoether.agora.ui.theme.AgoraTheme
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A Bot has no attachment API: it writes `![alt](/data/x.png)` into its reply and the server
 * registers whatever the reference pointed at as an asset carrying a `content_hash`. The text
 * path itself is prose, so the client must pair the link with its asset by ordinal — reading the
 * path, or ignoring assets, is what left the bubble with an empty slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemohMarkdownAssetTest {
    @get:Rule val compose = createComposeRule()

    private fun turn(
        text: String,
        assets: String = "",
        id: String = "a1",
        role: String = "assistant",
    ): MemohTurn = decodeTurn(
        JSONObject()
            .put("id", id)
            .put("role", role)
            .put("turn_id", "t1")
            .put("text", text)
            .apply { if (assets.isNotBlank()) put("assets", JSONArray(assets)) },
    )

    private fun cache() = java.nio.file.Files.createTempDirectory("memoh-assets").toFile()

    @Test fun markdownImageTargetsFollowTheOrderTheyAppearIn() {
        val text = "first\n\n![a](/data/a.png)\n\ntext\n\n![b](/data/b.png)"
        assertEquals(listOf("/data/a.png", "/data/b.png"), markdownImageTargets(text))
    }

    @Test fun angleBracketedAndSpacedTargetsAreStillRead() {
        val text = "![a](</data/a b.png>)"
        assertEquals(listOf("/data/a b.png"), markdownImageTargets(text))
    }

    @Test fun plainTextHasNoImageTargets() {
        assertTrue(markdownImageTargets("no pictures here").isEmpty())
    }

    @Test fun assetsDecodeByHashAndKeepTheirOrdinal() {
        val decoded = decodeAssets(
            JSONArray(
                """[{"id":"x","ordinal":1,"role":"markdown","mime":"image/png","content_hash":"h2","size_bytes":9},
                   {"id":"y","ordinal":0,"role":"markdown","mime":"image/png","content_hash":"h1","size_bytes":5},
                   {"id":"z","ordinal":2,"role":"markdown","mime":"image/png"}]""",
            ),
        )
        // A row without a hash names no file, so it cannot be fetched.
        assertEquals(listOf("h1", "h2"), decoded.map { it.contentHash })
        assertEquals(listOf(0, 1), decoded.map { it.ordinal })
    }

    @Test fun theLinkIsPairedWithTheAssetThatHoldsTheBytesNotWithTheTextField() {
        val cacheDir = cache()
        try {
            val reply = turn(
                "ttnk status\n\n![ttnk status](/data/ttnk-status.png)",
                """[{"id":"a","ordinal":0,"role":"markdown","mime":"image/png","content_hash":"hash-ttnk","name":"ttnk-status.png","size_bytes":12}]""",
            )
            val linked = markdownImageLinks(reply)
            assertEquals(setOf("/data/ttnk-status.png"), linked.keys)
            assertEquals("hash-ttnk", linked.values.single().contentHash)

            // Before the bytes exist the link is known but has no file yet.
            assertNull(linked.values.single().cachedFile(cacheDir))

            val target = linked.values.single().cachedTarget(cacheDir)
            target.parentFile?.mkdirs()
            target.writeBytes(ByteArray(12) { 3 })
            assertTrue(linked.values.single().cachedFile(cacheDir)!!.isFile)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test fun theProjectedMessageCarriesTheImageSoTheRendererCanDrawIt() {
        val cacheDir = cache()
        try {
            val reply = turn(
                "ttnk status\n\n![ttnk status](/data/ttnk-status.png)",
                """[{"id":"a","ordinal":0,"role":"markdown","mime":"image/png","content_hash":"hash-1","name":"t.png","size_bytes":8}]""",
            )
            // No file yet: the map exists, but the entry has no attachment to draw.
            val before = memohTranscriptRows(
                listOf(reply), emptyList(), null, emptyList(), null, "", false, cacheDir,
            ).single { it.participant == Participant.MODEL }
            assertEquals(setOf("/data/ttnk-status.png"), before.markdownImages.keys)
            assertNull(before.markdownImages.values.single().attachment)

            val asset = reply.assets.single()
            val target = asset.cachedTarget(cacheDir)
            target.parentFile?.mkdirs()
            target.writeBytes(ByteArray(8) { 1 })

            val after = memohTranscriptRows(
                listOf(reply), emptyList(), null, emptyList(), null, "", false, cacheDir,
            ).single { it.participant == Participant.MODEL }
            val attachment = after.markdownImages.values.single().attachment
            assertEquals(target.absolutePath, attachment?.path)
            assertEquals("image/png", attachment?.mimeType)
            assertEquals("hash-1", attachment?.sha256)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test fun aDataUriReferenceGetsNoAssetAndIsNotExpectedToRender() {
        // The server does not accept the data scheme, so no asset is registered and the client
        // must not invent one from the text.
        val reply = turn("![inline](data:image/png;base64,aGk=)")
        assertTrue(reply.assets.isEmpty())
        assertTrue(markdownImageLinks(reply).isEmpty())
    }

    @Test fun anAssetDownloadsIntoItsHashSlotAndRenders() {
        val cacheDir = cache()
        try {
            MemohCloudServer().use { server ->
                val png = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
                )
                server.handler = { exchange, request ->
                    if (request.path.endsWith("/media/hash-ttnk")) {
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(200, png.size.toLong())
                        exchange.responseBody.use { it.write(png) }
                        exchange.close()
                        true
                    } else false
                }
                val api = MemohApi().apply { configure(server.gateway, "bearer") }
                val reply = turn(
                    "![ttnk status](/data/ttnk-status.png)",
                    """[{"id":"a","ordinal":0,"role":"markdown","mime":"image/png","content_hash":"hash-ttnk","name":"ttnk-status.png","size_bytes":70}]""",
                )
                val asset = reply.assets.single()
                kotlinx.coroutines.runBlocking {
                    api.downloadAsset("bot-1", asset, asset.cachedTarget(cacheDir))
                }
                assertTrue(asset.cachedFile(cacheDir)!!.isFile)
                val rows = memohTranscriptRows(
                    listOf(reply), emptyList(), null, emptyList(), null, "", false, cacheDir,
                )
                assertEquals(
                    asset.cachedTarget(cacheDir).absolutePath,
                    rows.single { it.participant == Participant.MODEL }
                        .markdownImages.values.single().attachment?.path,
                )
            }
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test fun aReplyWithPicturesStillRendersItsText() {
        compose.setContent {
            AgoraTheme {
                androidx.compose.material3.Text("ttnk status")
            }
        }
        compose.onNodeWithText("ttnk status").assertIsDisplayed()
    }
}
