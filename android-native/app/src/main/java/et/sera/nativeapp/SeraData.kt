package et.sera.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SeraPost(val id: String, val authorId: String, val title: String, val body: String, val likes: Int, val comments: Int, val shares: Int, val kind: String, val publishedAt: String, val author: String = "SERA member", val headline: String = "", val imageUrl: String? = null)

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
    val result = request(token, "posts?select=id,author_id,title,body,kind,like_count,comment_count,share_count,published_at&status=eq.published&order=published_at.desc&limit=20")
    val body = result.first ?: return null to result.second
    return try {
        val array = JSONArray(body)
        val posts = (0 until array.length()).map {
            val item = array.getJSONObject(it)
            SeraPost(
                id = item.optString("id"),
                authorId = item.optString("author_id"),
                title = item.optString("title"),
                body = item.optString("body"),
                likes = item.optInt("like_count"),
                comments = item.optInt("comment_count"),
                shares = item.optInt("share_count"),
                kind = item.optString("kind"),
                publishedAt = item.optString("published_at")
            )
        }
        if (posts.isEmpty()) return emptyList<SeraPost>() to ""
        val authors = posts.map { it.authorId }.filter { it.isNotBlank() }.distinct()
        val names = mutableMapOf<String, Pair<String, String>>()
        if (authors.isNotEmpty()) {
            val filter = authors.joinToString(",")
            val people = request(token, "post_authors?select=id,full_name,headline&id=in.($filter)")
            people.first?.let { json ->
                val rows = JSONArray(json)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    names[row.optString("id")] = row.optString("full_name", "SERA member") to row.optString("headline")
                }
            }
        }
        val images = mutableMapOf<String, String>()
        val ids = posts.map { it.id }.filter { it.isNotBlank() }.joinToString(",")
        if (ids.isNotBlank()) {
            val media = request(token, "post_images?select=post_id,path&post_id=in.($ids)&order=sort_order.asc")
            media.first?.let { json ->
                val rows = JSONArray(json)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val postId = row.optString("post_id")
                    if (!images.containsKey(postId)) {
                        val signed = signedPostImage(token, row.optString("path"))
                        if (signed != null) images[postId] = signed
                    }
                }
            }
        }
        posts.map { post ->
            val author = names[post.authorId]
            post.copy(author = author?.first?.takeIf { it.isNotBlank() } ?: "SERA member",
                headline = author?.second ?: "", imageUrl = images[post.id])
        } to ""
    } catch (e: Exception) { null to "Unable to read the feed response." }
}

private fun signedPostImage(token: String, path: String): String? {
    if (path.isBlank()) return null
    return try {
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/sign/sera-post-media/" + encodedPath).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write("""{"expiresIn":3600}""".toByteArray()) }
        val body = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
        connection.disconnect()
        val signed = JSONObject(body).optString("signedURL").ifBlank { JSONObject(body).optString("signedUrl") }
        if (signed.isBlank()) null else BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + signed.let { if (it.startsWith("/")) it else "/$it" }
    } catch (e: Exception) { null }
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
