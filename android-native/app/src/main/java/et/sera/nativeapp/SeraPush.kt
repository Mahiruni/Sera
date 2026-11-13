package et.sera.nativeapp

import android.Manifest
import android.app.Application
import android.media.AudioAttributes
import android.media.RingtoneManager
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object SeraPush {
    const val CHANNEL="sera_messages"
    const val CALL_CHANNEL="sera_voice_calls"
    private val callUuid=Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private const val PREFS="sera_push"
    private val registrationGeneration=java.util.concurrent.atomic.AtomicInteger(0)
    fun initialize(context:Context):Boolean {
        if(FirebaseApp.getApps(context).isNotEmpty())return true
        val raw=BuildConfig.FIREBASE_CLIENT_JSON
        if(raw.isBlank())return false
        return runCatching{
            val json=JSONObject(raw)
            val project=json.getJSONObject("project_info")
            val clients=json.getJSONArray("client")
            val client=(0 until clients.length()).map{clients.getJSONObject(it)}.first{it.getJSONObject("client_info").getJSONObject("android_client_info").getString("package_name")==context.packageName}
            val options=FirebaseOptions.Builder().setApplicationId(client.getJSONObject("client_info").getString("mobilesdk_app_id"))
                .setApiKey(client.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
                .setProjectId(project.getString("project_id")).setGcmSenderId(project.getString("project_number")).build()
            FirebaseApp.initializeApp(context,options)!=null
        }.getOrDefault(false)
    }
    fun channel(context:Context){
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"SERA notifications",NotificationManager.IMPORTANCE_HIGH).apply{description="Messages and professional activity";enableVibration(true);setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())})
        manager.createNotificationChannel(NotificationChannel(CALL_CHANNEL,"SERA voice calls",NotificationManager.IMPORTANCE_HIGH).apply{description="Incoming voice calls";enableVibration(true);vibrationPattern=longArrayOf(0,350,250,350);lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE;setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())})
        SeraNotificationSounds.createChannels(context)
    }
    private fun prefs(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun clearLocal(context:Context){SeraMessageAlerts.clearAccount(context);registrationGeneration.incrementAndGet();prefs(context).edit().remove("owner").apply();context.getSystemService(NotificationManager::class.java).cancelAll()}
    suspend fun register(context:Context,access:String,userId:String)=withContext(Dispatchers.IO){
        if(!initialize(context)||userId.isBlank())return@withContext
        // Data messages can refresh the foreground inbox even without OS notification permission.
        val generation=registrationGeneration.get()
        val token=runCatching{com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().token,15,TimeUnit.SECONDS)}.getOrNull()?:return@withContext
        if(generation!=registrationGeneration.get())return@withContext
        registerToken(context,access,userId,token,generation)
    }
    private fun registerToken(context:Context,access:String,userId:String,token:String,generation:Int){
        if(generation!=registrationGeneration.get())return
        val old=prefs(context).getString("token",null)
        val oldOwner=prefs(context).getString("owner",null)
        if(old!=null&&old!=token&&oldOwner==userId)deviceRequest(access,old,userId,true)
        if(deviceRequest(access,token,userId,false)) {
            if(generation==registrationGeneration.get())prefs(context).edit().putString("token",token).putString("owner",userId).apply()
            else deviceRequest(access,token,userId,true)
        }
    }
    fun refreshToken(context:Context,token:String){
        val generation=registrationGeneration.get()
        val user=owner(context)?:return
        val access=runCatching{SessionStore(context).actionAccess(user)}.getOrNull()?:return
        if(owner(context)==user&&generation==registrationGeneration.get())registerToken(context,access,user,token,generation)
    }
    suspend fun unregister(context:Context,access:String,userId:String)=withContext(Dispatchers.IO){
        val token=prefs(context).getString("token",null)
        if(token!=null&&userId.isNotBlank())deviceRequest(access,token,userId,true)
        if(initialize(context))runCatching{com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().deleteToken(),15,TimeUnit.SECONDS)}
        prefs(context).edit().remove("token").apply()
    }
    fun owner(context:Context)=prefs(context).getString("owner",null)
    fun active(context:Context)=owner(context)!=null
    private fun deviceRequest(access:String,token:String,userId:String,remove:Boolean):Boolean{
        var c:HttpURLConnection?=null
        return try{
            val suffix=if(remove)"?user_id=eq.$userId&token=eq."+java.net.URLEncoder.encode(token,"UTF-8") else "?on_conflict=token"
            c=(URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/professional_push_devices"+suffix).openConnection() as HttpURLConnection).apply{
                requestMethod=if(remove)"DELETE" else "POST";connectTimeout=10000;readTimeout=10000
                setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);setRequestProperty("Authorization","Bearer $access")
                if(!remove){doOutput=true;setRequestProperty("Content-Type","application/json");setRequestProperty("Prefer","resolution=merge-duplicates,return=minimal")}
            }
            if(!remove)c.outputStream.use{it.write(JSONObject().put("user_id",userId).put("token",token).put("platform","android").put("enabled",true).toString().toByteArray())}
            c.responseCode in 200..299
        }catch(_:Exception){false}finally{c?.disconnect()}
    }
    fun notify(context:Context,kind:String,title:String,body:String){
        if(!active(context))return
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        channel(context)
        val intent=Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP;putExtra("sera_kind",kind)}
        val id=(System.currentTimeMillis()%Int.MAX_VALUE).toInt()
        val pi=PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,SeraNotificationSounds.channelFor(context,kind)).setSmallIcon(R.drawable.ic_sera_notification).setColor(ContextCompat.getColor(context,R.color.sera_teal)).setContentTitle(title).setContentText(body).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pi).build()
        context.getSystemService(NotificationManager::class.java).notify(id,n)
    }
    fun clearCallNotification(context:Context,id:String){if(callUuid.matches(id))context.getSystemService(NotificationManager::class.java).cancel("sera-call:"+id,1)}
    private fun callAlerts(context:Context)=context.getSharedPreferences("sera_call_alerts",Context.MODE_PRIVATE)
    @Synchronized fun dismissCallAlert(context:Context,data:Map<String,String>){
        val id=data["call_id"]?:return
        if(!callUuid.matches(id)||prefs(context).getString("owner",null)!=data["recipient_id"])return
        val now=System.currentTimeMillis()
        val store=callAlerts(context);val editor=store.edit()
        store.all.forEach{(key,value)->if(value !is Long||value<now)editor.remove(key)}
        editor.putLong(id,now+90000).apply()
        clearCallNotification(context,id)
    }
    private fun silencedAlerts(context:Context)=context.getSharedPreferences("sera_call_silenced",Context.MODE_PRIVATE)
    internal fun isCallSilenced(context:Context,id:String)=silencedAlerts(context).getLong(id,0)>System.currentTimeMillis()
    @Synchronized fun silenceCall(context:Context,id:String,recipient:String){
        if(!callUuid.matches(id)||prefs(context).getString("owner",null)!=recipient)return
        val now=System.currentTimeMillis();val store=silencedAlerts(context);val editor=store.edit()
        store.all.forEach{(key,value)->if(value !is Long||value<now)editor.remove(key)}
        editor.putLong(id,now+90000).apply();clearCallNotification(context,id)
    }
    @Synchronized fun notifyCall(context:Context,data:Map<String,String>){
        if (!BuildConfig.LIVE_CALLING_INCLUDED) return
        val id=data["call_id"]?:return
        val recipient=data["recipient_id"]?:return
        if(!callUuid.matches(id)||prefs(context).getString("owner",null)!=recipient)return
        if(callAlerts(context).getLong(id,0)>System.currentTimeMillis()||isCallSilenced(context,id))return
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val remaining=callRingWindowMillis(data["expires_at"],System.currentTimeMillis())
        if(remaining<=0)return
        channel(context)
        val notification=buildCallNotification(context,id,recipient,remaining)
        context.getSystemService(NotificationManager::class.java).notify("sera-call:"+id,1,notification)
    }
    internal fun buildCallNotification(context:Context,id:String,recipient:String,remaining:Long):android.app.Notification{
        require(callUuid.matches(id)&&callUuid.matches(recipient)&&remaining in 1..45000)
        val intent=Intent(context,MainActivity::class.java).apply{action="et.sera.nativeapp.INCOMING_CALL";flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP;putExtra("sera_kind","voice_call:"+id)}
        val pi=PendingIntent.getActivity(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val silence=PendingIntent.getBroadcast(context,id.hashCode(),Intent(context,SeraCallSilenceReceiver::class.java).apply{action="et.sera.nativeapp.SILENCE_CALL";putExtra("call_id",id);putExtra("recipient_id",recipient)},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline=PendingIntent.getBroadcast(context,id.hashCode(),Intent(context,SeraCallDeclineReceiver::class.java).apply{action="et.sera.nativeapp.DECLINE_CALL";putExtra("call_id",id);putExtra("recipient_id",recipient)},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public=NotificationCompat.Builder(context,CALL_CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Sera").setContentText("Incoming call").build()
        return NotificationCompat.Builder(context,CALL_CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming SERA call").setContentText("Open SERA to answer").setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(remaining.coerceAtMost(45000)).setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(pi)
            .addAction(android.R.drawable.sym_call_incoming,"Open call",pi)
            .addAction(android.R.drawable.sym_call_missed,"Decline",decline)
            .addAction(android.R.drawable.ic_lock_silent_mode,"Silence",silence).setDeleteIntent(silence).build().apply{flags=flags or android.app.Notification.FLAG_INSISTENT}
    }
    fun dispatch(access:String,kind:String,id:String){
        if(!id.matches(Regex("[0-9a-f-]{36}")))return
        var c:HttpURLConnection?=null
        try{
            c=(URL("https://sera.hisabtech.com/api/native/push-event").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=10000;readTimeout=15000;setRequestProperty("Authorization","Bearer $access");setRequestProperty("Content-Type","application/json")}
            c.outputStream.use{it.write(JSONObject().put("kind",kind).put("id",id).toString().toByteArray())};c.responseCode
        }catch(_:Exception){}finally{c?.disconnect()}
    }
}
class SeraCallSilenceReceiver:android.content.BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){if(intent.action=="et.sera.nativeapp.SILENCE_CALL")SeraPush.silenceCall(context,intent.getStringExtra("call_id")?:return,intent.getStringExtra("recipient_id")?:return)}
}
/** Explicit decline action: validate account, call membership and server state before transition. */
class SeraCallDeclineReceiver:android.content.BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!="et.sera.nativeapp.DECLINE_CALL")return
        val id=intent.getStringExtra("call_id")?:return
        val recipient=intent.getStringExtra("recipient_id")?:return
        if(!id.matches(Regex("[0-9a-fA-F-]{36}"))||SeraPush.owner(context)!=recipient)return
        val pending=goAsync()
        val app=context.applicationContext
        // Silence locally immediately; the authoritative decline is still subject to server authorization.
        SeraPush.silenceCall(app,id,recipient)
        Thread{
            try{
                val token=SessionStore(app).actionAccess(recipient)?:return@Thread
                if(SeraPush.owner(app)!=recipient)return@Thread
                val raw=boundedCallRpc(token,"sera_call_incoming",JSONObject().put("p_id",id))?:return@Thread
                if(raw=="null")return@Thread
                val call=JSONObject(raw)
                // The incoming RPC only returns a row for its authenticated callee, while ringing.
                if(call.optString("id")!=id)return@Thread
                val conversation=call.optString("conversation_id")
                if(!conversation.matches(Regex("[0-9a-fA-F-]{36}")))return@Thread
                val result=boundedCallRpc(token,"sera_call_transition",JSONObject().put("p_conversation",conversation).put("p_id",id).put("p_action","decline"))
                if(!result.isNullOrBlank()&&result!="null"){
                    SeraPush.dismissCallAlert(app,mapOf("call_id" to id,"recipient_id" to recipient))
                    // A bounded request keeps the broadcast within Android’s execution window.
                    boundedRequest("https://sera.hisabtech.com/api/native/push-event",token,JSONObject().put("kind","voice_call_dismiss").put("id",id),false)
                }
            }catch(_:Exception){}finally{pending.finish()}
        }.start()
    }
}
private fun boundedCallRpc(token:String,fn:String,payload:JSONObject):String?=
    boundedRequest(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/rpc/"+fn,token,payload,true)
private fun boundedRequest(url:String,token:String,payload:JSONObject,supabase:Boolean):String?{
    var connection:HttpURLConnection?=null
    return try{
        connection=(URL(url).openConnection() as HttpURLConnection).apply{
            requestMethod="POST";doOutput=true;instanceFollowRedirects=false
            connectTimeout=1200;readTimeout=1200
            setRequestProperty("Authorization","Bearer "+token)
            setRequestProperty("Content-Type","application/json")
            if(supabase)setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
        }
        connection.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
        if(connection.responseCode in 200..299)connection.inputStream.bufferedReader().use{it.readText()} else null
    }catch(_:Exception){null}finally{connection?.disconnect()}
}
class SeraApplication:Application(){override fun onCreate(){super.onCreate();SeraPush.initialize(this);SeraPush.channel(this)}}
class SeraFirebaseService:FirebaseMessagingService(){
    override fun onMessageReceived(message:RemoteMessage){
        if(message.data["kind"]=="voice_call_dismiss"){SeraPush.dismissCallAlert(this,message.data);return}
        if(message.data["kind"]=="voice_call"){SeraPush.notifyCall(this,message.data);return}
        if(message.data["kind"] in listOf("message","company_message") && message.data.containsKey("message_id")){val preview=SeraMessagePreview.fetch(this,message.data);SeraMessageAlerts.receive(this,message.data,preview);return}
        SeraPush.notify(this,message.data["kind"]?:"activity",message.data["title"]?:"Sera",message.data["body"]?:"You have a new update.")
    }
    override fun onNewToken(token:String){super.onNewToken(token);SeraPush.refreshToken(this,token)}
    override fun onDeletedMessages(){super.onDeletedMessages();SeraPushRecovery.request(this)}
}

/** Server timestamps are authoritative; never extend a ringing invitation locally. */
internal fun callRingWindowMillis(expiresAt:String?,now:Long):Long{
    if(expiresAt.isNullOrBlank())return 0L
    return runCatching{(java.time.Instant.parse(expiresAt).toEpochMilli()-now).coerceIn(0L,45000L)}.getOrDefault(0L)
}
