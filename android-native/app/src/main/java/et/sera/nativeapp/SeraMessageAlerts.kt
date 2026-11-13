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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Account-bound metadata only. Never store notification message bodies. */
internal object SeraMessageAlerts {
    private val uuid=Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    fun validId(id:String)=uuid.matches(id)
    private var visible:Pair<String,String>?=null
    private val sounded=LinkedHashSet<String>()
    @Synchronized fun playIncomingOnce(context:Context,owner:String,thread:String,id:String){
        if(!foreground||!enabled(context,owner)||mutedUntil(context,owner,thread)>System.currentTimeMillis())return
        if(!context.getSharedPreferences("sera_chat_settings",0).getBoolean("sound",true))return
        if(!sounded.add(owner+":"+id))return
        while(sounded.size>256)sounded.remove(sounded.first())
        android.os.Handler(android.os.Looper.getMainLooper()).post{if(foreground)SeraNotificationSounds.playIncoming(context.applicationContext)}
    }
    @Volatile private var foreground=false
    var banner by androidx.compose.runtime.mutableStateOf<SeraMessageBannerEvent?>(null)
        private set
    fun setForeground(value:Boolean){foreground=value;if(!value)banner=null}
    fun dismissBanner(){banner=null}
    fun enabled(context:Context,owner:String)=prefs(context).getBoolean("enabled:$owner",true)
    fun setEnabled(context:Context,owner:String,value:Boolean){prefs(context).edit().putBoolean("enabled:$owner",value).apply();if(!value){dismissBanner();manager(context).activeNotifications.filter{it.tag?.startsWith("sera-message:$owner:")==true}.forEach{manager(context).cancel(it.tag,it.id)}}}
    fun mutedUntil(context:Context,owner:String,thread:String)=prefs(context).getLong("mute:$owner:$thread",0)
    fun mute(context:Context,owner:String,thread:String,until:Long){prefs(context).edit().putLong("mute:$owner:$thread",until).apply();if(until>System.currentTimeMillis()){manager(context).cancel(tag(owner,thread),1);if(banner?.thread==thread)dismissBanner()}}
    private fun showBanner(context:Context,event:SeraMessageBannerEvent){
        android.os.Handler(android.os.Looper.getMainLooper()).post{
            if(foreground&&SeraPush.owner(context)==event.owner&&enabled(context,event.owner)&&mutedUntil(context,event.owner,event.thread)<=System.currentTimeMillis()&&(event.kind!="message"||!isVisible(event.owner,event.thread))){
                banner=event
            }
        }
    }
    private fun prefs(c:Context)=c.getSharedPreferences("sera_message_alerts",Context.MODE_PRIVATE)
    private fun manager(c:Context)=c.getSystemService(NotificationManager::class.java)
    private fun tag(owner:String,thread:String)="sera-message:$owner:$thread"
    @Synchronized fun setVisible(context:Context,owner:String,thread:String?){
        visible=thread?.let{owner to it}
        if(thread!=null){manager(context).cancel(tag(owner,thread),1);prefs(context).edit().remove("count:$owner:$thread").apply()}
    }
    @Synchronized private fun isVisible(owner:String,thread:String)=visible==(owner to thread)
    @Synchronized fun clearVisible(owner:String,thread:String?){if(visible==(owner to thread))visible=null}
    @Synchronized fun clearAccount(context:Context){visible=null;sounded.clear();dismissBanner();val store=prefs(context);val editor=store.edit();store.all.keys.filter{it.startsWith("seen:")||it.startsWith("count:")}.forEach{editor.remove(it)};editor.apply()}
    fun clearThreadAlert(context:Context,owner:String,thread:String){manager(context).cancel(tag(owner,thread),1);prefs(context).edit().remove("count:$owner:$thread").apply()}
    @Synchronized fun receive(context:Context,data:Map<String,String>,preview:SeraNotificationPreview?=null){
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
        if(!enabled(context,owner)||mutedUntil(context,owner,thread)>System.currentTimeMillis())return
        if(foreground){playIncomingOnce(context,owner,thread,id);if(kind!="message"||visible!=(owner to thread))showBanner(context,SeraMessageBannerEvent(owner,thread,id,kind,preview));return}
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
        val title=preview?.title?:if(kind=="company_message")"New company message" else "New SERA message"
        val body=if(count==1)preview?.body?:"You have a new private message." else "$count new private messages"
        val public=NotificationCompat.Builder(context,SeraPush.CHANNEL).setSmallIcon(R.drawable.ic_sera_notification).setColor(ContextCompat.getColor(context,R.color.sera_teal)).setContentTitle("SERA.et").setContentText("New private activity").build()
        val builder=NotificationCompat.Builder(context,SeraNotificationSounds.channelFor(context,kind))
            .setSmallIcon(R.drawable.ic_sera_notification).setColor(ContextCompat.getColor(context,R.color.sera_teal)).setContentTitle(title).setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setNumber(count).setOnlyAlertOnce(false).setAutoCancel(true).setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_view,"Open conversation",pi)
        if(kind=="message")SeraNotificationActions.addActions(context,builder,owner,thread,id)
        manager(context).notify(notificationTag,1,builder.build())
    }
}
