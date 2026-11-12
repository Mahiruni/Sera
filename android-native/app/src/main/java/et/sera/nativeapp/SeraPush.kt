package et.sera.nativeapp

import android.Manifest
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
    private const val PREFS="sera_push"
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
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"SERA notifications",NotificationManager.IMPORTANCE_HIGH).apply{description="Messages and professional activity";enableVibration(true)})
    }
    private fun prefs(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun clearLocal(context:Context){prefs(context).edit().remove("owner").apply();context.getSystemService(NotificationManager::class.java).cancelAll();if(initialize(context))FirebaseMessaging.getInstance().deleteToken()}
    suspend fun register(context:Context,access:String,userId:String)=withContext(Dispatchers.IO){
        if(!initialize(context)||userId.isBlank())return@withContext
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return@withContext
        val token=runCatching{com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().token)}.getOrNull()?:return@withContext
        val old=prefs(context).getString("token",null)
        val oldOwner=prefs(context).getString("owner",null)
        if(old!=null&&old!=token&&oldOwner==userId)deviceRequest(access,old,userId,true)
        if(deviceRequest(access,token,userId,false))prefs(context).edit().putString("token",token).putString("owner",userId).apply()
    }
    suspend fun unregister(context:Context,access:String,userId:String)=withContext(Dispatchers.IO){
        val token=prefs(context).getString("token",null)
        if(token!=null&&userId.isNotBlank())deviceRequest(access,token,userId,true)
        clearLocal(context)
    }
    fun active(context:Context)=prefs(context).getString("owner",null)!=null
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
        val n=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pi).build()
        context.getSystemService(NotificationManager::class.java).notify(id,n)
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
class SeraFirebaseService:FirebaseMessagingService(){
    override fun onMessageReceived(message:RemoteMessage){
        SeraPush.notify(this,message.data["kind"]?:"activity",message.data["title"]?:"SERA.et",message.data["body"]?:"You have a new update.")
    }
    override fun onNewToken(token:String){super.onNewToken(token)}
}
