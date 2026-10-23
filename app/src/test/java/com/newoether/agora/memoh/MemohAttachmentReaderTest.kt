package com.newoether.agora.memoh

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.database.MatrixCursor
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class MemohAttachmentReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class DocumentProvider(val file: File, val declaredSize: Long?) : ContentProvider() {
        var opened = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?) =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>("picked.png", declaredSize))
            }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            opened = true
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("unused")
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int = error("unused")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("unused")
    }

    private fun provider(declaredSize: Long?): DocumentProvider {
        val file = temporaryFolder.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        return DocumentProvider(file, declaredSize).also {
            ShadowContentResolver.registerProviderInternal("attachment-budget-test", it)
            // Only Metadata behaviour comes from the provider here; the stream is served by the
            // shadow, which is the path Robolectric's ContentResolver actually opens.
            Shadows.shadowOf(RuntimeEnvironment.getApplication().contentResolver)
                .registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
        }
    }

    private val uri = Uri.parse("content://attachment-budget-test/picked.png")

    @Test
    fun `unknown and understated provider sizes retain actual bytes and raw base64 only`() = runBlocking {
        for (declaredSize in listOf(null, -1L, 0L, 1L)) {
            provider(declaredSize)
            val attachment = resolveAttachmentFromUri(RuntimeEnvironment.getApplication(), uri, 4)
            assertNotNull(attachment)
            assertEquals(4L, attachment!!.size)
            assertEquals("", attachment.url)
            assertEquals("AQIDBA==", attachment.base64)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), Base64.decode(attachment.base64, Base64.NO_WRAP))
            assertEquals(MAX_DRAFT_ATTACHMENT_BYTES - 4, remainingAttachmentBytes(listOf(attachment)))
        }
    }

    @Test
    fun `actual bytes exceeding remaining allowance are rejected despite understated size`() = runBlocking {
        provider(1)
        try {
            resolveAttachmentFromUri(RuntimeEnvironment.getApplication(), uri, 3)
            throw AssertionError("Expected remaining allowance rejection")
        } catch (e: AttachmentTooLargeException) {
            assertEquals(4L, e.bytes)
        }
    }

    @Test
    fun `per file limit rejects declared size before opening despite available draft budget`() = runBlocking {
        val provider = provider(MAX_ATTACHMENT_BYTES + 1)
        try {
            resolveAttachmentFromUri(RuntimeEnvironment.getApplication(), uri)
            throw AssertionError("Expected per-file size rejection")
        } catch (e: AttachmentTooLargeException) {
            assertEquals(MAX_ATTACHMENT_BYTES + 1, e.bytes)
            assertTrue(!provider.opened)
        }
    }

    @Test
    fun `declared size above remaining allowance avoids opening the stream`() = runBlocking {
        val provider = provider(4)
        try {
            resolveAttachmentFromUri(RuntimeEnvironment.getApplication(), uri, 3)
            throw AssertionError("Expected declared size rejection")
        } catch (e: AttachmentTooLargeException) {
            assertEquals(4L, e.bytes)
            assertTrue(!provider.opened)
        }
    }
}
