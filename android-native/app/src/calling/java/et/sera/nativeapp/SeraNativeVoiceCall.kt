package et.sera.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.testTag
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
internal fun SeraNativeVoiceCallButton(token:String,userId:String,conversation:String,name:String,photo:String?,ink:Color,accent:Color,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit={},host:SeraGlobalCallState?=null){
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
    var quality by remember(conversation){mutableStateOf<String?>(null)}
    var peerGeneration by remember(conversation){mutableIntStateOf(0)}
    var lastAnswer by remember(conversation){mutableStateOf<String?>(null)}
    var showHistory by remember(conversation){mutableStateOf(false)}
    var minimized by remember(conversation){mutableStateOf(false)}
    var cancelRequested by remember(conversation){mutableStateOf(false)}
    var history by remember(conversation){mutableStateOf<List<SeraCallEvent>>(emptyList())}
    var historyLoading by remember(conversation){mutableStateOf(false)}
    suspend fun fetchHistory(){historyLoading=true;try{val a=JSONArray(callRequest(token,"sera_call_history",JSONObject().put("p_conversation",conversation))?:"[]");history=(0 until a.length()).map{val j=a.getJSONObject(it);SeraCallEvent(j.getString("id"),j.getString("caller"),j.getString("state"),j.getString("created_at"),if(j.isNull("connected_seconds"))null else j.optLong("connected_seconds"))}.distinctBy{it.id}}catch(e:CancellationException){throw e}catch(_:Exception){error="Call history unavailable. Try again."}finally{historyLoading=false}}
    var summary by remember(conversation){mutableStateOf<SeraVoiceSummary?>(null)}
    var silenced by remember(conversation){mutableStateOf(false)}
    fun finishSummary(title:String,call:CallRow?,detail:String=""){summary=SeraVoiceSummary(title,if(started>0)(SystemClock.elapsedRealtime()-started)/1000 else null,call?.callee==userId,detail=detail)}
    fun payload()=JSONObject().put("p_conversation",conversation)
    fun closePeer(){peerGeneration++;val previous=rtc;rtc=null;previous?.close()}
    fun cleanup(){closePeer();SeraCallForeground.stop(context,foregroundLease);foregroundLease=null;phase="";muted=false;speaker=false;routeMenu=false;routes=emptyList();routeLabel="Audio";started=0;seconds=0;quality=null;lastAnswer=null;row=null;minimized=false;cancelRequested=false}
    suspend fun end(action:String,message:String="",target:CallRow?=null){
        val call=target?:row;call?.let{SeraPush.clearCallNotification(context,it.id)};if(call!=null||phase.isNotBlank())finishSummary(when(action){"decline"->"Call declined";"fail"->"Connection lost";else->if(started>0)"Call ended" else "Call cancelled"},call,message);cleanup()
        if(call!=null&&call.live)runCatching{callRequest(token,"sera_call_transition",payload().put("p_id",call.id).put("p_action",action));withContext(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",call.id)}}.onFailure{error="Call closed locally. Remote status will expire."}
    }
    fun peer(c:JSONObject):SeraNativeRtc{
        onAudioStart()
        val generation=++peerGeneration
        return SeraNativeRtc(context,c){state->scope.launch{
            if(rtc!=null&&generation==peerGeneration&&!cancelRequested){when(state){"connected"->{phase="Connected";if(started==0L)started=SystemClock.elapsedRealtime()};"connecting"->if(row?.state=="accepted")phase="Connecting…";"disconnected"->phase="Reconnecting…";"failed"->if(!busy)end("fail","Call connection failed.")}}
        }}.also{rtc=it}
    }
    suspend fun prepareWithFallback(fresh:JSONObject,invitation:CallRow?):String{
        val attempts=seraRelayAttempts(fresh)
        if(attempts.isEmpty())throw SeraRelayUnavailable()
        for((index,attempt) in attempts.withIndex()){
            if(cancelRequested)throw CancellationException("Call cancelled")
            val remaining=if(invitation==null)attempt.budgetMs else (callRingWindowMillis(invitation.expiresAt,System.currentTimeMillis())-4000L).coerceAtMost(attempt.budgetMs)
            if(remaining<2500L)throw SeraRelayUnavailable()
            phase=attempt.label
            val engine=peer(attempt.config)
            val result=try{kotlinx.coroutines.withTimeoutOrNull(remaining){if(invitation==null)engine.offer() else engine.answer(invitation.offer?:error("Call expired."))}}
                catch(e:SeraRelayUnavailable){null}
            if(result!=null&&!cancelRequested)return result
            closePeer()
            if(cancelRequested)throw CancellationException("Call cancelled")
            android.util.Log.i("SeraRtc","Secure relay attempt "+(index+1)+" unavailable")
        }
        throw SeraRelayUnavailable()
    }
    fun cancelPreparation(){
        cancelRequested=true;phase="Cancelling…";closePeer()
        // Do not cancel an in-flight signaling write: its response is needed to close any created session.
    }
    fun begin(accept:Boolean){
        if(busy)return;busy=true;summary=null;silenced=false;cancelRequested=false;minimized=false;error="";phase="Preparing call…"
        preparation=scope.launch{try{
            if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))error("Open SERA to begin a call.")
            foregroundLease=SeraCallForeground.start(context){val current=row;preparation?.cancel();scope.launch{end("end",target=current)}}
            val fresh=managedCallConfig(token,conversation,true)?:error("Secure call relay unavailable. Try again.")
            if(!fresh.optBoolean("enabled"))error("Secure calling access unavailable. Try again later.")
            if(cancelRequested){cleanup();return@launch};config=fresh
            if(accept){val s=row?:error("Call ended.");val answer=prepareWithFallback(fresh,s);if(cancelRequested){end("decline",target=s);return@launch};val next=parseCall(callRequest(token,"sera_call_transition",payload().put("p_id",s.id).put("p_action","accept").put("p_answer",answer)))?:error("Call ended.");if(!next.live)error("Call ended.");row=next;if(cancelRequested){end("end",target=next);return@launch};scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",next.id)};if(started==0L)phase="Connecting…"}
            else{val offer=prepareWithFallback(fresh,null);if(cancelRequested){cleanup();return@launch};val s=parseCall(callRequest(token,"sera_call_start",payload().put("p_id",UUID.randomUUID().toString()).put("p_offer",offer)))?:error("Call unavailable.");row=s;if(cancelRequested){end("end",target=s);return@launch};phase="Calling…";scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call",s.id)}}
        }catch(e:kotlinx.coroutines.TimeoutCancellationException){if(accept)end(if(cancelRequested)"decline" else "fail",if(cancelRequested)"" else "Call preparation timed out.")else{val cancelled=cancelRequested;cleanup();if(!cancelled)error="Call preparation timed out."}}catch(e:CancellationException){if(cancelRequested){if(accept)end("decline",target=row)else{finishSummary("Call cancelled",row);cleanup()}}else{cleanup();throw e}}catch(e:LinkageError){android.util.Log.e("SeraRtc","Native call engine linkage failure",e);if(accept)end("fail","Voice engine could not start. Update Sera.")else{cleanup();error="Voice engine could not start. Update Sera."}}catch(e:Exception){android.util.Log.e("SeraRtc","Call preparation failed: "+e.javaClass.simpleName);if(accept)end("fail",e.message?:"Call unavailable.")else{val cancelled=cancelRequested;if(!cancelled)finishSummary(if(e is SeraRelayUnavailable)"Unable to connect" else "Call unavailable",row,e.message?:"Try again.");cleanup()}}finally{busy=false}}
    }
    var permissionAccept by remember{mutableStateOf(false)}
    var permissionPending by remember{mutableStateOf(false)}
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permissionPending=false;if(it)begin(permissionAccept)else error="Microphone permission is required for voice calling."}
    fun request(accept:Boolean){permissionAccept=accept;if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){error="Enable SERA notifications in Android settings before starting a call.";return};if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin(accept)else {permissionPending=true;micPermission.launch(Manifest.permission.RECORD_AUDIO)}}
    LaunchedEffect(token,conversation){config=runCatching{managedCallConfig(token,conversation,false)}.getOrNull()}
    val enabled=config?.optBoolean("enabled")==true
    LaunchedEffect(enabled,token,conversation,lifecycle){
        if(!enabled)return@LaunchedEffect
        var failureSince=0L
        while(true){
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)||SeraCallForeground.owns(foregroundLease)){
                if(!busy){try{
                    val old=row;val next=parseCall(callRequest(token,"sera_call_status",payload().put("p_id",old?.id?:JSONObject.NULL)));if(busy||row?.id!=old?.id){delay(1500);continue};failureSince=0L
                    if(next==null){if(old!=null){finishSummary("Call unavailable",old);cleanup()}}
                    else if(!next.live){if(old!=null){finishSummary(when(next.state){"missed"->if(old.callee==userId)"Missed call" else "No answer";"declined"->"Call declined";"failed"->"Connection lost";else->if(started>0)"Call ended" else "Call cancelled"},old);cleanup()}}
                    else if(next.state=="accepted"&&rtc==null){if(old!=null){cleanup();error="Call answered on another device."}}
                    else if(old!=null||next.callee==userId){
                        if(old==null){summary=null;silenced=false;minimized=false}
                        row=next
                        if(next.state=="accepted"&&next.caller==userId&&next.answer!=null&&lastAnswer!=next.answer){rtc?.remoteAnswer(next.answer);lastAnswer=next.answer;if(started==0L)phase="Connecting…"}
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){if(!busy&&row!=null){if(failureSince==0L)failureSince=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-failureSince>=30000L)end("fail","Call signaling lost. Try again.")}}}
            }
            delay(1500)
        }
    }
    LaunchedEffect(enabled,token,conversation,lifecycle){if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){try{val a=JSONArray(callRequest(token,"sera_call_history",payload())?:"[]");onHistory((0 until a.length()).map{val j=a.getJSONObject(it);SeraCallEvent(j.getString("id"),j.getString("caller"),j.getString("state"),j.getString("created_at"),if(j.isNull("connected_seconds"))null else j.optLong("connected_seconds"))})}catch(e:CancellationException){throw e}catch(_:Exception){};delay(10000)}}}
    LaunchedEffect(row?.id,row?.state,started){
        val id=row?.id?:return@LaunchedEffect
        if(row?.state!="accepted"||started<=0L)return@LaunchedEffect
        while(row?.id==id&&row?.state=="accepted"){
            if(phase=="Connected"){
                val confirmed=try{callRequest(token,"sera_call_confirm_media",payload().put("p_id",id))=="true"}catch(e:CancellationException){throw e}catch(_:Exception){false}
                if(confirmed)break
            }
            delay(4000)
        }
    }
    LaunchedEffect(started){while(started>0){seconds=(SystemClock.elapsedRealtime()-started)/1000;delay(1000)}}
    LaunchedEffect(phase,row?.id){
        quality=null
        if(phase!="Connected")return@LaunchedEffect
        var previous:SeraAudioSample?=null
        while(true){
            val sample=try{rtc?.audioSample()}catch(e:CancellationException){throw e}catch(_:Exception){null}
            quality=sample?.let{seraAudioQuality(previous,it)}
            previous=sample
            delay(4000)
        }
    }
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
    DisposableEffect(incoming,resumed,row?.id,silenced){
        val id=row?.id
        val alert=if(incoming&&resumed&&id!=null&&!silenced&&!SeraPush.isCallSilenced(context,id)&&callRingWindowMillis(row?.expiresAt,System.currentTimeMillis())>0)SeraForegroundRing.start(context)else null
        onDispose{alert?.close()}
    }
    LaunchedEffect(incoming,row?.id){if(incoming){val remaining=callRingWindowMillis(row?.expiresAt,System.currentTimeMillis());delay(remaining);silenced=true}}
    // Local ringback is only an outgoing progress cue, not proof that the remote device rings.
    LaunchedEffect(phase,row?.id,resumed){
        if(phase=="Calling…"&&resumed){
            val tone=runCatching{android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL,35)}.getOrNull()
            try{while(true){tone?.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE,1000);delay(4000)}}finally{tone?.stopTone();tone?.release()}
        }
    }
    val active=row!=null||phase.isNotBlank()
    if(host!=null){
        SideEffect{host.report(conversation,active||busy||permissionPending||summary!=null,enabled)}
        var seenStart by remember{mutableIntStateOf(host.callRequest)}
        var seenReturn by remember{mutableIntStateOf(host.returnRequest)}
        var seenHistory by remember{mutableIntStateOf(host.historyRequest)}
        LaunchedEffect(host.callRequest){if(seenStart!=host.callRequest){seenStart=host.callRequest;host.consumeStart();request(false)}}
        LaunchedEffect(host.returnRequest){if(seenReturn!=host.returnRequest){seenReturn=host.returnRequest;minimized=false}}
        LaunchedEffect(host.historyRequest){if(seenHistory!=host.historyRequest){seenHistory=host.historyRequest;showHistory=true;fetchHistory()}}
        if(active&&minimized)SeraOngoingCallStrip(name,photo,if(started>0)seraCallDuration(seconds) else phase.ifBlank{"Calling…"},muted,onRestore={minimized=false},onMute={muted=!muted;rtc?.mute(muted)},onEnd={if(busy){cancelPreparation()}else scope.launch{end("end")}},canMute=row?.state=="accepted")
    }
    if(!enabled&&summary==null&&!active)return
    if(host==null)IconButton(onClick={if(active)minimized=false else request(false)},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Call,voiceText(if(active)"Return to call" else "Voice call"),tint=if(active)accent else ink)}
    if(host==null&&minimized&&active){
        TextButton(onClick={minimized=false}){Text(if(started>0)seraCallDuration(seconds) else voiceText("Return to call"),color=accent,maxLines=1)}
    }
    if(host==null&&!active)IconButton(onClick={showHistory=true;scope.launch{fetchHistory()}},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.History,voiceText("Recent calls"),tint=ink)}
    if(showHistory)Dialog(onDismissRequest={showHistory=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface){
            SeraVoiceHistoryContent(name,photo,history,userId,historyLoading,onCall={showHistory=false;request(false)},onClose={showHistory=false})
        }
    }
    if(active&&!minimized)Dialog(onDismissRequest={if(!incoming)minimized=true},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,dismissOnBackPress=!incoming)){
        val colors=MaterialTheme.colorScheme
        val dark=ink.luminance()>.5f
        val bg=if(dark)Color(0xFF0C171B) else Color(0xFFF3FAF9)
        val halo=if(dark)Color(0xFF173438) else Color(0xFFE5F3F1)
        val primary=colors.onSurface
        val secondary=colors.onSurfaceVariant
        val teal=colors.primary
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg,halo,bg))).systemBarsPadding().padding(horizontal=24.dp,vertical=12.dp).testTag("sera_voice_screen"),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                if(!incoming)IconButton(onClick={minimized=true}){Icon(Icons.Outlined.KeyboardArrowDown,voiceText("Minimize call"),tint=primary)}else Spacer(Modifier.size(48.dp))
                Text("Sera Voice",color=teal,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                SeraVoiceAvatar(name,photo,teal,incoming||phase=="Calling…"||busy)
                Spacer(Modifier.height(20.dp))
                Text(name,color=primary,fontSize=27.sp,fontWeight=FontWeight.SemiBold,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(targetState=if(incoming)"Incoming voice call" else phase.ifBlank{"Calling…"},label="call status"){status->
                    Text(voiceText(status),color=if(status=="Reconnecting…")colors.error else secondary,fontSize=15.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                if(busy&&!cancelRequested){Spacer(Modifier.height(18.dp));LinearProgressIndicator(Modifier.fillMaxWidth(.65f));Spacer(Modifier.height(8.dp));Text(voiceText("Secure voice connection"),color=secondary,fontSize=12.sp)}
                if(started>0){Spacer(Modifier.height(10.dp));Text(seraCallDuration(seconds),color=primary,fontSize=28.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,modifier=Modifier.testTag("sera_voice_timer"))}
                if(muted){Spacer(Modifier.height(8.dp));Text(voiceText("Microphone muted"),color=teal,fontSize=12.sp)}
                quality?.let{Spacer(Modifier.height(8.dp));Text(voiceText(it),color=colors.error,fontSize=12.sp)}
            }
            Column(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                if(incoming){
                    TextButton(onClick={silenced=true;row?.let{SeraPush.silenceCall(context,it.id,userId)}},enabled=!silenced){Icon(Icons.Outlined.VolumeOff,null);Spacer(Modifier.width(6.dp));Text(voiceText(if(silenced)"Ringtone silenced" else "Silence ringtone"))}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                        SeraVoiceControl(Icons.Outlined.CallEnd,"Decline",destructive=true){scope.launch{end("decline")}}
                        SeraVoiceControl(Icons.Outlined.Call,"Answer",accept=true){request(true)}
                    }
                }else{
                    if(row?.state=="accepted"){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                            SeraVoiceControl(if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic,if(muted)"Unmute" else "Mute",active=muted){muted=!muted;rtc?.mute(muted)}
                            Box{
                                SeraVoiceControl(Icons.Outlined.VolumeUp,routeLabel,active=speaker){routes=runCatching{rtc?.routes().orEmpty()}.getOrDefault(emptyList());routeMenu=true}
                                DropdownMenu(expanded=routeMenu,onDismissRequest={routeMenu=false}){
                                    routes.forEach{route->DropdownMenuItem(text={Text(voiceText(route.label))},onClick={routeMenu=false;if(rtc?.selectRoute(route.id)==true){routeLabel=route.label;speaker=route.label=="Speaker"}else error="This audio device is no longer available."})}
                                    if(routes.isEmpty())DropdownMenuItem(text={Text(voiceText("No audio routes available"))},onClick={routeMenu=false})
                                }
                            }
                            SeraVoiceControl(Icons.Outlined.ChatBubbleOutline,"Messages"){minimized=true}
                        }
                    }
                    SeraVoiceControl(Icons.Outlined.CallEnd,if(cancelRequested)"Cancelling…" else "End call",destructive=true,enabled=!cancelRequested){
                        if(busy){cancelPreparation()}else scope.launch{end("end")}
                    }
                }
                Text(voiceText("Sera · Voice call"),color=secondary,fontSize=11.sp)
            }
        }
    }
    summary?.takeIf{!active}?.let{result->
        Dialog(onDismissRequest={summary=null},properties=DialogProperties(usePlatformDefaultWidth=false)){
            Surface(Modifier.fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface){
                SeraVoiceSummaryContent(name,photo,result,onAgain={summary=null;request(false)},onDone={summary=null})
            }
        }
    }
    if(error.isNotBlank())AlertDialog(onDismissRequest={error=""},title={Text(voiceText("Voice call"))},text={Text(voiceText(error))},confirmButton={TextButton(onClick={error=""}){Text(voiceText("OK"))}})
}
private suspend fun managedCallConfig(token:String,conversation:String,issue:Boolean):JSONObject?=withContext(Dispatchers.IO){
 val c=(java.net.URL("https://sera.hisabtech.com/api/native/call-config").openConnection() as java.net.HttpURLConnection).apply{requestMethod="POST";doOutput=true;instanceFollowRedirects=false;connectTimeout=10000;readTimeout=15000;setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json")}
 try{c.outputStream.use{it.write(JSONObject().put("conversation",conversation).put("issue",issue).toString().toByteArray(Charsets.UTF_8))};if(c.responseCode !in 200..299)null else JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}
}
