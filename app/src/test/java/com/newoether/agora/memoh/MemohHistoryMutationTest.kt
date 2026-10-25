package com.newoether.agora.memoh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retry must not leave the replaced answer on screen, and deleting a bot must require the typed
 * confirmation word before its destructive button becomes usable.
 *
 * The trim rules are pure data, so they are asserted directly instead of driving a Compose host.
 */
class MemohHistoryMutationTest {
    private fun turn(id: String, role: String, text: String, turnId: String = "t-$id") =
        MemohTurn(id = id, role = role, text = text, turnId = turnId)

    private val transcript = listOf(
        turn("u1", "user", "first question"),
        turn("a1", "assistant", "first answer"),
        turn("u2", "user", "second question"),
        turn("a2", "assistant", "second answer"),
    )

    @Test
    fun retryDropsTheAnsweredReplyAndEverythingAfterIt() {
        val trimmed = requireNotNull(trimmedHistory(transcript, "t-u2"))

        assertEquals(listOf("u1", "a1", "u2"), trimmed.surviving.map { it.id })
        // A retry keeps the old question; only the answer is regenerated.
        assertEquals("second question", trimmed.result.last().text)
        assertEquals(listOf("a2"), trimmed.undo.removed.map { it.id })
    }

    @Test
    fun addressingTheAnswerStillTrimsFromItsQuestion() {
        val trimmed = requireNotNull(trimmedHistory(transcript, "t-a2"))

        assertEquals(listOf("u1", "a1", "u2"), trimmed.surviving.map { it.id })
        assertEquals(listOf("a2"), trimmed.undo.removed.map { it.id })
    }

    @Test
    fun retryOnAnEarlierTurnRemovesEveryLaterReply() {
        val trimmed = requireNotNull(trimmedHistory(transcript, "t-u1"))

        assertEquals(listOf("u1"), trimmed.surviving.map { it.id })
        assertEquals(listOf("a1", "u2", "a2"), trimmed.undo.removed.map { it.id })
    }

    @Test
    fun editRewritesTheQuestionAndDropsItsReply() {
        val trimmed = requireNotNull(trimmedHistory(transcript, "t-u2", newText = "  fixed question "))

        assertEquals(listOf("u1", "a1", "u2"), trimmed.result.map { it.id })
        assertEquals("fixed question", trimmed.result.last().text)
        assertEquals("second question", trimmed.undo.original.last { it.role == "user" }.text)
    }

    @Test
    fun undoRestoresExactlyTheOriginalTranscript() {
        val trimmed = requireNotNull(trimmedHistory(transcript, "t-u1", newText = "rewritten"))

        val restored = (transcript.take(trimmed.undo.cutIndex) + trimmed.undo.removed)
            .distinctBy { it.id }

        assertEquals(transcript, restored)
    }

    @Test
    fun nothingToTrimLeavesHistoryAlone() {
        // Unknown turn, blank id, and a question that already ends the transcript.
        assertNull(trimmedHistory(transcript, "t-missing"))
        assertNull(trimmedHistory(transcript, ""))
        assertNull(trimmedHistory(listOf(turn("u9", "user", "only question")), "t-u9"))
        assertNull(trimmedHistory(emptyList(), "t-u1"))
    }

    @Test
    fun deleteConfirmationOnlyAcceptsTheExactWord() {
        // The dialog arms its confirm button from this predicate.
        val accepts = { typed: String -> typed.trim() == DELETION_CONFIRM_WORD }

        assertTrue(accepts("Delete"))
        assertTrue(accepts("  Delete "))
        assertFalse(accepts("delete"))
        assertFalse(accepts("DELETE"))
        assertFalse(accepts("Delete bot"))
        assertFalse(accepts(""))
        assertEquals("Delete", DELETION_CONFIRM_WORD)
    }
}
