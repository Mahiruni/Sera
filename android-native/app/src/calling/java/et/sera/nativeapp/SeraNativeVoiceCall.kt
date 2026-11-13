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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.History
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

private data class CallRow(val id:String,val caller:String,val callee:String,val state:String,val offer:String?,val answer:String?,val expiresAt:String){
    val live get()=state=="ringing"||state=="accepted"
}
private fun parseCall(raw:String?):CallRow?{if(raw.isNullOrBlank()||raw=="null")return null;val j=JSONObject(raw);return CallRow(j.getString("id"),j.getString("caller"),j.getString("callee"),j.getString("state"),j.optString("offer").takeUnless{it.isBlank()||it=="null"},j.optString("answer").takeUnless{it.isBlank()||it=="null"},j.optString("expires_at"))}
private suspend fun callRequest(token:String,fn:String,payload:JSONObject):String?=withContext(Dispatchers.IO){val r=chatRpc(token,fn,payload);if(r.first==null)error(r.second.ifBlank{"Call unavailable."});r.first}
@Composable
internal fun SeraNativeVoiceCallButton(token:String,userId:String,conversation:String,name:String,photo:String?,ink:Color,accent:Color,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit={}){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var foregroundLease by remember(conversation){mutableStateOf<String?>(null)}
    var preparation by remember(conversation){mutableStateOf<kotlinx.coroutines.Job?>(null)}
    var config by remember(conversation){mutableStateOf<JSONObject?>(null)}
    var row by remember(conversation){mutableStateOf<CallRow?>(null)}
    var rtc by remember(conversation){mutableStateOf<SeraNativeRtc?>(null)}
    var phase by remember(conversation){mutableStateOf("")}
    var error by remember(conversation){mutableStateOf("")}
    var busy by remember(conversation){mutableStateOf(false)}
    var muted by remember(conversation){mutableStateOf(false)}
    var speaker by remember(conversation){mutableStateOf(false)}
    var routeMenu by remember(conversation){mutableStateOf(false)}
    var routes by remember(conversation){mutableStateOf<List<SeraAudioRoute>>(emptyList())}
    var routeLabel by remember(conversation){mutableStateOf("Audio") }
    var started by remember(conversation){mutableLongStateOf(0L)}
    var seconds by remember(conversation){mutableLongStateOf(0L)}
    var lastAnswer by remember(conversation){mutableStateOf<String?>(null)}
    var showHistory by remember(conversation){mutableStateOf(false)}
    var minimized by remember(conversation){mutableStateOf(false)}
    var cancelRequested by remember(conversation){mutableStateOf(false)}
    var history by remember(conversation){mutableStateOf<List<String>>(emptyList())}
    fun payload()=JSONObject().put("p_conversation",conversation)
    fun cleanup(){rtc?.close();rtc=null;SeraCallForeground.stop(context,foregroundLease);foregroundLease=null;phase="";muted=false;speaker=false;routeMenu=false;routes=emptyList();routeLabel="Audio";started=0;seconds=0;lastAnswer=null;row=null;minimized=false;cancelRequested=false}
    suspend fun end(action:String,message:String="",target:CallRow?=null){
        val call=target?:row;call?.let{SeraPush.clearCallNotification(context,it.id)};cleanup();if(message.isNotBlank())error=message
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
        if(busy)return;busy=true;cancelRequested=false;minimized=false;error="";phase="Preparing call…"
        preparation=scope.launch{try{
            if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))error("Open SERA to begin a call.")
            foregroundLease=SeraCallForeground.start(context){val current=row;preparation?.cancel();scope.launch{end("end",target=current)}}
            val fresh=managedCallConfig(token,conversation,true)?:error("Secure call relay unavailable. Try again.")
            if(!fresh.optBoolean("enabled"))error("Secure call relay unavailable. Try again.")
            if(cancelRequested){cleanup();return@launch};config=fresh;val p=peer()
            if(accept){val s=row?:error("Call ended.");val answer=p.answer(s.offer?:error("Call expired."));if(cancelRequested){end("decline",target=s);return@launch};val next=parseCall(callRequest(token,"sera_call_transition",payload().put("p_id",s.id).put("p_action","accept").put("p_answer",answer)))?:error("Call ended.");if(!next.live)error("Call ended.");row=next;if(cancelRequested){end("end",target=next);return@launch};scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",next.id)};if(started==0L)phase="Connecting…"}
            else{val offer=p.offer();if(cancelRequested){cleanup();return@launch};val s=parseCall(callRequest(token,"sera_call_start",payload().put("p_id",UUID.randomUUID().toString()).put("p_offer",offer)))?:error("Call unavailable.");row=s;if(cancelRequested){end("end",target=s);return@launch};phase="Calling…";scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call",s.id)}}
        }catch(e:kotlinx.coroutines.TimeoutCancellationException){if(accept)end(if(cancelRequested)"decline" else "fail",if(cancelRequested)"" else "Call preparation timed out.")else{val cancelled=cancelRequested;cleanup();if(!cancelled)error="Call preparation timed out."}}catch(e:CancellationException){cleanup();throw e}catch(e:Exception){if(accept)end("fail",e.message?:"Call unavailable.")else{val cancelled=cancelRequested;cleanup();if(!cancelled)error=e.message?:"Call unavailable."}}finally{busy=false}}
    }
    var permissionAccept by remember{mutableStateOf(false)}
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)begin(permissionAccept)else error="Microphone permission is required for voice calling."}
    fun request(accept:Boolean){permissionAccept=accept;if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){error="Enable SERA notifications in Android settings before starting a call.";return};if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin(accept)else micPermission.launch(Manifest.permission.RECORD_AUDIO)}
    LaunchedEffect(token,conversation){config=runCatching{managedCallConfig(token,conversation,false)}.getOrNull()}
    val enabled=config?.optBoolean("enabled")==true
    LaunchedEffect(enabled,token,conversation,lifecycle){
        if(!enabled)return@LaunchedEffect
        var failureSince=0L
        while(true){
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)||SeraCallForeground.owns(foregroundLease)){
                if(!busy){try{
                    val old=row;val next=parseCall(callRequest(token,"sera_call_status",payload().put("p_id",old?.id?:JSONObject.NULL)));if(busy||row?.id!=old?.id){delay(1500);continue};failureSince=0L
                    if(next==null){if(old!=null){cleanup();error="Call is no longer available."}}
                    else if(!next.live){if(old!=null){cleanup();error=when(next.state){"missed"->"No answer";"declined"->"Call declined";"failed"->"Call connection failed";else->"Call ended"}}}
                    else if(next.state=="accepted"&&rtc==null){if(old!=null){cleanup();error="Call answered on another device."}}
                    else if(old!=null||next.callee==userId){
                        row=next
                        if(next.state=="accepted"&&next.caller==userId&&next.answer!=null&&lastAnswer!=next.answer){rtc?.remoteAnswer(next.answer);lastAnswer=next.answer;if(started==0L)phase="Connecting…"}
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){if(!busy&&row!=null){if(failureSince==0L)failureSince=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-failureSince>=30000L)end("fail","Call signaling lost. Try again.")}}}
            }
            delay(1500)
        }
    }
    LaunchedEffect(enabled,token,conversation,lifecycle){if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){try{val a=JSONArray(callRequest(token,"sera_call_history",payload())?:"[]");onHistory((0 until a.length()).map{val j=a.getJSONObject(it);SeraCallEvent(j.getString("id"),j.getString("caller"),j.getString("state"),j.getString("created_at"))})}catch(e:CancellationException){throw e}catch(_:Exception){};delay(10000)}}}
    LaunchedEffect(started){while(started>0){seconds=(SystemClock.elapsedRealtime()-started)/1000;delay(1000)}}
    LaunchedEffect(phase,row?.id){if(phase=="Connecting…"||phase=="Reconnecting…"){delay(45000);if(phase=="Connecting…"||phase=="Reconnecting…")end("fail","Could not establish the call.")}}
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
                }else if(!SeraCallForeground.owns(foregroundLease)){preparation?.cancel();if(current!=null)scope.launch{end("end",target=current)}}
            }
        }
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);preparation?.cancel();rtc?.close();SeraCallForeground.stop(context,foregroundLease)}
    }
    LaunchedEffect(incoming,resumed,row?.id){if(incoming&&resumed)row?.let{SeraPush.clearCallNotification(context,it.id)}}
    DisposableEffect(incoming,resumed,row?.id){
        val id=row?.id
        val ringtone=if(incoming&&resumed&&id!=null&&!SeraPush.isCallSilenced(context,id))runCatching{RingtoneManager.getRingtone(context,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)).apply{audioAttributes=android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();if(android.os.Build.VERSION.SDK_INT>=28)isLooping=true;play()}}.getOrNull()else null
        onDispose{runCatching{ringtone?.stop()}}
    }
    if(!enabled)return
    val active=row!=null||phase.isNotBlank()
    IconButton(onClick={if(active)minimized=false else request(false)},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Call,if(active)"Return to call" else "Voice call",tint=if(active)accent else ink)}
    if(minimized&&active){
        TextButton(onClick={minimized=false}){Text(if(started>0)"%02d:%02d".format(seconds/60,seconds%60) else "Return to call",color=accent,maxLines=1)}
    }
    if(!active)IconButton(onClick={showHistory=true;scope.launch{history=runCatching{val a=JSONArray(callRequest(token,"sera_call_history",payload())?:"[]");(0 until a.length()).map{val j=a.getJSONObject(it);(if(j.getString("caller")==userId)"Outgoing"else "Incoming")+" · "+j.getString("state")+" · "+j.optString("created_at").take(10)}}.getOrDefault(emptyList())}},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.History,"Recent calls",tint=ink)}
    if(showHistory)AlertDialog(onDismissRequest={showHistory=false},title={Text("Recent calls")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(name,fontWeight=FontWeight.SemiBold);if(history.isEmpty())Text("No recent calls") else history.take(10).forEach{Text(it,fontSize=13.sp)}}},confirmButton={TextButton(onClick={showHistory=false;request(false)},enabled=!active&&!busy){Text("Call",color=accent)}},dismissButton={TextButton(onClick={showHistory=false}){Text("Close")}})
    if(active&&!minimized)Dialog(onDismissRequest={if(!incoming)minimized=true},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,dismissOnBackPress=!incoming)){
        val dark=ink.luminance()>.5f
        val bg=if(dark)Color(0xFF102428) else Color(0xFFEAF6F3)
        val surface=if(dark)Color(0xFF1B383C) else Color(0xFFD8EDEA)
        val primary=if(dark)Color(0xFFF4FAF9) else Color(0xFF163D40)
        val secondary=if(dark)Color(0xFFABD0CD) else Color(0xFF426D6D)
        val ring=if(dark)Color(0xFF347C7C) else Color(0xFF9CCFC9)
        val red=Color(0xFFC23B50)
        val teal=Color(0xFF087E83)
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg,surface,bg))).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                if(!incoming)IconButton(onClick={minimized=true}){Icon(Icons.Outlined.KeyboardArrowDown,"Minimize call",tint=primary)}else Spacer(Modifier.size(48.dp))
                Text("Sera voice",color=secondary,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(154.dp).border(1.dp,ring.copy(alpha=.45f),CircleShape),contentAlignment=Alignment.Center){
                Box(Modifier.size(130.dp).background(ring.copy(alpha=.18f),CircleShape).border(1.dp,ring.copy(alpha=.65f),CircleShape),contentAlignment=Alignment.Center){
                    Box(Modifier.size(106.dp).clip(CircleShape).background(surface),contentAlignment=Alignment.Center){
                        Text(name.trim().split(" ").filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()},fontSize=32.sp,color=primary,fontWeight=FontWeight.SemiBold)
                        photo?.let{SeraMediaImage(it,"Profile photo",212,212,Modifier.fillMaxSize())}
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(name,color=primary,fontSize=28.sp,fontWeight=FontWeight.Bold,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(if(incoming)"Incoming voice call"else phase.ifBlank{"Calling…"},color=secondary,fontSize=15.sp)
            if(started>0){Spacer(Modifier.height(8.dp));Text("%02d:%02d".format(seconds/60,seconds%60),color=primary,fontSize=24.sp,fontWeight=FontWeight.SemiBold)}
            Spacer(Modifier.height(56.dp))
            if(incoming){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                    CallControl(Icons.Outlined.CallEnd,"Decline",red,primary){scope.launch{end("decline")}}
                    CallControl(Icons.Outlined.Call,"Answer",teal,primary){request(true)}
                }
            }else{
                if(row?.state=="accepted"){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                        CallControl(if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic,if(muted)"Unmute"else "Mute",if(muted)teal else Color(0xFF35565B),primary){muted=!muted;rtc?.mute(muted)}
                        Box{
                            CallControl(Icons.Outlined.VolumeUp,routeLabel,if(speaker)teal else Color(0xFF35565B),primary){routes=runCatching{rtc?.routes().orEmpty()}.getOrDefault(emptyList());routeMenu=true}
                            DropdownMenu(expanded=routeMenu,onDismissRequest={routeMenu=false}){
                                routes.forEach{route->DropdownMenuItem(text={Text(route.label)},onClick={
                                    routeMenu=false
                                    if(rtc?.selectRoute(route.id)==true){routeLabel=route.label;speaker=route.label=="Speaker"}
                                    else error="This audio device is no longer available."
                                })}
                                if(routes.isEmpty())DropdownMenuItem(text={Text("No audio routes available")},onClick={routeMenu=false})
                            }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
                CallControl(Icons.Outlined.CallEnd,if(cancelRequested)"Cancelling…" else "End call",red,primary){
                    if(busy){cancelRequested=true;phase="Cancelling…"}else scope.launch{end("end")}
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Voice call",color=secondary,fontSize=12.sp)
        }
    }
    if(error.isNotBlank())AlertDialog(onDismissRequest={error=""},title={Text("Voice call")},text={Text(error)},confirmButton={TextButton(onClick={error=""}){Text("OK")}})
}
@Composable private fun CallControl(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,color:Color,labelColor:Color,onClick:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally){FilledIconButton(onClick=onClick,modifier=Modifier.size(62.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=color,contentColor=Color.White)){Icon(icon,label,modifier=Modifier.size(26.dp))};Text(label,color=labelColor,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))}}

private suspend fun managedCallConfig(token:String,conversation:String,issue:Boolean):JSONObject?=withContext(Dispatchers.IO){
 val c=(java.net.URL("https://sera.hisabtech.com/api/native/call-config").openConnection() as java.net.HttpURLConnection).apply{requestMethod="POST";doOutput=true;instanceFollowRedirects=false;connectTimeout=10000;readTimeout=15000;setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json")}
 try{c.outputStream.use{it.write(JSONObject().put("conversation",conversation).put("issue",issue).toString().toByteArray(Charsets.UTF_8))};if(c.responseCode !in 200..299)null else JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}
}
