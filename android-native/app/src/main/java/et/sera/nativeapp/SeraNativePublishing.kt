package et.sera.nativeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls the moderated server gateway; never inserts posts directly into Supabase. */
internal suspend fun publishNativeText(token: String, body: String, audience:String="public",kind:String="idea"): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL("https://sera.hisabtech.com/api/native/posts/v2").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        val trimmed = body.trim()
        val title = trimmed.lineSequence().firstOrNull().orEmpty().take(140).let { if (it.length < 3) "Community update" else it }
        val payload = JSONObject().put("kind", kind).put("audience",audience).put("title", title).put("body", trimmed).toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val ok = connection.responseCode == 201
        val stream = if (ok) connection.inputStream else connection.errorStream
        val result = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val message = runCatching { JSONObject(result).optString("error") }.getOrDefault("")
        ok to (if (ok) "Published successfully." else message.ifBlank { "Publishing failed. Please retry." })
    } catch (_: Exception) {
        false to "Network unavailable. Your draft is preserved."
    } finally {
        connection?.disconnect()
    }
}
