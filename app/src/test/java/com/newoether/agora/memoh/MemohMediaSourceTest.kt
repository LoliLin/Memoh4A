package com.newoether.agora.memoh

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An attachment must be reachable from every source the server may use.
 *
 * The official iOS client resolves one from three places only: inline base64, a container path
 * via `/container/fs/download`, or an absolute url. It never reads `/media/{content_hash}`, so a
 * row whose only identifying field is the hash has to fall through to something that works
 * instead of leaving the bubble with an empty slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MemohMediaSourceTest {

    private fun api(server: String = "https://app.example.test/api/memoh") = MemohApi().apply {
        configure(server)
    }

    private fun attachment(
        contentHash: String = "",
        path: String = "",
        url: String = "",
        type: String = "image",
        mime: String = "image/png",
    ) = MemohAttachment(
        id = "att", name = "chart.png", mime = mime, type = type,
        contentHash = contentHash, path = path, url = url, botId = "bot-1",
    )

    @Test fun aContainerPathIsReachableEvenWhenAHashIsAlsoPresent() {
        val sources = api().attachmentDownloadUrls(
            "bot-1", attachment(contentHash = "abc123", path = "/data/out/chart.png"),
        )
        assertEquals(2, sources.size)
        assertTrue(sources[0].endsWith("/media/abc123"))
        // The container download is what the official client actually uses.
        assertTrue(sources[1].contains("/container/fs/download?path="))
        assertTrue(sources[1].contains("chart.png"))
    }

    @Test fun aHashOnlyRowStillOffersTheContainerFormSoTheDownloadCanFallThrough() {
        val sources = api().attachmentDownloadUrls("bot-1", attachment(contentHash = "abc123"))
        assertEquals(1, sources.size)
        assertTrue(sources.single().endsWith("/media/abc123"))
    }

    @Test fun anAbsoluteUrlIsUsedDirectlyAndAPathIsResolvedAgainstTheOrigin() {
        val absolute = api().attachmentDownloadUrls(
            "bot-1", attachment(url = "https://cdn.example.test/a.png"),
        )
        assertEquals(listOf("https://cdn.example.test/a.png"), absolute)

        val relative = api().attachmentDownloadUrls("bot-1", attachment(url = "/bots/bot-1/media/x"))
        assertEquals(1, relative.size)
        assertTrue(relative.single().startsWith("https://app.example.test/api/memoh/bots/bot-1/media/x"))
    }

    @Test fun anInlineDataUrlIsNotTreatedAsADownloadSource() {
        val sources = api().attachmentDownloadUrls(
            "bot-1", attachment(url = "data:image/png;base64,aGk="),
        )
        assertTrue(sources.isEmpty())
    }

    @Test fun theOwningBotFromTheRowWinsOverTheCallersBot() {
        val attachment = attachment(contentHash = "h").copy(botId = "row-bot")
        assertTrue(api().attachmentDownloadUrls("caller-bot", attachment).single().contains("/bots/row-bot/"))
    }

    @Test fun downloadAttachmentTriesEachSourceUntilOneProducesTheFile() = kotlinx.coroutines.runBlocking {
        val root = java.nio.file.Files.createTempDirectory("memoh-media-src").toFile()
        try {
            val target = File(root, "out.png")
            // Every source fails against an unreachable host: the caller must see the failure
            // rather than a zero-length file that later reads as a cache hit.
            val missing = attachment(contentHash = "h1", path = "/nope.png", url = "/also-nope.png")
            val failure = runCatching {
                api("http://127.0.0.1:1/api/memoh").downloadAttachment("bot-1", missing, target)
            }
            assertTrue(failure.isFailure)
            assertFalse(target.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun aNonImageAnswerIsRejectedInsteadOfCachedAsAPicture() = kotlinx.coroutines.runBlocking {
        MemohCloudServer().use { server ->
            val root = java.nio.file.Files.createTempDirectory("memoh-media-type").toFile()
            try {
                server.handler = { exchange, request ->
                    if (request.path.endsWith("/media/hash-1")) {
                        exchange.responseHeaders.add("Content-Type", "text/html")
                        server.reply(exchange, "<html>login</html>")
                        true
                    } else false
                }
                val target = File(root, "a.png")
                val image = attachment(contentHash = "hash-1")
                val failure = runCatching { api(server.gateway).downloadAttachment("bot-1", image, target) }
                assertTrue("an HTML page is not an image: ${failure.exceptionOrNull()}", failure.isFailure)
                assertFalse("a rejected body must not be cached", target.isFile)
                assertTrue(server.requests.any { it.path.endsWith("/media/hash-1") })
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test fun aRealImageIsWrittenAndCanBeReadBack() = kotlinx.coroutines.runBlocking {
        MemohCloudServer().use { server ->
            val root = java.nio.file.Files.createTempDirectory("memoh-media-ok").toFile()
            try {
                val png = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
                )
                server.handler = { exchange, request ->
                    if (request.path.endsWith("/media/hash-2")) {
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(200, png.size.toLong())
                        exchange.responseBody.use { it.write(png) }
                        exchange.close()
                        true
                    } else false
                }
                val target = File(root, "b.png")
                api(server.gateway).downloadAttachment("bot-1", attachment(contentHash = "hash-2"), target)
                assertTrue(target.isFile)
                assertEquals(png.size.toLong(), target.length())
            } finally {
                root.deleteRecursively()
            }
        }
    }
}
