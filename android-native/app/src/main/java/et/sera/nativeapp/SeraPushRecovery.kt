package et.sera.nativeapp
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf

/** Dropped FCM messages trigger a server resync, not fabricated alert replay. */
internal object SeraPushRecovery{
 var revision by mutableIntStateOf(0)
     private set
 fun request(context:Context){
  if(!SeraPush.active(context))return
  Handler(Looper.getMainLooper()).post{
   revision++
   SeraUnreadRefresh.changed()
  }
 }
}
internal fun seraLaunchDestination(intent:Intent?):String?{
 if(intent==null)return null
 val explicit=intent.getStringExtra("sera_kind")
 if(explicit!=null)return explicit
 val kind=intent.getStringExtra("kind")?:return null
 if(kind=="message"){
  val owner=intent.getStringExtra("recipient_id")
  val thread=intent.getStringExtra("conversation_id")
  if(owner!=null&&thread!=null&&SeraMessageAlerts.validId(owner)&&SeraMessageAlerts.validId(thread))return "message:$thread:$owner"
 }
 return kind
}
