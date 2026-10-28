package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemohModelsTest {
    @Test
    fun `user and assistant sharing turn ID keep distinct row keys`() {
        val user = decodeTurn(JSONObject("""{"role":"user","turn_id":"same","id":"user-row","text":"hello"}"""))
        val reply = decodeTurn(JSONObject("""{"role":"assistant","turn_id":"same","id":"assistant-row","messages":[]}"""))
        assertEquals("user-row", user.id)
        assertEquals("assistant-row", reply.id)
        assertNotEquals(user.id, reply.id)
    }

    @Test
    fun `legacy turns without row ID stay role disambiguated`() {
        val user = decodeTurn(JSONObject("""{"role":"user","turn_id":"same"}"""))
        val reply = decodeTurn(JSONObject("""{"role":"assistant","turn_id":"same"}"""))
        assertNotEquals(user.id, reply.id)
    }

    @Test
    fun `a server-relative url is never handed to the image loader as a path`() {
        val cacheDir = java.nio.file.Files.createTempDirectory("memoh-rel-url").toFile()
        try {
            // Coil cannot open `/bots/.../media/...`; passing it through produced a card whose
            // picture slot stayed empty forever.
            val relative = MemohAttachment(
                id = "att-rel", name = "shot.png", mime = "image/png", type = "image",
                url = "/bots/bot-1/media/abc",
            )
            val absolute = MemohAttachment(
                id = "att-abs", name = "shot2.png", mime = "image/png", type = "image",
                url = "https://cdn.test/shot2.png",
            )
            val turn = MemohTurn(
                "a1", "assistant", turnId = "t1",
                blocks = listOf(MemohBlock(1, "attachments", "", attachments = listOf(relative, absolute))),
            )
            val images = memohTranscriptRows(
                listOf(turn), emptyList(), null, emptyList(), null, "", false, cacheDir,
            ).first { it.participant == com.newoether.agora.model.Participant.MODEL }
                .segments.orEmpty().flatMap { it.toolImages }
            assertEquals(listOf("https://cdn.test/shot2.png"), images.map { it.path })
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `a bot picture held on a message block reaches the bubble once downloaded`() {
        val cacheDir = java.nio.file.Files.createTempDirectory("memoh-block-image").toFile()
        try {
            val attachment = MemohAttachment(
                id = "att-1", name = "chart.png", mime = "image/png", type = "image",
                contentHash = "hash-1", botId = "bot-1",
            )
            val block = MemohBlock(
                id = 3, type = "attachments", text = "",
                attachments = listOf(attachment),
            )
            val turn = MemohTurn("a1", "assistant", turnId = "t1", blocks = listOf(block))

            // Before the download lands there is no file, so the row legitimately has no image.
            assertTrue(
                memohTranscriptRows(listOf(turn), emptyList(), null, emptyList(), null, "", false, cacheDir)
                    .first { it.participant == com.newoether.agora.model.Participant.MODEL }
                    .segments.orEmpty().flatMap { it.toolImages }.isEmpty(),
            )

            val target = attachment.cachedTarget(cacheDir)
            target.parentFile?.mkdirs()
            target.writeBytes(ByteArray(64) { 7 })

            val images = memohTranscriptRows(
                listOf(turn), emptyList(), null, emptyList(), null, "", false, cacheDir,
            ).first { it.participant == com.newoether.agora.model.Participant.MODEL }
                .segments.orEmpty().flatMap { it.toolImages }
            assertEquals(listOf(target.absolutePath), images.map { it.path })
            assertEquals("image/png", images.single().mimeType)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `tool block decodes approval user input and detail fields`() {
        val json = JSONObject(
            """{"id":7,"type":"tool","name":"ask_user","input":{"prompt":"Choose"},""" +
                """"output":{"ok":true},"diff":"a -> b","approval":{"approval_id":"a1","can_approve":true,""" +
                """"options":[{"id":"yes","name":"Allow","kind":"allow_once"}]},"user_input":""" +
                """{"user_input_id":"u1","status":"pending","can_respond":true,"questions":[""" +
                """{"id":"q1","text":"Option?","kind":"single_select","required":true,"options":[""" +
                """{"id":"x","label":"Option X","description":"First"}]}]}}"""
        )
        val block = decodeBlocks(JSONArray().put(json)).single()
        assertEquals("ask_user", block.toolName)
        assertEquals("a1", block.approvalId)
        assertTrue(block.canApprove)
        assertEquals("yes", block.approvalOptions.single().id)
        assertEquals("u1", block.userInputId)
        assertTrue(block.canRespond)
        assertEquals("q1", block.questions.single().id)
        assertTrue(block.questions.single().required)
        assertEquals("x", block.questions.single().options.single().id)
        assertTrue(block.toolInput.contains("prompt"))
        assertTrue(block.toolOutput.contains("ok"))
        assertEquals("a -> b", block.toolDiff)
    }

    @Test
    fun `terminal tool without user input has no active interactions`() {
        val block = decodeBlocks(JSONArray().put(JSONObject("""{"id":1,"type":"tool","running":false}"""))).single()
        assertFalse(block.canApprove)
        assertFalse(block.canRespond)
        assertTrue(block.questions.isEmpty())
    }

    @Test
    fun `live reply replaces matching history row without duplicating its user`() {
        val history = listOf(
            MemohTurn("u1", "user", "First", turnId = "turn-1"),
            MemohTurn("a1", "assistant", "Old answer", turnId = "turn-1"),
            MemohTurn("u2", "user", "Later", turnId = "turn-2"),
        )
        val rows = memohTranscriptRows(
            history, listOf(MemohTurn("runtime-u1", "user", "First", turnId = "turn-1")),
            "First", emptyList(), "turn-1", "turn-1", true, null,
        )
        assertEquals(3, rows.size)
        assertEquals("First", rows[0].text)
        assertEquals(MEMOH_LIVE_MESSAGE_ID, rows[1].id)
        assertEquals("Later", rows[2].text)
    }

    @Test
    fun `remote run user appears before its historical assistant`() {
        val rows = memohTranscriptRows(
            listOf(MemohTurn("a", "assistant", "answer", turnId = "turn")),
            listOf(MemohTurn("u", "user", "question", turnId = "turn")),
            null, emptyList(), null, "turn", true, null,
        )
        assertEquals(listOf("question", ""), rows.map { it.text })
        assertEquals(MEMOH_LIVE_MESSAGE_ID, rows.last().id)
    }

    @Test
    fun `server turn positions order history and runtime users before their answers`() {
        val earlierUser = decodeTurn(JSONObject("""{"id":"u1","role":"user","turn_id":"one","turn_position":1,"text":"first"}"""))
        val earlierAnswer = decodeTurn(JSONObject("""{"id":"a1","role":"assistant","turn_id":"one","turn_position":1,"text":"answer one"}"""))
        val laterUser = decodeTurn(JSONObject("""{"id":"u2","role":"user","turn_id":"two","turn_position":2,"text":"second"}"""))
        val laterAnswer = decodeTurn(JSONObject("""{"id":"a2","role":"assistant","turn_id":"two","turn_position":2,"text":"answer two"}"""))
        val rows = memohTranscriptRows(
            listOf(laterAnswer, earlierAnswer, earlierUser), listOf(laterUser),
            null, emptyList(), null, "two", true, null,
        )
        assertEquals(listOf("first", "answer one", "second", ""), rows.map { it.text })
        assertEquals(MEMOH_LIVE_MESSAGE_ID, rows.last().id)
    }

    @Test
    fun `legacy history without complete positions keeps server order`() {
        val first = MemohTurn("a", "assistant", turnPosition = 2)
        val legacy = MemohTurn("b", "user")
        val last = MemohTurn("c", "user", turnPosition = 1)
        assertEquals(listOf(first, legacy, last), memohOrderTurns(listOf(first, legacy, last)))
    }

    @Test
    fun `runtime user without a position inherits its answer's position`() {
        // The runtime echo of a user turn carries no turn_position; the persisted answer does.
        val answer = MemohTurn("a1", "assistant", "answer", turnId = "t1", turnPosition = 5)
        val echo = MemohTurn("u1", "user", "question", turnId = "t1")
        val laterUser = MemohTurn("u2", "user", "next", turnId = "t2", turnPosition = 6)
        val rows = memohTranscriptRows(
            listOf(answer, laterUser), listOf(echo), null, emptyList(), null, "t1", true, null,
        )
        assertEquals(listOf("question", "", "next"), rows.map { it.text })
    }

    @Test
    fun `an unresolvable row keeps server order instead of being moved`() {
        val placed = MemohTurn("a", "assistant", turnPosition = 9)
        val orphan = MemohTurn("b", "user", "unknown turn", turnId = "missing")
        val earlier = MemohTurn("c", "user", "earlier", turnPosition = 1)
        assertEquals(listOf(placed, orphan, earlier), memohOrderTurns(listOf(placed, orphan, earlier)))
    }

    @Test
    fun `history summary does not repeat assistant text blocks around a tool`() {
        val reply = MemohTurn("a", "assistant", "Before after", listOf(
            MemohBlock(0, "text", "Before"),
            MemohBlock(1, "tool", "Completed", toolName = "search"),
            MemohBlock(2, "text", "after"),
        ))
        val row = memohMessages(listOf(reply), null, emptyList(), emptyList(), false).single()
        assertEquals(listOf("Before", "after"), row.segments!!.filter { it.type == "answer" }.map { it.content })
    }

    @Test
    fun `text-only historical answers still render`() {
        val reply = MemohTurn("a", "assistant", "The answer")
        val row = memohMessages(listOf(reply), null, emptyList(), emptyList(), false).single()
        assertEquals("The answer", row.segments!!.single().content)
    }

    @Test
    fun `bot canManage requires manage permission when list is non-empty`() {
        val implicit = MemohBot("b1", "Bot 1")
        assertTrue(implicit.canManage)

        val manager = MemohBot("b2", "Bot 2", currentUserPermissions = listOf("chat", "manage"))
        assertTrue(manager.canManage)

        val viewer = MemohBot("b3", "Bot 3", currentUserPermissions = listOf("chat"))
        assertFalse(viewer.canManage)
    }

    @Test
    fun `user grant model preserves id and permissions`() {
        val grant = MemohUserGrant("g1", "b1", "u1", "User One", "user1", "", false, listOf("chat", "manage"))
        assertEquals("g1", grant.id)
        assertEquals("u1", grant.userId)
        assertTrue(grant.permissions.contains("manage"))
    }
    @Test
    fun `turn with attachments decodes list of UIAttachment`() {
        val json = JSONObject(
            """{"role":"user","id":"t1","text":"photo","attachments":[{"id":"a1","name":"sunset.jpg","mime":"image/jpeg","type":"image","size":2048,"url":"data:image/jpeg;base64,abc"}]}"""
        )
        val turn = decodeTurn(json)
        assertEquals(1, turn.attachments.size)
        val att = turn.attachments[0]
        assertEquals("a1", att.id)
        assertEquals("sunset.jpg", att.name)
        assertEquals("image/jpeg", att.mime)
        assertEquals("image", att.type)
        assertEquals(2048L, att.size)
        assertEquals("data:image/jpeg;base64,abc", att.url)
    }

    @Test
    fun `block with attachments decodes attachments properly`() {
        val json = JSONObject(
            """{"id":1,"type":"attachments","attachments":[{"id":"f1","name":"document.pdf","mime":"application/pdf","type":"file","size":5000}]}"""
        )
        val blocks = decodeBlocks(JSONArray().put(json))
        assertEquals(1, blocks.size)
        assertEquals(1, blocks[0].attachments.size)
        assertEquals("document.pdf", blocks[0].attachments[0].name)
        assertEquals("file", blocks[0].attachments[0].type)
    }
}