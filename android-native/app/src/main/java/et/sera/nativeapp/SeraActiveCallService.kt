package et.sera.nativeapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Main-thread, process-local lease. Never restarts a microphone after process death. */
internal object SeraCallForeground {
    private data class Lease(val id:String,val ready:CompletableDeferred<Unit>,val onStop:()->Unit)
    private var active:Lease?=null
    suspend fun start(context:Context,onStop:()->Unit):String{
        check(active==null){"Another call is active."}
        val lease=Lease(UUID.randomUUID().toString(),CompletableDeferred(),onStop)
        active=lease
        try{
            ContextCompat.startForegroundService(context,Intent(context,SeraActiveCallService::class.java).setAction("sera.call.START").putExtra("lease",lease.id))
            withTimeout(5000){lease.ready.await()}
            return lease.id
        }catch(e:Exception){stop(context,lease.id);throw e}
    }
    fun owns(id:String?)=id!=null&&active?.id==id
    fun ready(id:String){active?.takeIf{it.id==id}?.ready?.complete(Unit)}
    fun lost(id:String){
        val lease=active?.takeIf{it.id==id}?:return
        active=null
        if(lease.ready.isCompleted&&!lease.ready.isCancelled)lease.onStop()
        else lease.ready.completeExceptionally(IllegalStateException("Call service stopped."))
    }
    fun stop(context:Context,id:String?){
        if(!owns(id))return
        val lease=active
        active=null
        lease?.ready?.cancel()
        context.stopService(Intent(context,SeraActiveCallService::class.java))
    }
}
class SeraActiveCallService:Service(){
    private var lease:String?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        val id=intent?.getStringExtra("lease")
        if(intent?.action=="sera.call.END"){
            if(id!=null&&id==lease){SeraCallForeground.lost(id);stopSelf()}else if(lease==null)stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action!="sera.call.START"||id==null||!SeraCallForeground.owns(id)){stopSelf();return START_NOT_STICKY}
        lease=id
        try{
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"Ongoing SERA calls",NotificationManager.IMPORTANCE_LOW).apply{description="Microphone use during a voice call";lockscreenVisibility=Notification.VISIBILITY_PRIVATE;setSound(null,null)})
            val notification=notification(id)
            if(Build.VERSION.SDK_INT>=30)startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION_ID,notification)
            SeraCallForeground.ready(id)
        }catch(_:Exception){SeraCallForeground.lost(id);stopSelf()}
        return START_NOT_STICKY
    }
    private fun notification(id:String):Notification{
        val open=PendingIntent.getActivity(this,812,Intent(this,MainActivity::class.java).setAction("sera.call.OPEN").putExtra("sera_kind","voice_call_active").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end=PendingIntent.getService(this,813,Intent(this,SeraActiveCallService::class.java).setAction("sera.call.END").putExtra("lease",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(voiceText("Ongoing voice call")).setContentText(voiceText("Tap to return to your call"))
            .setCategory(NotificationCompat.CATEGORY_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,voiceText("End call"),end).build()
    }
    override fun onTaskRemoved(rootIntent:Intent?){lease?.let{SeraCallForeground.lost(it)};stopSelf();super.onTaskRemoved(rootIntent)}
    override fun onDestroy(){lease?.let{SeraCallForeground.lost(it)};lease=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
    companion object{private const val CHANNEL="sera_active_call";private const val NOTIFICATION_ID=41023}
}
