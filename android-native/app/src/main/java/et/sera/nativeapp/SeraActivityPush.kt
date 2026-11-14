package et.sera.nativeapp

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Account-bound activity alerts. Provider payloads contain identifiers, never private text. */
internal object SeraActivityPush{
    private val client=OkHttpClient.Builder().callTimeout(3500,TimeUnit.MILLISECONDS).build()
    @Volatile private var visibleSpace:Pair<String,String>?=null
    private fun manager(context:Context)=context.getSystemService(NotificationManager::class.java)
    private fun prefs(context:Context)=context.getSharedPreferences("sera_activity_alerts",Context.MODE_PRIVATE)
    internal fun destination(kind:String,owner:String,target:String):String?{
        if(!SeraMessageAlerts.validId(owner)||!SeraMessageAlerts.validId(target))return null
        return when(kind){"space_message"->"space_message:$target:$owner";"missed_call"->"message:$target:$owner";else->null}
    }
    internal data class OpenEvent(val kind:String,val owner:String,val target:String,val event:String)
    internal fun parseOpen(raw:String?):OpenEvent?{
        val parts=raw?.split(":")?:return null
        if(parts.size!=5||parts[0]!="activity"||destination(parts[1],parts[3],parts[2])==null||!SeraMessageAlerts.validId(parts[4]))return null
        return OpenEvent(parts[1],parts[3],parts[2],parts[4])
    }
    internal fun openDestination(kind:String,owner:String,target:String,event:String):String?{
        if(destination(kind,owner,target)==null||!SeraMessageAlerts.validId(event))return null
        return "activity:$kind:$target:$owner:$event"
    }
    fun launchDestination(intent:Intent?):String?{
        val raw=intent?.getStringExtra("sera_kind")?:return null
        val parsed=parseOpen(raw)?:return null
        return raw.takeIf{intent.data?.toString()=="sera://activity/${parsed.owner}/${parsed.kind}/${parsed.target}/${parsed.event}"}
    }
    fun markOpened(context:Context,token:String,event:OpenEvent):Boolean{
        if(SeraPush.owner(context)!=event.owner)return false
        return runCatching{
            val payload=JSONObject().put("owner",event.owner).put("target",event.target).put("event",event.event).put("kind",event.kind)
            val request=Request.Builder().url("https://sera.hisabtech.com/api/native/activity-read")
                .header("Authorization","Bearer $token").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use{it.code==204&&SeraPush.owner(context)==event.owner}
        }.getOrDefault(false)
    }
    private fun tag(kind:String,owner:String,target:String,event:String)="sera-activity:$owner:$kind:"+(if(kind=="space_message")target else event)
    fun setVisibleSpace(context:Context,owner:String,space:String){visibleSpace=owner to space;manager(context).cancel(tag("space_message",owner,space,""),1)}
    fun clearVisibleSpace(owner:String,space:String){if(visibleSpace==(owner to space))visibleSpace=null}
    @Synchronized fun clearAlerts(context:Context,owner:String){manager(context).activeNotifications.filter{it.tag?.startsWith("sera-activity:$owner:")==true}.forEach{manager(context).cancel(it.tag,it.id)}}
    @Synchronized fun clearAccount(context:Context){visibleSpace=null;prefs(context).edit().clear().apply()}
    @Synchronized private fun seen(context:Context,owner:String,id:String,mark:Boolean):Boolean{
        val store=prefs(context);val key="seen:$owner"
        val prior=runCatching{val a=JSONArray(store.getString(key,"[]"));(0 until a.length()).map{a.getString(it)}}.getOrDefault(emptyList())
        if(id in prior)return true
        if(mark)store.edit().putString(key,JSONArray((prior+id).takeLast(256)).toString()).apply()
        return false
    }
    fun receive(context:Context,data:Map<String,String>){
        val owner=data["recipient_id"]?:return;val target=data["conversation_id"]?:return;val event=data["message_id"]?:return;val kind=data["kind"]?:return
        val destination=openDestination(kind,owner,target,event)?:return
        if(!SeraMessageAlerts.validId(event)||SeraPush.owner(context)!=owner||!SeraMessageAlerts.enabled(context,owner)||seen(context,owner,event,false))return
        if(kind=="missed_call"&&SeraMessageAlerts.mutedUntil(context,owner,target)>System.currentTimeMillis())return
        SeraUnreadRefresh.changed()
        if(kind=="space_message"&&SeraMessageAlerts.isForeground()&&visibleSpace==(owner to target)){seen(context,owner,event,true);return}
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val access=runCatching{SessionStore(context).actionAccess(owner)}.getOrNull()?:return
        val preview=runCatching{
            val request=Request.Builder().url("https://sera.hisabtech.com/api/native/activity-preview?owner=$owner&conversation=$target&message=$event&kind=$kind")
                .header("Authorization","Bearer $access").header("Accept-Language",context.resources.configuration.locales[0].language).build()
            client.newCall(request).execute().use{response->
                if(response.code!=200||SeraPush.owner(context)!=owner)return
                JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull()?:return
        if(SeraPush.owner(context)!=owner||seen(context,owner,event,true))return
        if(kind=="space_message"&&SeraMessageAlerts.isForeground()&&visibleSpace==(owner to target))return
        val title=preview.optString("title").take(160).ifBlank{"Sera"}
        val body=preview.optString("body").take(280)
        val sender=preview.optString("senderId").takeIf{SeraMessageAlerts.validId(it)&&preview.optString("mode")!="hidden"}
        val senderName=preview.optString("senderName").ifBlank{title}
        val avatar=preview.optString("avatar").takeUnless{it.isBlank()||it=="null"}
        val at=runCatching{java.time.Instant.parse(preview.optString("createdAt")).toEpochMilli()}.getOrDefault(System.currentTimeMillis())
        val key=tag(kind,owner,target,event)
        val intent=Intent(context,MainActivity::class.java).setData(android.net.Uri.parse("sera://activity/$owner/$kind/$target/$event")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("sera_kind",destination)
        val pi=PendingIntent.getActivity(context,1,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val count=if(kind=="space_message")(manager(context).activeNotifications.firstOrNull{it.tag==key&&it.id==1}?.notification?.number?:0).plus(1).coerceAtMost(999) else 1
        SeraPush.channel(context)
        fun post(image:Bitmap?,silent:Boolean)=synchronized(this){
            if(SeraPush.owner(context)!=owner||!SeraMessageAlerts.enabled(context,owner))return@synchronized
            if(silent&&manager(context).activeNotifications.none{it.tag==key&&it.id==1&&it.notification.extras.getString("sera_activity_event")==event})return@synchronized
            val public=NotificationCompat.Builder(context,SeraPush.CHANNEL).setSmallIcon(R.drawable.ic_sera_notification).setContentTitle("Sera").setContentText(context.getString(R.string.sera_notification_activity)).build()
            val builder=NotificationCompat.Builder(context,SeraNotificationSounds.channelFor(context,kind)).setSmallIcon(R.drawable.ic_sera_notification)
                .setColor(ContextCompat.getColor(context,R.color.sera_teal)).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(if(kind=="missed_call")NotificationCompat.CATEGORY_MISSED_CALL else NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).setWhen(at).setNumber(count).setOnlyAlertOnce(silent).setAutoCancel(true).setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_view,context.getString(R.string.sera_notification_open),pi)
            if(image!=null)builder.setLargeIcon(image)
            val notification=builder.build();notification.extras.putString("sera_activity_event",event)
            manager(context).notify(key,1,notification)
        }
        post(sender?.let{SeraNotificationAvatars.cached(owner,it)?:SeraNotificationAvatars.initials(senderName)},false)
        if(sender!=null&&avatar!=null){
            val image=SeraNotificationAvatars.load(context,owner,sender,avatar)
            if(image!=null&&manager(context).activeNotifications.any{it.tag==key&&it.id==1&&it.notification.extras.getString("sera_activity_event")==event})post(image,true)
        }
    }
}
