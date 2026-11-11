package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Transport only. A future authenticated backend must issue short-lived upload tickets
 * and moderate content before any post becomes public.
 */
internal suspend fun uploadMediaWithProgress(
    context: Context,
    uri: Uri,
    uploadUrl: String,
    contentType: String,
    size: Long,
    onProgress: (Int) -> Unit
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val target = URL(uploadUrl)
        require(target.protocol == "https" && target.host.isNotBlank()) { "HTTPS destination required" }
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
            } ?: throw IOException("Unable to read attachment")
            if (connection.responseCode !in 200..299) throw IOException("Upload rejected (HTTP ${connection.responseCode})")
            onProgress(100)
            Result.success(Unit)
        } finally {
            connection.disconnect()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
