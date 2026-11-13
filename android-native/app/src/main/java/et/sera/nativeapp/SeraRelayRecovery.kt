package et.sera.nativeapp

import org.json.JSONArray
import org.json.JSONObject

internal class SeraRelayUnavailable:IllegalStateException("No secure call relay found. Switch Wi-Fi or mobile data and try again.")
internal data class SeraRelayAttempt(val config:JSONObject,val label:String,val budgetMs:Long)

/** Never synthesize credentials or endpoints; use only the authenticated server response. */
internal fun seraRelayAttempts(config:JSONObject):List<SeraRelayAttempt>{
    val servers=config.optJSONArray("iceServers")?:return emptyList()
    val valid=(0 until servers.length()).mapNotNull{servers.optJSONObject(it)}.filter{
        val url=it.optString("urls")
        (url.startsWith("turn:")||url.startsWith("turns:"))&&it.optString("username").isNotBlank()&&it.optString("credential").isNotBlank()
    }
    if(valid.isEmpty())return emptyList()
    fun attempt(values:List<JSONObject>,label:String,budget:Long)=SeraRelayAttempt(JSONObject().put("enabled",true).put("iceServers",JSONArray(values)),label,budget)
    val tls=valid.filter{it.optString("urls").startsWith("turns:")}.sortedBy{if(it.optString("urls").contains(":443?"))0 else 1}
    return listOf(attempt(valid,"Finding secure connection…",17000L),attempt(tls.ifEmpty{valid},"Trying alternative secure connection…",15000L))
}
