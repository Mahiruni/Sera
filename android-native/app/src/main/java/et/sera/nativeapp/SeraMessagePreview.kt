package et.sera.nativeapp

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class SeraNotificationPreview(val title:String,val body:String,val senderId:String?=null,val avatar:String?=null,val createdAt:Long=0L,val mode:String="hidden")
/** Fetch only from the authorized SERA endpoint. No private text in FCM or local preferences.
 * Called on Firebase's worker thread, never while holding the UI alert lock.
 */
internal data class SeraPreviewResult(val preview:SeraNotificationPreview?=null,val suppressed:Boolean=false)
internal object SeraMessagePreview{
    private val client=OkHttpClient.Builder().callTimeout(2500,TimeUnit.MILLISECONDS).build()
    fun fetch(context:Context,data:Map<String,String>):SeraPreviewResult{
        if(data["kind"]!="message")return SeraPreviewResult()
        val owner=data["recipient_id"]?:return SeraPreviewResult()
        val thread=data["conversation_id"]?:return SeraPreviewResult()
        val id=data["message_id"]?:return SeraPreviewResult()
        if(listOf(owner,thread,id).any{!SeraMessageAlerts.validId(it)}||SeraPush.owner(context)!=owner)return SeraPreviewResult()
        if(!SeraMessageAlerts.enabled(context,owner)||SeraMessageAlerts.mutedUntil(context,owner,thread)>System.currentTimeMillis())return SeraPreviewResult()
        val access=runCatching{SessionStore(context).actionAccess(owner)}.getOrNull()?:return SeraPreviewResult()
        return runCatching{
            val request=Request.Builder().url("https://sera.hisabtech.com/api/native/message-preview?owner=$owner&conversation=$thread&message=$id").header("Authorization","Bearer $access").header("Accept-Language",context.resources.configuration.locales[0].language).get().build()
            client.newCall(request).execute().use{response->
                if(SeraPush.owner(context)!=owner||response.code in listOf(204,403,404))return SeraPreviewResult(suppressed=true)
                if(response.code!=200)return SeraPreviewResult()
                val json=JSONObject(response.body?.string().orEmpty())
                val title=json.optString("title").take(160)
                val body=json.optString("body").take(280)
                val sender=json.optString("senderId").takeIf{SeraMessageAlerts.validId(it)}
                val avatar=json.optString("avatar").takeUnless{it.isBlank()||it=="null"}
                val at=runCatching{java.time.Instant.parse(json.optString("createdAt")).toEpochMilli()}.getOrDefault(0L)
                SeraPreviewResult(if(title.isBlank()||body.isBlank())null else SeraNotificationPreview(title,body,sender,avatar,at,json.optString("mode","hidden")))
            }
        }.getOrDefault(SeraPreviewResult())
    }
    fun incomingCall(context:Context,owner:String,id:String):SeraNotificationPreview?{
        if(!SeraMessageAlerts.validId(owner)||!SeraMessageAlerts.validId(id)||SeraPush.owner(context)!=owner)return null
        val access=runCatching{SessionStore(context).actionAccess(owner)}.getOrNull()?:return null
        return runCatching{
            val request=Request.Builder().url("https://sera.hisabtech.com/api/native/call-preview?owner=$owner&call=$id").header("Authorization","Bearer $access").build()
            client.newCall(request).execute().use{response->
                if(response.code!=200||SeraPush.owner(context)!=owner)return null
                val json=JSONObject(response.body?.string().orEmpty());val sender=json.optString("senderId").takeIf{SeraMessageAlerts.validId(it)}?:return null
                val title=json.optString("title").take(160);if(title.isBlank())return null
                SeraNotificationPreview(title,"",sender,json.optString("avatar").takeUnless{it.isBlank()||it=="null"},mode="sender")
            }
        }.getOrNull()
    }
}
