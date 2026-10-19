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
}
