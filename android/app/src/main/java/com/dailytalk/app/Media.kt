package com.dailytalk.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Photos and videos are NOT stored in Firebase. They are uploaded to a free public file host (catbox.moe by default)
 * and only the resulting link is stored in the chat. Anyone with the link can open the file.
 */
object Media {
    val maxBytes: Long = (BuildConfig.MAX_UPLOAD_MB.toLongOrNull() ?: 200L) * 1000 * 1000
    val endpoint: String get() = BuildConfig.UPLOAD_URL.ifBlank { "https://catbox.moe/user/api.php" }

    private val images = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    private val videos = setOf("video/mp4", "video/webm", "video/quicktime")
    /** Same pattern as database.rules.json. */
    val hostedUrl = Regex("^https://files\\.catbox\\.moe/[A-Za-z0-9_-]+\\.[A-Za-z0-9]{2,5}$")

    fun kind(mime: String?): String? = when (mime) { in images -> "image"; in videos -> "video"; else -> null }

    /** Returns an error message, or null when the file can be sent. Mirrors the website. */
    fun validate(mime: String?, size: Long, max: Long = maxBytes): String? = when {
        size <= 0 -> "This file is empty."
        kind(mime) == null -> "Only photos (JPEG, PNG, WebP, GIF) and videos (MP4, WebM, MOV) can be sent."
        size > max -> "This file is larger than ${max / 1_000_000} MB."
        else -> null
    }

    fun parseResponse(text: String): String {
        val url = text.trim()
        if (hostedUrl.matches(url)) return url
        throw Repo.UserFacingException(if (url.isNotEmpty() && url.length < 200) "The file host refused the upload: $url" else "The file host sent an unexpected answer.")
    }

    fun safeName(name: String): String {
        val clean = name.replace(Regex("[^\\w.\\-]+"), "_").takeLast(80)
        return if (Regex(".*\\.[A-Za-z0-9]{2,5}$").matches(clean)) clean else (clean.ifBlank { "file" } + ".bin")
    }

    data class Info(val name: String, val mime: String?, val size: Long)

    fun describe(cr: ContentResolver, uri: Uri): Info {
        var name = "file"; var size = 0L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return Info(name, cr.getType(uri), size)
    }

    /** Streams the file as multipart/form-data (nothing is buffered in memory) and returns the hosted link. */
    suspend fun upload(cr: ContentResolver, uri: Uri, info: Info, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val boundary = "----Dailytalk" + UUID.randomUUID().toString().replace("-", "")
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"${safeName(info.name)}\"\r\n" +
            "Content-Type: ${info.mime ?: "application/octet-stream"}\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.doOutput = true
            conn.connectTimeout = 15_000; conn.readTimeout = 10 * 60_000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setFixedLengthStreamingMode(head.size.toLong() + info.size + tail.size)
            conn.outputStream.use { out ->
                out.write(head)
                cr.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(64 * 1024); var sent = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); sent += n
                        onProgress((sent.toFloat() / info.size).coerceIn(0f, 1f))
                        currentCoroutineContext().ensureActive()
                    }
                } ?: throw Repo.UserFacingException("Could not read the selected file.")
                out.write(tail)
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw Repo.UserFacingException("The file host answered with an error ($code).")
            parseResponse(body)
        } catch (e: java.io.IOException) {
            throw Repo.UserFacingException("Upload failed. Check your connection and try again.")
        } finally { conn.disconnect() }
    }
}
