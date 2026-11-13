package et.sera.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

internal data class SeraCallEvent(val id:String,val caller:String,val state:String,val createdAt:String)
private data class CallRow(val id:String,val caller:String,val callee:String,val state:String,val offer:String?,val answer:String?,val expiresAt:String){
    val live get()=state=="ringing"||state=="accepted"
}
private fun parseCall(raw:String?):CallRow?{if(raw.isNullOrBlank()||raw=="null")return null;val j=JSONObject(raw);return CallRow(j.getString("id"),j.getString("caller"),j.getString("callee"),j.getString("state"),j.optString("offer").takeUnless{it.isBlank()||it=="null"},j.optString("answer").takeUnless{it.isBlank()||it=="null"},j.optString("expires_at"))}
private suspend fun callRequest(token:String,fn:String,payload:JSONObject):String?=withContext(Dispatchers.IO){val r=chatRpc(token,fn,payload);if(r.first==null)error(r.second.ifBlank{"Call unavailable."});r.first}
@Composable
internal fun SeraNativeVoiceCallButton(token:String,userId:String,conversation:String,name:String,photo:String?,ink:Color,accent:Color,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit={}){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var config by remember(conversation){mutableStateOf<JSONObject?>(null)}
    var row by remember(conversation){mutableStateOf<CallRow?>(null)}
    var rtc by remember(conversation){mutableStateOf<SeraNativeRtc?>(null)}
    var phase by remember(conversation){mutableStateOf("")}
    var error by remember(conversation){mutableStateOf("")}
    var busy by remember(conversation){mutableStateOf(false)}
    var muted by remember(conversation){mutableStateOf(false)}
    var speaker by remember(conversation){mutableStateOf(false)}
    var started by remember(conversation){mutableLongStateOf(0L)}
    var seconds by remember(conversation){mutableLongStateOf(0L)}
    var lastAnswer by remember(conversation){mutableStateOf<String?>(null)}
    var showHistory by remember(conversation){mutableStateOf(false)}
    var history by remember(conversation){mutableStateOf<List<String>>(emptyList())}
    fun payload()=JSONObject().put("p_conversation",conversation)
    fun cleanup(){rtc?.close();rtc=null;phase="";muted=false;speaker=false;started=0;seconds=0;lastAnswer=null;row=null}
    suspend fun end(action:String,message:String=""){
        val call=row;call?.let{SeraPush.clearCallNotification(context,it.id)};cleanup();if(message.isNotBlank())error=message
        if(call!=null&&call.live)runCatching{callRequest(token,"sera_call_transition",payload().put("p_id",call.id).put("p_action",action));withContext(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",call.id)}}.onFailure{error="Call closed locally. Remote status will expire."}
    }
    fun peer():SeraNativeRtc{
        onAudioStart()
        val c=config?:error("Calling is unavailable.")
        return SeraNativeRtc(context,c){state->scope.launch{
            if(rtc!=null){when(state){"connected"->{phase="Connected";if(started==0L)started=SystemClock.elapsedRealtime()};"connecting"->if(row?.state=="accepted")phase="Connecting…";"disconnected"->phase="Reconnecting…";"failed"->end("fail","Call connection failed.")}}
        }}.also{rtc=it}
    }
    fun begin(accept:Boolean){
        if(busy)return;busy=true;error="";phase="Preparing call…"
        scope.launch{try{
            val fresh=callRequest(token,"sera_call_config",payload())?.let{JSONObject(it)}?:error("Calling is unavailable.")
            if(!fresh.optBoolean("enabled"))error("Calling is unavailable.")
            config=fresh;val p=peer()
            if(accept){val s=row?:error("Call ended.");val answer=p.answer(s.offer?:error("Call expired."));val next=parseCall(callRequest(token,"sera_call_transition",payload().put("p_id",s.id).put("p_action","accept").put("p_answer",answer)))?:error("Call ended.");if(!next.live)error("Call ended.");row=next;scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",next.id)};if(started==0L)phase="Connecting…"}
            else{val offer=p.offer();val s=parseCall(callRequest(token,"sera_call_start",payload().put("p_id",UUID.randomUUID().toString()).put("p_offer",offer)))?:error("Call unavailable.");row=s;phase="Ringing…";scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call",s.id)}}
        }catch(e:CancellationException){cleanup();throw e}catch(e:Exception){if(accept)end("fail",e.message?:"Call unavailable.")else{cleanup();error=e.message?:"Call unavailable."}}finally{busy=false}}
    }
    var permissionAccept by remember{mutableStateOf(false)}
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)begin(permissionAccept)else error="Microphone permission is required for voice calling."}
    fun request(accept:Boolean){permissionAccept=accept;if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin(accept)else micPermission.launch(Manifest.permission.RECORD_AUDIO)}
    LaunchedEffect(token,conversation){config=runCatching{callRequest(token,"sera_call_config",payload())?.let{JSONObject(it)}}.getOrNull()}
    val enabled=config?.optBoolean("enabled")==true
    LaunchedEffect(enabled,token,conversation,lifecycle){
        if(!enabled)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            var failures=0
            while(true){
                if(!busy){try{
                    val old=row;val next=parseCall(callRequest(token,"sera_call_status",payload().put("p_id",old?.id?:JSONObject.NULL)));failures=0
                    if(next==null){if(old!=null){cleanup();error="Call is no longer available."}}
                    else if(!next.live){if(old!=null){cleanup();error=when(next.state){"missed"->"No answer";"declined"->"Call declined";"failed"->"Call connection failed";else->"Call ended"}}}
                    else if(next.state=="accepted"&&rtc==null){if(old!=null){cleanup();error="Call answered on another device."}}
                    else if(old!=null||next.callee==userId){
                        row=next
                        if(next.state=="accepted"&&next.caller==userId&&next.answer!=null&&lastAnswer!=next.answer){lastAnswer=next.answer;rtc?.remoteAnswer(next.answer);if(started==0L)phase="Connecting…"}
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){if(++failures>=3&&row!=null)end("fail","Call connection lost.")}}
                delay(1500)
            }
        }
    }
    LaunchedEffect(enabled,token,conversation,lifecycle){if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){try{val a=JSONArray(callRequest(token,"sera_call_history",payload())?:"[]");onHistory((0 until a.length()).map{val j=a.getJSONObject(it);SeraCallEvent(j.getString("id"),j.getString("caller"),j.getString("state"),j.getString("created_at"))})}catch(e:CancellationException){throw e}catch(_:Exception){};delay(10000)}}}
    LaunchedEffect(started){while(started>0){seconds=(SystemClock.elapsedRealtime()-started)/1000;delay(1000)}}
    LaunchedEffect(phase,row?.id){if(phase=="Connecting…"||phase=="Reconnecting…"){delay(25000);if(phase=="Connecting…"||phase=="Reconnecting…")end("fail","Could not establish the call.")}}
    val incoming=row?.state=="ringing"&&row?.callee==userId&&phase.isBlank()
    var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    val latestRow by rememberUpdatedState(row)
    val latestPhase by rememberUpdatedState(phase)
    DisposableEffect(lifecycle,conversation){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_RESUME)resumed=true
            if(event==Lifecycle.Event.ON_PAUSE)resumed=false
            if(event==Lifecycle.Event.ON_STOP){
                val current=latestRow
                if(current!=null&&current.state=="ringing"&&current.callee==userId&&latestPhase.isBlank()){
                    SeraPush.notifyCall(context,mapOf("call_id" to current.id,"recipient_id" to userId,"expires_at" to current.expiresAt))
                }else if(current!=null)scope.launch{end("end")}
            }
        }
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);rtc?.close()}
    }
    LaunchedEffect(incoming,resumed,row?.id){if(incoming&&resumed)row?.let{SeraPush.clearCallNotification(context,it.id)}}
    DisposableEffect(incoming,resumed,row?.id){
        val id=row?.id
        val ringtone=if(incoming&&resumed&&id!=null&&!SeraPush.isCallSilenced(context,id))runCatching{RingtoneManager.getRingtone(context,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)).apply{audioAttributes=android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();if(android.os.Build.VERSION.SDK_INT>=28)isLooping=true;play()}}.getOrNull()else null
        onDispose{runCatching{ringtone?.stop()}}
    }
    if(!enabled)return
    IconButton(onClick={showHistory=true;scope.launch{history=runCatching{val a=JSONArray(callRequest(token,"sera_call_history",payload())?:"[]");(0 until a.length()).map{val j=a.getJSONObject(it);(if(j.getString("caller")==userId)"Outgoing"else "Incoming")+" · "+j.getString("state")}}.getOrDefault(emptyList())}},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Call,"Voice call",tint=ink)}
    if(showHistory)AlertDialog(onDismissRequest={showHistory=false},title={Text("Voice call")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Call "+name);if(history.isNotEmpty()){Text("Recent calls",fontWeight=FontWeight.SemiBold);history.take(5).forEach{Text(it,fontSize=12.sp)}}}},confirmButton={TextButton(onClick={showHistory=false;request(false)},enabled=row==null&&!busy){Text("Call",color=accent)}},dismissButton={TextButton(onClick={showHistory=false}){Text("Cancel")}})
    if(row!=null||phase.isNotBlank())Dialog(onDismissRequest={if(!busy)scope.launch{end(if(incoming)"decline"else "end")}},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,dismissOnBackPress=!busy)){
        Column(Modifier.fillMaxSize().background(Color(0xFF111D21)).systemBarsPadding().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Text("SERA · Voice call",color=Color(0xFF9ED7D2),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF29444A)),contentAlignment=Alignment.Center){Text(name.take(1).uppercase(),fontSize=32.sp,color=accent);photo?.let{SeraMediaImage(it,"Profile photo",192,192,Modifier.fillMaxSize())}}
            Spacer(Modifier.height(20.dp));Text(name,color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold)
            Text(if(incoming)"Incoming voice call"else phase.ifBlank{"Ringing…"},color=Color(0xFFA8C3C5),fontSize=15.sp)
            if(started>0)Text("%02d:%02d".format(seconds/60,seconds%60),color=Color.White,fontSize=16.sp)
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){
                if(incoming){CallControl(Icons.Outlined.Call,"Accept",Color(0xFF087E83)){request(true)};CallControl(Icons.Outlined.CallEnd,"Decline",Color(0xFFB63042)){scope.launch{end("decline")}}}
                else{if(row?.state=="accepted"){CallControl(if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic,if(muted)"Unmute"else "Mute",Color(0xFF29444A)){muted=!muted;rtc?.mute(muted)};CallControl(if(speaker)Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,"Speaker",Color(0xFF29444A)){speaker=!speaker;rtc?.speaker(speaker)}};if(!busy)CallControl(Icons.Outlined.CallEnd,"End call",Color(0xFFB63042)){scope.launch{end("end")}}}
            }
        }
    }
    if(error.isNotBlank())AlertDialog(onDismissRequest={error=""},title={Text("Voice call")},text={Text(error)},confirmButton={TextButton(onClick={error=""}){Text("OK")}})
}
@Composable private fun CallControl(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,color:Color,onClick:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally){FilledIconButton(onClick=onClick,modifier=Modifier.size(62.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=color,contentColor=Color.White)){Icon(icon,label,modifier=Modifier.size(26.dp))};Text(label,color=Color.White,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))}}
