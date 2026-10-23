package com.newoether.agora.memoh

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemohAttachmentBudgetTest {
    @Test
    fun `oversized stream consumes only remaining allowance plus one byte`() {
        val stream = ByteArrayInputStream(ByteArray(8192))

        assertEquals(4, readAttachmentAtMost(stream, 3).size)
        assertEquals(8188, stream.available())
    }

    @Test
    fun `full budget permits only a single probe byte`() {
        val stream = ByteArrayInputStream(ByteArray(8192))

        assertEquals(1, readAttachmentAtMost(stream, 0).size)
        assertEquals(8191, stream.available())
    }

    @Test
    fun `empty short and exact allowance streams are preserved`() {
        for (size in listOf(0, 2, 4)) {
            val bytes = ByteArray(size) { it.toByte() }
            assertArrayEquals(bytes, readAttachmentAtMost(ByteArrayInputStream(bytes), 4))
        }
    }

    @Test
    fun `short reads and a zero length read do not truncate or spin`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val stream = object : InputStream() {
            var position = 0
            var firstRead = true
            override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (firstRead) {
                    firstRead = false
                    return 0
                }
                assertTrue(length > 0)
                val next = read()
                if (next < 0) return -1
                buffer[offset] = next.toByte()
                return 1
            }
        }

        assertArrayEquals(bytes.copyOf(4), readAttachmentAtMost(stream, 3))
        assertEquals(4, stream.position)
    }

    @Test
    fun `repeated selections and removals update the same draft allowance`() {
        var draft = listOf(
            MemohAttachment(id = "first", size = MAX_ATTACHMENT_BYTES),
            MemohAttachment(id = "other", size = MAX_ATTACHMENT_BYTES - 3),
        )
        assertEquals(3L, remainingAttachmentBytes(draft))
        val bytes = readAttachmentAtMost(ByteArrayInputStream(byteArrayOf(1, 2)), remainingAttachmentBytes(draft))
        draft = draft + MemohAttachment(id = "second", size = bytes.size.toLong())
        assertEquals(1L, remainingAttachmentBytes(draft))

        draft = draft + MemohAttachment(id = "third", size = 1)
        assertEquals(0L, remainingAttachmentBytes(draft))
        draft = draft.filterNot { it.id == "second" }
        assertEquals(2L, remainingAttachmentBytes(draft))
    }

    @Test
    fun `two full 8 MiB files fit and a third is rejected before reading`() {
        assertEquals(8L * 1024 * 1024, MAX_ATTACHMENT_BYTES)
        assertEquals(16L * 1024 * 1024, MAX_DRAFT_ATTACHMENT_BYTES)
        val draft = List(2) { MemohAttachment(size = MAX_ATTACHMENT_BYTES) }
        assertNull(attachmentDraftError(draft))
        assertEquals(0L, remainingAttachmentBytes(draft))
        assertNotNull(attachmentPickerError(draft))
        assertNotNull(attachmentDraftError(draft + MemohAttachment(size = 1)))
        assertNotNull(attachmentDraftError(draft + MemohAttachment(size = MAX_ATTACHMENT_BYTES)))
    }

    @Test
    fun `per file limit remains independent of draft budget`() {
        assertNull(attachmentDraftError(listOf(MemohAttachment(size = MAX_ATTACHMENT_BYTES))))
        assertNotNull(attachmentDraftError(listOf(MemohAttachment(size = MAX_ATTACHMENT_BYTES + 1))))
        assertNotNull(attachmentDraftError(listOf(MemohAttachment(size = -1))))
    }

    @Test
    fun `small injected byte budgets accept exact total and reject one byte over`() {
        val draft = listOf(MemohAttachment(size = 4), MemohAttachment(size = 4))
        assertNull(attachmentDraftError(draft, maxBytes = 8, maxFileBytes = 4))
        assertEquals(0L, remainingAttachmentBytes(draft, maxBytes = 8))
        assertNotNull(attachmentDraftError(draft + MemohAttachment(size = 1), maxBytes = 8, maxFileBytes = 4))
        assertNotNull(attachmentDraftError(listOf(MemohAttachment(size = 5)), maxBytes = 8, maxFileBytes = 4))
        assertNotNull(attachmentDraftError(List(3) { MemohAttachment(size = Long.MAX_VALUE) },
            maxBytes = Long.MAX_VALUE, maxFileBytes = Long.MAX_VALUE))
    }

    @Test
    fun `count limit applies to empty files across selections and deletion restores a slot`() {
        assertEquals(10, MAX_DRAFT_ATTACHMENTS)
        var draft = List(9) { MemohAttachment(id = "$it", size = 0) }
        assertNull(attachmentPickerError(draft))
        draft = draft + MemohAttachment(id = "tenth", size = 0)
        assertNull(attachmentDraftError(draft))
        assertNotNull(attachmentPickerError(draft))
        assertNotNull(attachmentDraftError(draft + MemohAttachment(size = 0)))
        draft = draft.filterNot { it.id == "0" }
        assertNull(attachmentPickerError(draft))
        assertNull(attachmentDraftError(draft + MemohAttachment(size = 0)))
    }

    @Test
    fun `live picker admission stops before reading excess and permits replacement after deletion`() {
        var draft = List(9) { MemohAttachment(id = "$it", size = 1) }
        var reads = 0
        fun select() {
            if (attachmentPickerError(draft) != null) return
            reads++
            val limit = minOf(MAX_ATTACHMENT_BYTES, remainingAttachmentBytes(draft))
            val bytes = readAttachmentAtMost(ByteArrayInputStream(byteArrayOf(1)), limit)
            draft = draft + MemohAttachment(id = "picked", size = bytes.size.toLong())
        }
        select()
        select()
        assertEquals(1, reads)
        draft = draft.filterNot { it.id == "picked" }
        select()
        assertEquals(2, reads)
        assertEquals(10, draft.size)
    }

    @Test
    fun `removing a full size file restores bytes for a later selection`() {
        var draft = List(2) { MemohAttachment(id = "$it", size = MAX_ATTACHMENT_BYTES) }
        assertNotNull(attachmentPickerError(draft))
        draft = draft.filterNot { it.id == "0" }
        assertEquals(MAX_ATTACHMENT_BYTES, remainingAttachmentBytes(draft))
        assertNull(attachmentPickerError(draft))
        assertNull(attachmentDraftError(draft + MemohAttachment(size = MAX_ATTACHMENT_BYTES)))
    }

    @Test
    fun `budget saturates at zero`() {
        assertEquals(0L, remainingAttachmentBytes(listOf(MemohAttachment(size = Long.MAX_VALUE))))
    }
}
