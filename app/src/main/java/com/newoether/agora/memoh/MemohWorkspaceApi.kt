package com.newoether.agora.memoh

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Bot workspace files: listing, reading, revision-fenced writes, uploading and
 * the entry operations (mkdir/rename/delete/download/archive/extract).
 *
 * These are extension functions because they share the client's single
 * authenticated [MemohApi.request] path rather than a second HTTP client.
 */
internal suspend fun MemohApi.listFiles(botId: String, directory: String): List<MemohFile> {
    val data = request("/bots/${Uri.encode(botId)}/container/fs/list?path=${Uri.encode(directory)}")
    val array = data.optJSONArray("entries") ?: JSONArray()
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val path = item.optString("path")
        if (path.isBlank()) null else MemohFile(
            name = item.optString("name").ifBlank { path.substringAfterLast('/') },
            path = path,
            isDirectory = item.optBoolean("isDir"),
        )
    }
}

internal suspend fun MemohApi.readFile(botId: String, path: String): MemohFileContent {
    val data = request("/bots/${Uri.encode(botId)}/container/fs/read?path=${Uri.encode(path)}")
    return MemohFileContent(data.optString("content"), data.optString("revision"))
}

internal suspend fun MemohApi.writeFile(botId: String, path: String, content: String, revision: String): String {
    // The remote file may be changed by the Agent while the editor is open.
    require(revision.isNotBlank()) { "This file has no revision; safe overwrite is unavailable." }
    val response = request("/bots/${Uri.encode(botId)}/container/fs/write",
        JSONObject().put("path", path).put("content", content)
            .put("expectedRevision", revision))
    return response.optString("revision")
}

/** Creates a directory inside the bot workspace. */
internal suspend fun MemohApi.makeDirectory(botId: String, path: String) {
    require(path.isNotBlank()) { "A directory path is required" }
    request("/bots/${Uri.encode(botId)}/container/fs/mkdir",
        JSONObject().put("path", path.trim()))
}

/** Renames or moves a workspace entry. */
internal suspend fun MemohApi.renameEntry(botId: String, oldPath: String, newPath: String) {
    require(oldPath.isNotBlank() && newPath.isNotBlank()) { "Both paths are required" }
    require(oldPath != newPath) { "The new path is unchanged" }
    request("/bots/${Uri.encode(botId)}/container/fs/rename",
        JSONObject().put("oldPath", oldPath).put("newPath", newPath.trim()))
}

/**
 * Deletes a workspace entry.
 *
 * [recursive] is required for non-empty directories; callers must confirm
 * destructive deletes first.
 */
internal suspend fun MemohApi.deleteEntry(botId: String, path: String, recursive: Boolean = false) {
    require(path.isNotBlank()) { "A path is required" }
    request("/bots/${Uri.encode(botId)}/container/fs/delete",
        JSONObject().put("path", path).put("recursive", recursive))
}

/** Downloads a single workspace file into [destination] through the system picker. */
internal suspend fun MemohApi.downloadFile(
    botId: String,
    path: String,
    destination: Uri,
    resolver: ContentResolver,
) = withBusinessScope { captured ->
    val request = businessRequest("/bots/${Uri.encode(botId)}/container/fs/download?path=${Uri.encode(path)}", captured)
        .header("Accept", "application/octet-stream, */*")
        .get()
        .build()
    captured.identity.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw MemohHttpException(response.code, errorFor(response))
        val body = response.body ?: throw IOException("Memoh returned an empty file")
        val output = resolver.openOutputStream(destination, "w")
            ?: throw IOException("Cannot open destination for writing")
        output.use { sink -> body.byteStream().use { input -> input.copyTo(sink) } }
    }
}

/** Packs the given workspace paths into a tar.gz and streams it to [destination]. */
internal suspend fun MemohApi.downloadArchive(
    botId: String,
    paths: List<String>,
    destination: Uri,
    resolver: ContentResolver,
) = withBusinessScope { captured ->
    require(paths.isNotEmpty()) { "Choose at least one path to download" }
    val payload = JSONObject().put("paths", JSONArray(paths))
    val request = businessRequest("/bots/${Uri.encode(botId)}/container/fs/archive", captured)
        .header("Accept", "application/gzip, application/octet-stream, */*")
        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
    captured.identity.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw MemohHttpException(response.code, errorFor(response))
        val body = response.body ?: throw IOException("Memoh returned an empty archive")
        val output = resolver.openOutputStream(destination, "w")
            ?: throw IOException("Cannot open destination for writing")
        output.use { sink -> body.byteStream().use { input -> input.copyTo(sink) } }
    }
}

/** Extracts an archive that already lives in the workspace. */
internal suspend fun MemohApi.extractArchive(botId: String, path: String): String {
    require(path.isNotBlank()) { "An archive path is required" }
    val response = request("/bots/${Uri.encode(botId)}/container/fs/extract", JSONObject().put("path", path))
    return response.getString("destination").also {
        require(it.isNotBlank()) { "Memoh did not return the extraction destination" }
    }
}

internal suspend fun MemohApi.botBackupSummary(botId: String): JSONObject =
    request("/bots/${Uri.encode(botId)}/backup/summary")

internal suspend fun MemohApi.exportBotBackup(
    botId: String,
    destination: Uri,
    resolver: ContentResolver,
    sections: List<String>,
    passphrase: String,
) = withBusinessScope { captured ->
    val payload = JSONObject().put("sections", JSONArray(sections))
    if (passphrase.isNotBlank()) payload.put("passphrase", passphrase)
    val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = businessRequest("/bots/${Uri.encode(botId)}/backup/export", captured)
        .header("Accept", "application/octet-stream, application/zip, */*")
        .post(requestBody)
        .build()
    captured.identity.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw MemohHttpException(response.code, errorFor(response))
        val body = response.body ?: throw IOException("Memoh returned an empty backup")
        val output = resolver.openOutputStream(destination, "w")
            ?: throw IOException("Cannot open destination for writing")
        output.use { sink -> body.byteStream().use { input -> input.copyTo(sink) } }
    }
}

internal suspend fun MemohApi.previewBotBackup(
    uri: Uri,
    resolver: ContentResolver,
    mode: String,
    targetBotId: String,
    sections: List<String>,
    passphrase: String,
): JSONObject = botBackupMultipart("/bots/backup/import/preview", uri, resolver,
    mode, targetBotId, sections, passphrase)

internal suspend fun MemohApi.importBotBackup(
    uri: Uri,
    resolver: ContentResolver,
    mode: String,
    targetBotId: String,
    sections: List<String>,
    passphrase: String,
): JSONObject = botBackupMultipart("/bots/backup/import", uri, resolver,
    mode, targetBotId, sections, passphrase)

private suspend fun MemohApi.botBackupMultipart(
    path: String,
    uri: Uri,
    resolver: ContentResolver,
    mode: String,
    targetBotId: String,
    sections: List<String>,
    passphrase: String,
): JSONObject = withBusinessScope { captured ->
    val filename = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?.takeIf { it.isNotBlank() } ?: "backup.zip"
    val source = object : RequestBody() {
        override fun contentType() = (resolver.getType(uri) ?: "application/octet-stream").toMediaType()
        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read selected backup")
            input.use { it.copyTo(sink.outputStream()) }
        }
    }
    val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("file", filename, source)
        .addFormDataPart("mode", mode)
        .apply {
            if (targetBotId.isNotBlank()) addFormDataPart("target_bot_id", targetBotId)
            if (sections.isNotEmpty()) addFormDataPart("sections", JSONArray(sections).toString())
            if (passphrase.isNotEmpty()) addFormDataPart("passphrase", passphrase)
        }
        .build()
    val request = businessRequest(path, captured)
        .header("Accept", "application/json")
        .post(multipart).build()
    captured.identity.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw MemohHttpException(response.code, errorFor(response))
        val raw = response.body?.string().orEmpty()
        if (raw.isBlank()) JSONObject() else if (raw.trim().startsWith("[")) JSONObject().put("items", org.json.JSONArray(raw.trim())) else JSONObject(raw)
    }
}

internal suspend fun MemohApi.uploadFile(
    botId: String,
    path: String,
    uri: Uri,
    resolver: ContentResolver,
    filename: String,
) = withBusinessScope { captured ->
    val source = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun writeTo(sink: BufferedSink) {
            val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot read selected file")
            stream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) sink.write(buffer, 0, count)
                }
            }
        }
    }
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("path", path)
        .addFormDataPart("file", filename, source)
        .build()
    val response = captured.identity.client.newCall(businessRequest("/bots/${Uri.encode(botId)}/container/fs/upload", captured)
        .post(body).build()).execute()
    response.use {
        if (!it.isSuccessful) throw MemohHttpException(it.code, errorFor(it))
    }
}
