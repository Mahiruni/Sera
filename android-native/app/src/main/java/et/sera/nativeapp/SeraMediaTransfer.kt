package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Transfer primitive for a future authenticated, short-lived upload ticket.
 * The server must issue and validate the destination; never accept arbitrary URLs from users.
 * This function does NOT publish media or bypass moderation.
 */
internal suspend fun uploadMediaWithProgress(
    context: Context,
    uri: Uri,
    uploadUrl: String,
    contentType: String,
    size: Long,
    onProgress: (Int) -> Unit
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val target = URL(uploadUrl)
        require(target.protocol == "https") { "HTTPS is required" }
        require(target.host.isNotBlank()) { "Missing upload host" }
        require(size in 1..(100L * 1024 * 1024)) { "Invalid attachment size" }
        require(contentType.startsWith("image/") || contentType.startsWith("video/")) { "Invalid media type" }
        val connection = (target.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setFixedLengthStreamingMode(size)
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                connection.outputStream.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var transferred = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        transferred += count
                        if (transferred > size) throw IOException("Attachment exceeds declared size")
                        onProgress((transferred * 100 / size).toInt().coerceIn(0, 99))
                    }
                    if (transferred != size) throw IOException("Attachment size changed")
                }
            } ?: throw IOException("Unable to read selected attachment")
            if (connection.responseCode !in 200..299) throw IOException("Upload rejected (HTTP ${connection.responseCode})")
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }
}
