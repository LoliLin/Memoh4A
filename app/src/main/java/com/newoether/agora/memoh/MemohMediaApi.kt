package com.newoether.agora.memoh

import android.net.Uri
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream

internal fun MemohApi.mediaCacheDirectory(root: File, botId: String): File {
    fun String.component() = replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "default" }
    return File(root, "memoh-media/${scope.identity.mediaCacheKey}/${teamId.component()}/${botId.component()}")
}

/** Current Memoh stores media by content_hash; older installations return container paths. */
internal fun MemohApi.attachmentDownloadUrl(botId: String, attachment: MemohAttachment): String? = when {
    attachment.contentHash.isNotBlank() -> "$baseUrl/bots/${Uri.encode(attachment.botId.ifBlank { botId })}/media/${Uri.encode(attachment.contentHash)}"
    attachment.path.isNotBlank() -> "$baseUrl/bots/${Uri.encode(botId)}/container/fs/download?path=${Uri.encode(attachment.path)}"
    attachment.url.startsWith("data:", ignoreCase = true) -> null
    attachment.url.startsWith("/bots/") -> baseUrl + attachment.url
    attachment.url.isNotBlank() -> scope.identity.endpoints.origin.toHttpUrl().resolve(attachment.url)?.toString()
    else -> null
}

internal suspend fun MemohApi.downloadAttachment(botId: String, attachment: MemohAttachment, target: File) {
    val url = attachmentDownloadUrl(botId, attachment) ?: throw IOException("No downloadable attachment source")
    downloadMedia(url, target)
}

/** Private media carries the current sign-in only on the configured origin, including redirects. */
internal suspend fun MemohApi.downloadMedia(source: String, target: File, maxBytes: Long = 64L * 1024 * 1024) =
    withBusinessScope { captured ->
        val origin = captured.identity.endpoints.origin.toHttpUrl()
        var url = origin.resolve(source) ?: source.toHttpUrlOrNull() ?: throw IOException("Invalid media URL")
        repeat(6) { hop ->
            require(url.username.isEmpty() && url.password.isEmpty()) { "Invalid media URL" }
            require(url.scheme == "https" || MemohEndpoints.isLocalhost(url.host)) { "Media requires HTTPS" }
            val sameOrigin = url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port
            val request = (if (sameOrigin) businessRequest("/users/me", captured).url(url)
                else Request.Builder().url(url).tag(MemohTransport.Scope::class.java, captured))
                .header("Accept", "image/*, application/octet-stream, */*").get().build()
            val client = captured.identity.client.newBuilder().followRedirects(false).followSslRedirects(false)
                .cookieJar(if (sameOrigin) captured.identity.cookies else CookieJar.NO_COOKIES).build()
            client.newCall(request).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    val location = response.header("Location") ?: throw IOException("Empty media redirect")
                    val next = url.resolve(location) ?: throw IOException("Invalid media redirect")
                    if (url.scheme == "https" && next.scheme != "https") throw IOException("Insecure media redirect")
                    if (hop == 5) throw IOException("Too many media redirects")
                    url = next
                } else {
                    if (!response.isSuccessful) throw MemohHttpException(response.code, "Media download failed (${response.code})")
                    val body = response.body ?: throw IOException("Empty media response")
                    if (body.contentLength() > maxBytes) throw IOException("Media file is too large")
                    writeMemohMediaAtomically(target) { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > maxBytes) throw IOException("Media file is too large")
                                output.write(buffer, 0, count)
                            }
                            if (total == 0L) throw IOException("Empty media response")
                        }
                        requireCurrent(captured)
                    }
                    return@withBusinessScope
                }
            }
        }
    }

/** A cancelled or failed transfer must never leave a partial file that looks like a cache hit. */
internal fun writeMemohMediaAtomically(target: File, write: (OutputStream) -> Unit) {
    val parent = target.parentFile ?: throw IOException("Missing media cache directory")
    if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot open media cache")
    val staging = File.createTempFile("media-", ".part", parent)
    try {
        staging.outputStream().use(write)
        if (!staging.renameTo(target) && !(target.isFile && target.length() > 0))
            throw IOException("Cannot finish media download")
    } finally {
        staging.delete()
    }
}
