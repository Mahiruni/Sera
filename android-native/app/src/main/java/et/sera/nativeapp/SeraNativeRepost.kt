package et.sera.nativeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reposts go through the authenticated server moderation gateway, not a direct table write. */
internal suspend fun repostNativePost(
    token: String, originalPostId: String, note: String
): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    if (!Regex("^[0-9a-fA-F-]{36}$").matches(originalPostId) ||
        note.length > 400 || token.isBlank()) {
        return@withContext false to "Invalid repost request."
    }
    var connection: HttpURLConnection? = null
    try {
        connection = (URL("https://sera.hisabtech.com/api/native/posts/repost")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        val data = JSONObject().put("post_id", originalPostId)
            .put("note", note.trim()).toString()
        connection.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val input = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = input?.bufferedReader()?.use { it.readText() }.orEmpty()
        val payload = runCatching { JSONObject(response) }.getOrNull()
        val success = status == 201 && payload?.optBoolean("success") == true
        success to if (success) "Reposted to your network." else
            payload?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "Unable to repost right now (HTTP $status)."
    } catch (_: Exception) {
        false to "Connection interrupted. Your repost was not submitted."
    } finally {
        connection?.disconnect()
    }
}
