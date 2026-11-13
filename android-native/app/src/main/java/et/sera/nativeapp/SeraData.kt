package et.sera.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

data class SeraPost(val id: String, val authorId: String, val title: String, val body: String, val likes: Int, val comments: Int, val shares: Int, val kind: String, val publishedAt: String, val author: String = "SERA member", val headline: String = "", val imageUrl: String? = null, val liked: Boolean = false, val avatarUrl: String? = null, val repostOf: String? = null, val repostAuthor: String? = null, val originalTime: String? = null, val originalTitle: String? = null, val originalBody: String? = null, val repostNote: String = "", val repostCount: Int = 0, val reposted: Boolean = false, val originalAvatar: String? = null, val originalAuthorId: String? = null, val audience:String="public", val editedAt:String?=null, val saved:Boolean=false,val identityVerified:Boolean=false)

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

fun loadSeraFeed(token: String, page: Int = 0, postId: String? = null, personalized:Boolean=false,savedOnly:Boolean=false): Pair<List<SeraPost>?, String> {
    if(postId!=null && !Regex("^[0-9a-fA-F-]{36}$").matches(postId))return null to "Invalid post."
    val filter=postId?.let{"&id=eq.$it"}?:""
    val savedJoin=if(savedOnly)",post_saves!inner(post_id)" else ""
    val result = if(personalized&&postId==null&&!savedOnly) spaceCall(token,"sera_ranked_feed",JSONObject().put("p_offset",page*20).put("p_limit",20)) else request(token, "posts?select=id,author_id,title,body,kind,like_count,comment_count,share_count,published_at,repost_of,repost_note,audience,edited_at$savedJoin&status=eq.published&order=published_at.desc,id.desc&limit=20&offset=${page * 20}$filter")
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
                publishedAt = item.optString("published_at"),
                repostOf = item.optString("repost_of").takeIf { it.isNotBlank() && it != "null" }, repostNote = item.optString("repost_note").takeUnless{it=="null"}.orEmpty(), audience=item.optString("audience","public"),editedAt=item.optString("edited_at").takeUnless{it.isBlank()||it=="null"}
            )
        }
        if (posts.isEmpty()) return emptyList<SeraPost>() to ""
        val sourceAuthors = mutableMapOf<String, String>()
        val sourceRows = mutableMapOf<String, JSONObject>()
        val sourceIds = posts.mapNotNull { it.repostOf }.distinct()
        if (sourceIds.isNotEmpty()) {
            val originals = request(token,
                "posts?select=id,author_id,title,body,published_at&status=eq.published&id=in.(${sourceIds.joinToString(",")})")
            originals.first?.let { json ->
                val rows = JSONArray(json)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    sourceAuthors[row.optString("id")] = row.optString("author_id"); sourceRows[row.optString("id")] = row
                }
            }
        }
        val authors = (posts.map { it.authorId } + sourceAuthors.values)
            .filter { it.isNotBlank() }.distinct()
        // Resolve visible profile names and avatars through the live member_cards RPC.
        // The old post_authors relation does not exist in the hosted schema.
        // Independent enrichment requests share one network round instead of a waterfall.
        val names = mutableMapOf<String,String>()
        val avatars = mutableMapOf<String,String>()
        val headlines = mutableMapOf<String,String>()
        val likedIds = mutableSetOf<String>()
        val savedIds = mutableSetOf<String>()
        val images = mutableMapOf<String,String>()
        val idsForLikes = posts.joinToString(","){it.id}
        val ids = (posts.map{it.id}+sourceIds).distinct().joinToString(",")
        val stateIds=posts.map{it.repostOf?:it.id}.distinct()
        val badgesAndStates = kotlinx.coroutines.runBlocking {
            val badges = async(Dispatchers.IO){identityBadges(token,authors)}
            val heads = async(Dispatchers.IO){spaceRows(spaceCall(token,"sera_feed_headlines",JSONObject().put("p_ids",JSONArray(authors))).first)}
            val people = async(Dispatchers.IO){
                val rows=spaceRows(spaceCall(token,"member_cards",JSONObject().put("ids",JSONArray(authors))).first)
                val paths=rows.mapNotNull{it.optString("avatar_path").takeIf{p->p.isNotBlank()&&p!="null"}}.distinct()
                val signed=signedFeedAvatars(token,paths)
                rows to signed
            }
            val likes = async(Dispatchers.IO){request(token,"post_likes?select=post_id&post_id=in.($idsForLikes)").first}
            val saves = async(Dispatchers.IO){request(token,"post_saves?select=post_id&post_id=in.($idsForLikes)").first}
            val media = async(Dispatchers.IO){request(token,"post_images?select=post_id,path&post_id=in.($ids)&order=sort_order.asc").first}
            val states = async(Dispatchers.IO){spaceRows(spaceCall(token,"sera_repost_states",JSONObject().put("p_ids",JSONArray(stateIds))).first).associateBy{it.optString("id")}}
            heads.await().forEach{headlines[it.optString("id")]=it.optString("headline").takeUnless{v->v=="null"}.orEmpty()}
            val (rows,signed)=people.await()
            rows.forEach{row->
                val id=row.optString("id");val name=row.optString("full_name")
                if(id.isNotBlank()&&name.isNotBlank()&&name!="null")names[id]=name
                signed[row.optString("avatar_path")]?.let{avatars[id]=it}
            }
            likes.await()?.let{json->val r=JSONArray(json);for(i in 0 until r.length())likedIds.add(r.getJSONObject(i).optString("post_id"))}
            saves.await()?.let{json->val r=JSONArray(json);for(i in 0 until r.length())savedIds.add(r.getJSONObject(i).optString("post_id"))}
            media.await()?.let{json->val r=JSONArray(json);for(i in 0 until r.length()){val row=r.getJSONObject(i);val id=row.optString("post_id");val path=row.optString("path");if(path.isNotBlank()&&!images.containsKey(id))images[id]=authenticatedPostMedia(path)}}
            badges.await() to states.await()
        }
        val badges=badgesAndStates.first
        val repostStates=badgesAndStates.second
        posts.map { post ->
            post.copy(author = names[post.authorId] ?: "SERA member",
                headline = headlines[post.authorId].orEmpty(), imageUrl = images[post.repostOf ?: post.id], liked = likedIds.contains(post.id), saved=savedIds.contains(post.id),identityVerified=post.authorId in badges,
                avatarUrl = avatars[post.authorId],
                repostAuthor = post.repostOf?.let { id -> sourceAuthors[id]?.let { author -> names[author] } }, originalTime=sourceRows[post.repostOf]?.optString("published_at"), originalTitle=sourceRows[post.repostOf]?.optString("title"), originalBody=sourceRows[post.repostOf]?.optString("body"), originalAvatar=avatars[sourceAuthors[post.repostOf]],originalAuthorId=sourceAuthors[post.repostOf],repostCount=repostStates[post.repostOf?:post.id]?.optInt("repost_count")?:0,reposted=repostStates[post.repostOf?:post.id]?.optBoolean("reposted")?:false)
        } to ""
    } catch (e: Exception) { null to "Unable to read the feed response." }
}

/** Batch private avatar signing under the caller's own storage policies. */
private fun signedFeedAvatars(token:String,paths:List<String>):Map<String,String>{
    if(paths.isEmpty())return emptyMap()
    var connection:HttpURLConnection?=null
    return try{
        val base=BuildConfig.SUPABASE_URL.trimEnd('/')
        connection=(URL(base+"/storage/v1/object/sign/sera-account-private").openConnection() as HttpURLConnection).apply{
            requestMethod="POST";connectTimeout=10000;readTimeout=10000;doOutput=true
            setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json")
        }
        connection.outputStream.use{it.write(JSONObject().put("paths",JSONArray(paths)).put("expiresIn",300).toString().toByteArray())}
        if(connection.responseCode !in 200..299)return emptyMap()
        val rows=JSONArray(connection.inputStream.bufferedReader().use{it.readText()})
        buildMap{for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val path=row.optString("path");val signed=row.optString("signedURL").ifBlank{row.optString("signedUrl")};if(path.isNotBlank()&&signed.isNotBlank()&&signed!="null")put(path,if(signed.startsWith("https://"))signed else base+"/storage/v1/"+signed.trimStart('/'))}}
    }catch(_:Exception){emptyMap()}finally{connection?.disconnect()}
}

internal fun signedPostImage(token: String, path: String, bucket: String = "sera-post-media"): String? {
    if (path.isBlank()) return null
    return try {
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/sign/" + bucket + "/" + encodedPath).openConnection() as HttpURLConnection).apply {
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
        if (signed.isBlank()) null else if (signed.startsWith("https://")) signed else BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + signed.let { if (it.startsWith("/")) it else "/$it" }
    } catch (e: Exception) { null }
}

internal fun authenticatedPostMedia(path:String):String=BuildConfig.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/authenticated/sera-post-media/"+path.split('/').joinToString("/"){android.net.Uri.encode(it)}

fun loadSeraIdentity(token: String): Pair<String?, String> {
    val result = request(token, "user", true)
    val body = result.first ?: return null to result.second
    return try {
        val user = JSONObject(body)
        val name = user.optJSONObject("user_metadata")?.optString("full_name")?.takeIf { it.isNotBlank() } ?: "SERA member"
        (name + "\n" + user.optString("email")) to ""
    } catch (e: Exception) { null to "Unable to read your account." }
}

fun setSeraLike(token: String, postId: String, liked: Boolean): Pair<Boolean, String> = setSeraPostInteraction(token,postId,liked,"post_likes")
fun setSeraSaved(token:String,postId:String,saved:Boolean):Pair<Boolean,String> = setSeraPostInteraction(token,postId,saved,"post_saves")
private fun setSeraPostInteraction(token:String,postId:String,liked:Boolean,table:String):Pair<Boolean,String> {
    if (!Regex("^[0-9a-fA-F-]{36}$").matches(postId)) return false to "Invalid post."
    return try {
        val userResult = request(token, "user", true)
        val userBody = userResult.first ?: return false to "Please sign in again."
        val userId = JSONObject(userBody).optString("id")
        if (userId.isBlank()) return false to "Please sign in again."
        val path = if (liked) "$table?post_id=eq.$postId&user_id=eq.$userId" else table
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = if (liked) "DELETE" else "POST"
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            if (!liked) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
        }
        if (!liked) connection.outputStream.use {
            it.write(JSONObject().put("post_id", postId).toString().toByteArray())
        }
        val status = connection.responseCode
        val response = if(status !in 200..299) connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() else ""
        connection.disconnect()
        val duplicate = !liked && status == 409 && runCatching { JSONObject(response).optString("code") == "23505" }.getOrDefault(false)
        (status in 200..299 || duplicate) to if (status in 200..299 || duplicate) "" else "Unable to update reaction (HTTP $status)."
    } catch (e: Exception) { false to "Connection failed. Please retry." }
}

fun loadSeraUserId(token: String): Pair<String?, String> {
    val response = request(token, "user", true)
    val body = response.first ?: return null to response.second
    return try { JSONObject(body).optString("id").takeIf { it.isNotBlank() } to "" }
    catch (e: Exception) { null to "Unable to identify signed-in user." }
}

/** Resolve the authenticated member's existing avatar without public storage access. */
fun loadSeraCurrentAvatar(token: String, userId: String): String? {
    if (userId.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return null
    return try {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/rest/v1/rpc/member_cards").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 11000
            readTimeout = 13000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("ids", JSONArray().put(userId))
                    .toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode !in 200..299) return null
            val rows = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            if (rows.length() == 0) return null
            val path = rows.getJSONObject(0).optString("avatar_path")
            if (path.isBlank() || path == "null") null else
                signedPostImage(token, path, "sera-account-private")
        } finally { connection.disconnect() }
    } catch (_: Exception) { null }
}
