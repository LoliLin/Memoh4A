package com.newoether.agora.memoh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemohAttachmentViewsTest {
    private val bytes = byteArrayOf(0, 1, 2, 127, -128, -1)
    private val encoded = "AAECf4D/"

    @Test
    fun `raw base64 resolves to bytes without requiring a mime type`() {
        val attachment = MemohAttachment(type = "image", base64 = encoded)

        assertArrayEquals(bytes, attachment.imageSource() as ByteArray)
    }

    @Test
    fun `base64 field accepts legacy data URLs`() {
        val attachment = MemohAttachment(base64 = "data:image/png;base64,$encoded")

        assertArrayEquals(bytes, attachment.imageSource() as ByteArray)
    }

    @Test
    fun `data URL in url field is decoded and takes precedence over base64`() {
        val attachment = MemohAttachment(
            url = "DATA:image/png;BASE64,$encoded",
            base64 = "invalid!",
        )

        assertArrayEquals(bytes, attachment.imageSource() as ByteArray)
    }

    @Test
    fun `ordinary URL is returned unchanged and takes precedence over base64`() {
        val url = "https://example.com/image.png?token=abc#preview"
        val attachment = MemohAttachment(url = url, base64 = encoded)

        assertSame(url, attachment.imageSource())
    }

    @Test
    fun `missing or malformed inline data returns null`() {
        val invalidSources = listOf(
            "",
            " \n\t",
            "!!!",
            "AAEC!f4D/",
            "A",
            "AA=ECf4D/",
            "data:image/png;base64",
            "data:image/png;base64,",
            "data:image/png;base64,!!!",
            "data:image/png;base64,A",
            "data:image/png,plain-text",
        )
        for (source in invalidSources) {
            assertNull("base64=$source", MemohAttachment(base64 = source).imageSource())
            if (source.startsWith("data:")) {
                assertNull(
                    "url=$source",
                    MemohAttachment(url = source, base64 = encoded).imageSource(),
                )
            }
        }
        assertNull(MemohAttachment().imageSource())
    }

    @Test
    fun `raw base64 accepts line wrapping and padding`() {
        val attachment = MemohAttachment(base64 = " AAEC\r\nf4D/\tAA== ")

        assertArrayEquals(bytes + byteArrayOf(0), attachment.imageSource() as ByteArray)
    }
}
