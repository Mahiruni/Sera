package et.sera.nativeapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object SeraNotificationActions {
    const val REPLY="et.sera.nativeapp.MESSAGE_REPLY"
    const val READ="et.sera.nativeapp.MESSAGE_READ"
    const val INPUT="sera_reply_text"
    private val client=OkHttpClient.Builder().callTimeout(6,TimeUnit.SECONDS).build()
    private fun intent(context:Context,action:String,owner:String,thread:String,message:String,clientId:String):Intent =
        Intent(context,SeraMessageActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("sera://message-action/$owner/$thread/$message/$clientId"))
            .putExtra("owner",owner).putExtra("thread",thread).putExtra("message",message).putExtra("client",clientId)

    fun addActions(context:Context,builder:NotificationCompat.Builder,owner:String,thread:String,message:String){
        val id=UUID.randomUUID().toString()
        val reply=PendingIntent.getBroadcast(context,0,intent(context,REPLY,owner,thread,message,id),
            PendingIntent.FLAG_UPDATE_CURRENT or (if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0))
        val read=PendingIntent.getBroadcast(context,0,intent(context,READ,owner,thread,message,id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val input=RemoteInput.Builder(INPUT).setLabel("Write a reply").build()
        builder.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send,"Reply",reply)
            .addRemoteInput(input).setAllowGeneratedReplies(true).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build())
        builder.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view,"Mark latest read",read)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build())
    }

    fun execute(context:Context,intent:Intent){
        val owner=intent.getStringExtra("owner")?:return
        val thread=intent.getStringExtra("thread")?:return
        val message=intent.getStringExtra("message")?:return
        val id=intent.getStringExtra("client")?:return
        if(listOf(owner,thread,message,id).any{!SeraMessageAlerts.validId(it)}||SeraPush.owner(context)!=owner)return
        val reply=intent.action==REPLY
        if(!reply&&intent.action!=READ)return
        val text=if(reply)RemoteInput.getResultsFromIntent(intent)?.getCharSequence(INPUT)?.toString()?.trim() else null
        if(reply&&(text.isNullOrBlank()||text.length>SERA_MESSAGE_LIMIT)){showResult(context,owner,thread,"Reply must contain 1–4,000 characters.");return}
        val store=if(reply)runCatching{NativeChatOutbox(context)}.getOrNull() else null
        if(reply){
            val saved=runCatching{check(store!=null);store.saveText(owner,thread,NativeQueuedText(id,text!!,message,java.time.Instant.now().toString(),TextSendStage.Ready))}
            if(saved.isFailure){showResult(context,owner,thread,"Reply not saved. Open SERA to try again.");return}
        }
        val access=runCatching{SessionStore(context).actionAccess(owner)}.getOrNull()
        if(access.isNullOrBlank()){showResult(context,owner,thread,if(reply)"Reply saved. Open SERA to send." else "Open SERA to mark this message read.");return}
        if(SeraPush.owner(context)!=owner)return
        val payload=JSONObject().put("action",if(reply)"reply" else "read").put("owner",owner).put("conversation",thread).put("message",message).put("clientId",id)
        if(reply)payload.put("body",text)
        val ok=runCatching{
            val request=Request.Builder().url("https://sera.hisabtech.com/api/native/message-action")
                .header("Authorization","Bearer $access").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use{it.isSuccessful}
        }.getOrDefault(false)
        if(SeraPush.owner(context)!=owner)return
        if(ok){
            if(reply)runCatching{store?.removeText(owner,thread,id)}
            SeraMessageAlerts.clearThreadAlert(context,owner,thread)
            SeraUnreadRefresh.changed()
        }else showResult(context,owner,thread,if(reply)"Reply saved. Open SERA to retry." else "Could not mark as read. Open SERA.")
    }

    private fun showResult(context:Context,owner:String,thread:String,body:String){
        if(SeraPush.owner(context)!=owner)return
        val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("sera://messages/$owner/$thread"))
            .putExtra("sera_kind","message:$thread:$owner").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,SeraNotificationSounds.SILENT).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SERA.et").setContentText(body).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pi).build()
        runCatching{context.getSystemService(NotificationManager::class.java).notify("sera-message:$owner:$thread",1,n)}
    }
}

class SeraMessageActionReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val pending=goAsync()
        Thread{try{SeraNotificationActions.execute(context.applicationContext,intent)}finally{pending.finish()}}.start()
    }
}
