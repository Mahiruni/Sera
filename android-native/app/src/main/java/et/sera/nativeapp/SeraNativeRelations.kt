package et.sera.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class SeraNetworkRelations(
    val follows: Set<String>,
    val requests: List<SeraConnectionRequest>
)
internal data class SeraConnectionRequest(val id: String, val requester: String, val recipient: String, val status: String)

private fun relationRequest(token: String, path: String, method: String = "GET", payload: JSONObject? = null): Pair<String?, String> {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 14000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
        }
        if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val status = connection.responseCode
        if (status !in 200..299) {
            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(240).orEmpty()
            return null to when (status) {
                401, 403 -> "Permission denied. Check your account session."
                409 -> "This relationship already exists."
                else -> "Unable to update network (HTTP $status). $detail"
            }
        }
        (connection.inputStream?.bufferedReader()?.use { it.readText() } ?: "") to ""
    } catch (_: Exception) { null to "Network connection failed. Please retry." }
    finally { connection?.disconnect() }
}

internal fun loadSeraRelations(token: String, userId: String): Pair<SeraNetworkRelations?, String> {
    val (followRows, followError) = relationRequest(token,
        "professional_follows?select=followed_id&follower_id=eq.$userId&limit=500")
    if (followRows == null) return null to followError
    val (requestRows, requestError) = relationRequest(token,
        "professional_connection_requests?select=id,requester_id,recipient_id,status&or=(requester_id.eq.$userId,recipient_id.eq.$userId)&order=created_at.desc&limit=500")
    if (requestRows == null) return null to requestError
    return try {
        val followArray = JSONArray(followRows)
        val requestArray = JSONArray(requestRows)
        SeraNetworkRelations(
            (0 until followArray.length()).map { followArray.getJSONObject(it).optString("followed_id") }.toSet(),
            (0 until requestArray.length()).map {
                val obj = requestArray.getJSONObject(it)
                SeraConnectionRequest(obj.optString("id"), obj.optString("requester_id"),
                    obj.optString("recipient_id"), obj.optString("status"))
            }
        ) to ""
    } catch (_: Exception) { null to "Unable to read network relationships." }
}

internal fun changeSeraFollow(token: String, userId: String, peerId: String, follow: Boolean): String {
    val (result, error) = if (follow) relationRequest(token, "professional_follows", "POST",
        JSONObject().put("follower_id", userId).put("followed_id", peerId))
    else relationRequest(token, "professional_follows?follower_id=eq.$userId&followed_id=eq.$peerId", "DELETE")
    return if (result != null) "" else error
}

internal fun requestSeraConnection(token: String, userId: String, peerId: String): String {
    val (result, error) = relationRequest(token, "professional_connection_requests", "POST",
        JSONObject().put("requester_id", userId).put("recipient_id", peerId).put("status", "pending"))
    return if (result != null) "" else error
}

internal fun resolveSeraConnection(token: String, request: SeraConnectionRequest, userId: String, action: String): String {
    if (request.status != "pending") return "This request is no longer pending."
    val status = when (action) {
        "accepted", "declined" -> if (request.recipient == userId) action else return "Only the recipient can respond."
        "withdrawn" -> if (request.requester == userId) action else return "Only the requester can withdraw."
        else -> return "Unsupported action."
    }
    val (result, error) = relationRequest(token,
        "professional_connection_requests?id=eq.${request.id}&status=eq.pending", "PATCH",
        JSONObject().put("status", status).put("responded_at", java.time.Instant.now().toString()))
    return if (result != null) "" else error
}
