package et.sera.nativeapp

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

/** Account-bound metadata only. Never store notification message bodies. */
internal object SeraMessageAlerts {
    private val uuid=Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    fun validId(id:String)=uuid.matches(id)
    private var visible:Pair<String,String>?=null
    private fun prefs(c:Context)=c.getSharedPreferences("sera_message_alerts",Context.MODE_PRIVATE)
    private fun manager(c:Context)=c.getSystemService(NotificationManager::class.java)
    private fun tag(owner:String,thread:String)="sera-message:$owner:$thread"
    @Synchronized fun setVisible(context:Context,owner:String,thread:String?){
        visible=thread?.let{owner to it}
        if(thread!=null){manager(context).cancel(tag(owner,thread),1);prefs(context).edit().remove("count:$owner:$thread").apply()}
    }
    @Synchronized fun clearVisible(owner:String,thread:String?){if(visible==(owner to thread))visible=null}
    @Synchronized fun clearAccount(context:Context){visible=null;prefs(context).edit().clear().apply()}
    @Synchronized fun receive(context:Context,data:Map<String,String>){
        val owner=data["recipient_id"]?:return
        val thread=data["conversation_id"]?:return
        val id=data["message_id"]?:return
        val kind=data["kind"]?:return
        if(!validId(owner)||!validId(thread)||!validId(id)||owner!=SeraPush.owner(context))return
        if(kind!="message"&&kind!="company_message")return
        val store=prefs(context)
        val key="seen:$owner"
        val seen=runCatching{val a=JSONArray(store.getString(key,"[]"));(0 until a.length()).map{a.getString(it)}}.getOrDefault(emptyList())
        if(id in seen)return
        store.edit().putString(key,JSONArray((seen+id).takeLast(256)).toString()).apply()
        SeraUnreadRefresh.changed()
        if(kind=="message"&&visible==(owner to thread))return
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val notificationTag=tag(owner,thread)
        val countKey="count:$owner:$thread"
        val existing=manager(context).activeNotifications.any{it.tag==notificationTag&&it.id==1}
        val count=(if(existing)store.getInt(countKey,0) else 0).plus(1).coerceAtMost(999)
        store.edit().putInt(countKey,count).apply()
        SeraPush.channel(context)
        val intent=Intent(context,MainActivity::class.java).apply{
            flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            setData(android.net.Uri.parse("sera://messages/$owner/$thread"))
            putExtra("sera_kind",if(kind=="message")"message:$thread:$owner" else "company_message")
        }
        val pi=PendingIntent.getActivity(context,1,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title=if(kind=="company_message")"New company message" else "New SERA message"
        val body=if(count==1)"You have a new private message." else "$count new private messages"
        val public=NotificationCompat.Builder(context,SeraPush.CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SERA.et").setContentText("New private activity").build()
        val n=NotificationCompat.Builder(context,SeraNotificationSounds.channelFor(context,kind))
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setNumber(count).setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_view,"Open conversation",pi).build()
        manager(context).notify(notificationTag,1,n)
    }
}
