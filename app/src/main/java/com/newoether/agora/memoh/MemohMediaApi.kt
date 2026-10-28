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

/**
 * Every source an attachment may carry, best-supported first.
 *
 * The official iOS client resolves an attachment from exactly three places: an inline `base64`
 * body, a container `path` through `/container/fs/download`, or an absolute `url`. It never asks
 * for `/media/{content_hash}`, so a server that stores only the hash leaves this client with a
 * URL that resolves to nothing — the bubble keeps its slot and the picture never arrives.
 * The hash form stays first because a server that does implement it is cheaper, but a failure
 * there now falls through to the container path and then to the URL instead of ending the load.
 */
internal fun MemohApi.attachmentDownloadUrls(botId: String, attachment: MemohAttachment): List<String> {
    val owner = Uri.encode(attachment.botId.ifBlank { botId })
    val sources = mutableListOf<String>()
    attachment.contentHash.takeIf(String::isNotBlank)?.let {
        sources += "$baseUrl/bots/$owner/media/${Uri.encode(it)}"
    }
    attachment.path.takeIf(String::isNotBlank)?.let {
        sources += "$baseUrl/bots/${Uri.encode(botId)}/container/fs/download?path=${Uri.encode(it)}"
    }
    if (!attachment.url.startsWith("data:", ignoreCase = true)) {
        attachment.url.takeIf(String::isNotBlank)?.let { url ->
            sources += when {
                url.startsWith("/bots/") -> baseUrl + url
                url.startsWith("http://") || url.startsWith("https://") -> url
                else -> scope.identity.endpoints.origin.toHttpUrl().resolve(url)?.toString().orEmpty()
            }
        }
    }
    return sources.filter { it.isNotBlank() }.distinct()
}

internal suspend fun MemohApi.downloadAttachment(botId: String, attachment: MemohAttachment, target: File) {
    val sources = attachmentDownloadUrls(botId, attachment)
    if (sources.isEmpty()) throw IOException("No downloadable attachment source")
    val wantsImage = attachment.type == "image" || attachment.mime.startsWith("image/")
    var lastFailure: Exception? = null
    for (source in sources) {
        try {
            downloadMedia(source, target, requireImage = wantsImage)
            return
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            lastFailure = failure
            // A partially written file must never be mistaken for a cache hit by the next source.
            if (target.isFile) target.delete()
        }
    }
    throw lastFailure ?: IOException("No downloadable attachment source")
}

/** Private media carries the current sign-in only on the configured origin, including redirects. */
internal suspend fun MemohApi.downloadMedia(
    source: String,
    target: File,
    maxBytes: Long = 64L * 1024 * 1024,
    requireImage: Boolean = false,
) =
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
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (requireImage && !contentType.startsWith("image/")) {
                        // A 200 that is HTML or JSON means this source answered with an error page
                        // or a JSON envelope, not the picture. Treating it as success produced a
                        // file the decoder could never open, which reads as a blank slot.
                        throw IOException("Source returned $contentType instead of an image")
                    }
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
