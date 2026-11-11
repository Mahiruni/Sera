package et.sera.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SeraPost(val title: String, val body: String, val likes: Int, val comments: Int)

private fun request(token: String, path: String, auth: Boolean = false): Pair<String?, String> {
    return try {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = URL(base + (if (auth) "/auth/v1/" else "/rest/v1/") + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        if (status in 200..299) response to "" else null to "SERA request failed (HTTP $status)."
    } catch (e: Exception) { null to "Network unavailable. Please retry." }
}

fun loadSeraFeed(token: String): Pair<List<SeraPost>?, String> {
    val result = request(token, "posts?select=title,body,like_count,comment_count&status=eq.published&order=published_at.desc&limit=20")
    val body = result.first ?: return null to result.second
    return try {
        val array = JSONArray(body)
        (0 until array.length()).map {
            val item = array.getJSONObject(it)
            SeraPost(item.optString("title"), item.optString("body"), item.optInt("like_count"), item.optInt("comment_count"))
        } to ""
    } catch (e: Exception) { null to "Unable to read the feed response." }
}

fun loadSeraIdentity(token: String): Pair<String?, String> {
    val result = request(token, "user", true)
    val body = result.first ?: return null to result.second
    return try {
        val user = JSONObject(body)
        val name = user.optJSONObject("user_metadata")?.optString("full_name")?.takeIf { it.isNotBlank() } ?: "SERA member"
        (name + "\n" + user.optString("email")) to ""
    } catch (e: Exception) { null to "Unable to read your account." }
}
