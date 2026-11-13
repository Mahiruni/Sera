package et.sera.nativeapp

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class SeraNotificationPreview(val title:String,val body:String)
/** Fetch only from the authorized SERA endpoint. No private text in FCM or local preferences.
 * Called on Firebase's worker thread, never while holding the UI alert lock.
 */
internal object SeraMessagePreview{
    private val client=OkHttpClient.Builder().callTimeout(1800,TimeUnit.MILLISECONDS).build()
    fun fetch(context:Context,data:Map<String,String>):SeraNotificationPreview?{
        if(data["kind"]!="message")return null
        val owner=data["recipient_id"]?:return null
        val thread=data["conversation_id"]?:return null
        val id=data["message_id"]?:return null
        if(listOf(owner,thread,id).any{!SeraMessageAlerts.validId(it)}||SeraPush.owner(context)!=owner)return null
        if(!SeraMessageAlerts.enabled(context,owner)||SeraMessageAlerts.mutedUntil(context,owner,thread)>System.currentTimeMillis())return null
        val access=runCatching{SessionStore(context).actionAccess(owner)}.getOrNull()?:return null
        return runCatching{
            val request=Request.Builder().url("https://sera.hisabtech.com/api/native/message-preview?owner=$owner&conversation=$thread&message=$id").header("Authorization","Bearer $access").get().build()
            client.newCall(request).execute().use{response->
                if(response.code!=200||SeraPush.owner(context)!=owner)return null
                val json=JSONObject(response.body?.string().orEmpty())
                val title=json.optString("title").take(160)
                val body=json.optString("body").take(280)
                if(title.isBlank()||body.isBlank())null else SeraNotificationPreview(title,body)
            }
        }.getOrNull()
    }
}
