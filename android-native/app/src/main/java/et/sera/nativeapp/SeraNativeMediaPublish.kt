package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** The backend owns moderation, image normalization and final publication. */
internal suspend fun publishNativeImages(
    context: Context,
    token: String,
    body: String,
    images: List<Uri>,
    audience:String="public",
    fields:JSONObject=JSONObject(),
): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    if (images.isEmpty() || images.size > 4)
        return@withContext false to "Select one to four photos."
    val boundary = "sera-" + UUID.randomUUID().toString()
    val newline = "\r\n"
    var connection: HttpURLConnection? = null
    try {
        val files = images.map { uri ->
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime !in listOf("image/jpeg", "image/png", "image/webp"))
                return@withContext false to "Only JPG, PNG and WebP photos are supported."
            val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
            if (size !in 1..(3L * 1024 * 1024))
                return@withContext false to "Each photo must be smaller than 3 MB."
            Triple(uri, mime, size)
        }
        if (files.sumOf { it.third } > 3L * 1024 * 1024)
            return@withContext false to "Photos must total 3 MB or less."
        connection = (URL("https://sera.hisabtech.com/api/native/posts/v2/media").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        connection.outputStream.use { output ->
            fun write(value: String) { output.write(value.toByteArray(Charsets.UTF_8)) }
            fun field(name: String, value: String) {
                write("--$boundary$newline")
                write("Content-Disposition: form-data; name=\"$name\"$newline$newline")
                write(value)
                write(newline)
            }
            field("kind",fields.optString("kind","photos"))
            field("audience",audience)
            field("title",fields.optString("title").trim().ifBlank{body.trim().take(110).ifBlank{"Photo update"}.let{if(it.length<3)"Photo update" else it}})
            field("body", body.trim())
            for(key in listOf("quote_source","category","location","skills","project_url"))field(key,fields.optString(key))
            files.forEachIndexed { index, (uri, mime, _) ->
                write("--$boundary$newline")
                write("Content-Disposition: form-data; name=\"media\"; filename=\"photo-$index\"$newline")
                write("Content-Type: $mime$newline$newline")
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(output) }
                    ?: throw IllegalStateException("Selected photo is no longer readable")
                write(newline)
            }
            write("--$boundary--$newline")
        }
        val status = connection.responseCode
        val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        val result = runCatching { JSONObject(raw) }.getOrNull()
        if (status in 200..299 && result?.optBoolean("success") == true)
            true to "Published to your community."
        else false to (result?.optString("error")?.takeIf { it.isNotBlank() }
            ?: "Photo publishing failed (HTTP $status).")
    } catch (_: Exception) {
        false to "Upload interrupted. Check your connection and retry."
    } finally {
        connection?.disconnect()
    }
}
