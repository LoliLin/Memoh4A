package com.newoether.agora.memoh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A history page is capped at [HISTORY_PAGE_SIZE] rows.
 *
 * Regression: the first load decided `olderMessagesAvailable = result.isNotEmpty()`, so any
 * conversation with at least one message kept offering "Load earlier messages" forever, even
 * after reaching the real start of history. Only a page shorter than the cap proves the end.
 */
@RunWith(RobolectricTestRunner::class)
class MemohHistoryPagingTest {

    private fun turns(count: Int): List<MemohTurn> =
        (0 until count).map { MemohTurn("m$it", "user", "message $it") }

    @Test
    fun `a full page still has older rows to fetch`() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, request ->
                if (request.path.endsWith("/messages")) {
                    val items = turns(HISTORY_PAGE_SIZE)
                    server.reply(
                        exchange,
                        """{"items":[${items.joinToString(",") {
                            """{"id":"${it.id}","role":"user","text":"${it.text}"}"""
                        }}]}""",
                    )
                    true
                } else false
            }
            val api = MemohApi().apply { configure(server.gateway) }
            api.selectTeam("a")
            val page = api.history("shared", "chat")

            assertEquals(HISTORY_PAGE_SIZE, page.size)
            assertTrue(
                "a page at the cap cannot prove the start of history",
                page.size >= HISTORY_PAGE_SIZE,
            )
        }
    }

    @Test
    fun `a short page is the start of history`() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, request ->
                if (request.path.endsWith("/messages")) {
                    server.reply(
                        exchange,
                        """{"items":[{"id":"only","role":"user","text":"first"}]}""",
                    )
                    true
                } else false
            }
            val api = MemohApi().apply { configure(server.gateway) }
            api.selectTeam("a")
            val page = api.history("shared", "chat")

            assertEquals(1, page.size)
            assertFalse(
                "a page under the cap means there is nothing older",
                page.size >= HISTORY_PAGE_SIZE,
            )
        }
    }

    @Test
    fun `history pages use server turn positions when present`() = runBlocking {
        MemohCloudServer().use { server ->
            server.handler = { exchange, request ->
                if (request.path.endsWith("/messages")) {
                    server.reply(exchange, """{"items":[
                        {"id":"later","role":"user","turn_id":"two","turn_position":2},
                        {"id":"answer","role":"assistant","turn_id":"one","turn_position":1},
                        {"id":"question","role":"user","turn_id":"one","turn_position":1}
                    ]}""")
                    true
                } else false
            }
            val api = MemohApi().apply { configure(server.gateway) }
            api.selectTeam("a")
            assertEquals(listOf("question", "answer", "later"), api.history("shared", "chat").map { it.id })
        }
    }

    @Test
    fun `the request asks for exactly one page`() = runBlocking {
        MemohCloudServer().use { server ->
            val api = MemohApi().apply { configure(server.gateway) }
            api.selectTeam("a")
            api.history("shared", "chat", beforeMessageId = "m40")

            val seen = server.requests.last { it.path.endsWith("/messages") }
            val query = seen.query.orEmpty()
            assertTrue("page size not sent: $query", query.contains("limit=$HISTORY_PAGE_SIZE"))
            assertTrue("cursor not sent: $query", query.contains("before_message_id=m40"))
        }
    }
}
