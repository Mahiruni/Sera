package et.sera.nativeapp
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal val SeraPurple=Color(0xFF7C3AED)
internal object SeraUnreadRefresh {
 var revision by mutableIntStateOf(0)
 fun changed(){if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())revision++ else android.os.Handler(android.os.Looper.getMainLooper()).post{revision++}}
}
@Composable internal fun rememberSeraUnreadActivity(token:String,userId:String,section:String,link:SeraLinkState=SeraLinkState(),activeCall:Boolean=false):Pair<Int,Int>{
 var counts by remember(token,userId){mutableStateOf(0 to 0)}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 val revision=SeraUnreadRefresh.revision
 LaunchedEffect(token,userId,revision,lifecycle,link,activeCall){
  if(token.isBlank()||userId.isBlank()){counts=0 to 0;return@LaunchedEffect}
  if(!link.available)return@LaunchedEffect
  lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
   while(true){
    val result=withContext(Dispatchers.IO){spaceCall(token,"sera_unread_activity")}
    runCatching{JSONObject(result.first?:"null")}.getOrNull()?.let{counts=it.optInt("notifications") to it.optInt("inbox")}
    delay(seraSecondarySyncDelay(link,activeCall,30000L))
   }
  }
 }
 return counts
}
