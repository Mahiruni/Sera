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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon as AndroidMaterialIcon
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
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.Lock
import org.webrtc.VideoTrack
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

private data class CallRow(val id:String,val caller:String,val callee:String,val state:String,val offer:String?,val answer:String?,val expiresAt:String,val callerReady:Boolean=false,val calleeReady:Boolean=false,val restartRequested:Boolean=false,val restartGeneration:Int=0,val restartOffer:String?=null,val restartAnswer:String?=null,val mediaMode:String="voice",val callerQuality:String="standard",val calleeQuality:String="standard"){
    val live get()=state=="ringing"||state=="accepted"
}
private fun parseCall(raw:String?):CallRow?{if(raw.isNullOrBlank()||raw=="null")return null;val j=JSONObject(raw);return CallRow(j.getString("id"),j.getString("caller"),j.getString("callee"),j.getString("state"),j.optString("offer").takeUnless{it.isBlank()||it=="null"},j.optString("answer").takeUnless{it.isBlank()||it=="null"},j.optString("expires_at"),j.optBoolean("caller_restart_ready"),j.optBoolean("callee_restart_ready"),j.optBoolean("restart_requested"),j.optInt("restart_generation"),j.optString("restart_offer").takeUnless{it.isBlank()||it=="null"},j.optString("restart_answer").takeUnless{it.isBlank()||it=="null"},j.optString("media_mode","voice"),j.optString("caller_video_quality","standard"),j.optString("callee_video_quality","standard"))}
private suspend fun callRequest(token:String,fn:String,payload:JSONObject):String?=withContext(Dispatchers.IO){val r=chatRpc(token,fn,payload);if(r.first==null)error(r.second.ifBlank{"Call unavailable."});r.first}
@Composable
internal fun SeraNativeVoiceCallButton(token:String,userId:String,conversation:String,name:String,photo:String?,ink:Color,accent:Color,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit={},host:SeraGlobalCallState?=null){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var foregroundLease by remember(conversation){mutableStateOf<String?>(null)}
    var preparation by remember(conversation){mutableStateOf<kotlinx.coroutines.Job?>(null)}
    var config by remember(conversation){mutableStateOf<JSONObject?>(null)}
    var videoAccess by remember(conversation){mutableStateOf<JSONObject?>(null)}
    var videoPicker by remember(conversation){mutableStateOf(false)}
    var videoMode by remember(conversation){mutableStateOf(false)}
    var cameraOn by remember(conversation){mutableStateOf(false)}
    var cameraWanted by remember(conversation){mutableStateOf(false)}
    var videoCap by remember(conversation){mutableStateOf("standard")}
    var videoSaver by remember(conversation){mutableStateOf(false)}
    var videoLevel by remember(conversation){mutableStateOf(SeraVideoLevel.DATA_SAVER)}
    var remoteVideo by remember(conversation){mutableStateOf<VideoTrack?>(null)}
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
    var recoverySince by remember(conversation){mutableLongStateOf(0L)}
    var lastAnswer by remember(conversation){mutableStateOf<String?>(null)}
    var restartApplied by remember(conversation){mutableIntStateOf(0)}
    var restartPendingGeneration by remember(conversation){mutableIntStateOf(0)}
    var restartPendingSdp by remember(conversation){mutableStateOf<String?>(null)}
    var restartAttemptAt by remember(conversation){mutableLongStateOf(0L)}
    var networkChanged by remember(conversation){mutableStateOf(false)}
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
    fun closePeer(){peerGeneration++;remoteVideo=null;cameraOn=false;val previous=rtc;rtc=null;previous?.close()}
    fun cleanup(){closePeer();SeraCallForeground.stop(context,foregroundLease);foregroundLease=null;phase="";muted=false;speaker=false;routeMenu=false;routes=emptyList();routeLabel="Audio";started=0;seconds=0;quality=null;recoverySince=0;lastAnswer=null;restartApplied=0;restartPendingGeneration=0;restartPendingSdp=null;restartAttemptAt=0;networkChanged=false;row=null;minimized=false;cancelRequested=false;videoMode=false;cameraWanted=false;videoCap="standard";videoSaver=false;videoLevel=SeraVideoLevel.DATA_SAVER;videoPicker=false}
    suspend fun end(action:String,message:String="",target:CallRow?=null){
        val call=target?:row;call?.let{SeraPush.clearCallNotification(context,it.id)};if(call!=null||phase.isNotBlank())finishSummary(when(action){"decline"->"Call declined";"fail"->"Connection lost";else->if(started>0)"Call ended" else "Call cancelled"},call,message);cleanup()
        if(call!=null&&call.live)runCatching{callRequest(token,"sera_call_transition",payload().put("p_id",call.id).put("p_action",action));withContext(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",call.id)}}.onFailure{error="Call closed locally. Remote status will expire."}
    }
    suspend fun restartSignal(call:CallRow,action:String,generation:Int=call.restartGeneration,sdp:String?=null):CallRow?=
        parseCall(callRequest(token,"sera_call_restart",payload().put("p_id",call.id).put("p_action",action).put("p_generation",generation).put("p_sdp",sdp?:JSONObject.NULL)))
    suspend fun recoverMedia(call:CallRow){
        val engine=rtc?:return
        if(call.state!="accepted"||lastAnswer==null&&call.caller==userId)return
        val caller=call.caller==userId
        if(!(if(caller)call.callerReady else call.calleeReady)){restartSignal(call,"ready");return}
        if(!call.callerReady||!call.calleeReady)return
        // Retry the same generated description if a signaling response was lost.
        val pending=restartPendingSdp
        if(pending!=null){
            val generation=restartPendingGeneration
            val action=if(caller)"offer" else "answer"
            val result=restartSignal(call,action,generation,pending)
            if(result!=null&&result.restartGeneration==generation){
                restartPendingSdp=null
                if(!caller)restartApplied=generation
            }
            return
        }
        if(caller&&call.restartGeneration>restartApplied&&call.restartAnswer!=null){
            engine.remoteAnswer(call.restartAnswer);restartApplied=call.restartGeneration;return
        }
        if(!caller&&call.restartGeneration>restartApplied&&call.restartOffer!=null){
            if(call.restartAnswer!=null)return // another device already answered this generation
            restartPendingGeneration=call.restartGeneration
            restartPendingSdp=engine.restartAnswer(call.restartOffer)
            val result=restartSignal(call,"answer",restartPendingGeneration,restartPendingSdp)
            if(result!=null){restartApplied=call.restartGeneration;restartPendingSdp=null}
            return
        }
        val now=SystemClock.elapsedRealtime()
        val recovering=phase=="Reconnecting…"||phase=="Connecting…"
        if(caller&&call.restartRequested&&call.restartGeneration<3&&engine.signalingStable()&&call.restartGeneration==restartApplied){
            restartAttemptAt=now;restartPendingGeneration=call.restartGeneration+1
            restartPendingSdp=engine.restartOffer()
            if(restartSignal(call,"offer",restartPendingGeneration,restartPendingSdp)!=null)restartPendingSdp=null
            return
        }
        if((networkChanged&&started>0L||recovering&&recoverySince>0L&&now-recoverySince>=3000L)&&now-restartAttemptAt>=8000L&&call.restartGeneration<3&&call.restartGeneration==restartApplied&&!call.restartRequested){
            restartAttemptAt=now;val result=restartSignal(call,"request");if(result?.restartRequested==true)networkChanged=false
        }
    }
    fun peer(c:JSONObject):SeraNativeRtc{
        onAudioStart()
        val generation=++peerGeneration
        return SeraNativeRtc(context,c,videoMode=videoMode,onRemoteVideo={track->scope.launch{if(generation==peerGeneration)remoteVideo=track}}){state->scope.launch{
            if(rtc!=null&&generation==peerGeneration&&!cancelRequested){when(state){"connected"->{recoverySince=0;phase="Connected";if(started==0L)started=SystemClock.elapsedRealtime()};"connecting","disconnected","failed"->if(row?.state=="accepted"&&!busy){if(recoverySince==0L)recoverySince=SystemClock.elapsedRealtime();phase=if(started>0)"Reconnecting…" else "Connecting…"}}}
        }}.also{rtc=it;it.setVideoQuality(videoCap,videoSaver);if(videoMode&&cameraWanted&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){cameraOn=it.camera(true);if(!cameraOn)error="Camera unavailable. Your audio call continues."}}
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
            if(accept){val s=row?:error("Call ended.");val answer=prepareWithFallback(fresh,s);if(cancelRequested){end("decline",target=s);return@launch};val next=parseCall(callRequest(token,"sera_call_transition",payload().put("p_id",s.id).put("p_action","accept").put("p_answer",answer)))?:error("Call ended.");if(!next.live)error("Call ended.");row=next;if(cancelRequested){end("end",target=next);return@launch};scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call_dismiss",next.id)};if(started==0L){phase="Connecting…";if(recoverySince==0L)recoverySince=SystemClock.elapsedRealtime()}}
            else{val offer=prepareWithFallback(fresh,null);if(cancelRequested){cleanup();return@launch};val request=payload().put("p_id",UUID.randomUUID().toString()).put("p_offer",offer);if(videoMode)request.put("p_quality",videoCap);val s=parseCall(callRequest(token,if(videoMode)"sera_call_start_video" else "sera_call_start",request))?:error("Call unavailable.");row=s;if(cancelRequested){end("end",target=s);return@launch};phase="Calling…";if(!videoMode)scope.launch(Dispatchers.IO){SeraPush.dispatch(token,"voice_call",s.id)}}
        }catch(e:kotlinx.coroutines.TimeoutCancellationException){if(accept)end(if(cancelRequested)"decline" else "fail",if(cancelRequested)"" else "Call preparation timed out.")else{val cancelled=cancelRequested;cleanup();if(!cancelled)error="Call preparation timed out."}}catch(e:CancellationException){if(cancelRequested){if(accept)end("decline",target=row)else{finishSummary("Call cancelled",row);cleanup()}}else{cleanup();throw e}}catch(e:LinkageError){android.util.Log.e("SeraRtc","Native call engine linkage failure",e);if(accept)end("fail","Voice engine could not start. Update Sera.")else{cleanup();error="Voice engine could not start. Update Sera."}}catch(e:Exception){android.util.Log.e("SeraRtc","Call preparation failed: "+e.javaClass.simpleName);if(accept)end("fail",e.message?:"Call unavailable.")else{val cancelled=cancelRequested;if(!cancelled)finishSummary(if(e is SeraRelayUnavailable)"Unable to connect" else "Call unavailable",row,e.message?:"Try again.");cleanup()}}finally{busy=false}}
    }
    var permissionAccept by remember{mutableStateOf(false)}
    var permissionPending by remember{mutableStateOf(false)}
    val audioPrefs=remember(context){context.getSharedPreferences("sera_call_audio_permissions",android.content.Context.MODE_PRIVATE)}
    var micGranted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
    val incomingMicPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        permissionPending=false
        micGranted=granted
        // Permission only: an incoming call is never answered by this callback.
    }
    fun openAudioSettings(){
        runCatching{context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+context.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}
    }
    val callPermissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        permissionPending=false
        val mic=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        micGranted=mic
        if(cameraWanted&&ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){cameraWanted=false;error="Camera unavailable. Your audio call continues."}
        if(mic&&(!permissionAccept||row?.state=="ringing"))begin(permissionAccept)else if(!mic)error="Microphone permission is required for voice calling."
    }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted&&row?.state=="accepted"&&videoMode&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){cameraWanted=true;cameraOn=rtc?.camera(true)==true}
    }
    fun toggleCamera(){
        if(!videoMode||row?.state!="accepted"||!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))return
        if(cameraOn){cameraWanted=false;rtc?.camera(false);cameraOn=false}
        else if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){cameraWanted=true;cameraOn=rtc?.camera(true)==true;if(!cameraOn)error="Camera unavailable. Your audio call continues."}
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    fun request(accept:Boolean,video:Boolean=false,withCamera:Boolean=false,requestedQuality:String="standard"){
        if(busy||permissionPending)return
        permissionAccept=accept
        if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){error="Enable SERA notifications in Android settings before starting a call.";return}
        if(accept&&row?.state!="ringing")return
        if(!accept&&video&&(videoAccess?.optBoolean("enabled")!=true||videoAccess?.optBoolean("peer_ready")!=true)){error="Video unavailable. Check recipient availability or subscription.";return}
        if(!accept&&video&&requestedQuality!="standard"&&videoAccess?.optBoolean("premium")!=true){error="Subscription required";return}
        videoMode=if(accept)row?.mediaMode=="video" else video
        cameraWanted=videoMode&&withCamera
        videoCap=if(accept)"standard" else requestedQuality
        videoPicker=false
        val missing=buildList{if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)add(Manifest.permission.RECORD_AUDIO);if(cameraWanted&&ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)add(Manifest.permission.CAMERA)}
        if(missing.isEmpty())begin(accept)else{if(Manifest.permission.RECORD_AUDIO in missing)audioPrefs.edit().putBoolean("asked",true).apply();permissionPending=true;callPermissions.launch(missing.toTypedArray())}
    }
    LaunchedEffect(token,conversation){config=runCatching{managedCallConfig(token,conversation,false)}.getOrNull();if(config?.optBoolean("enabled")!=true)host?.completeDiscovery(host.pendingIncomingId)}
    val enabled=config?.optBoolean("enabled")==true
    LaunchedEffect(token,conversation,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){try{videoAccess=callRequest(token,"sera_call_video_access",payload())?.let{JSONObject(it)}}catch(e:CancellationException){throw e}catch(_:Exception){};delay(20000)}}}
    LaunchedEffect(enabled,token,conversation,lifecycle){
        if(!enabled)return@LaunchedEffect
        var failureSince=0L
        while(true){
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)||SeraCallForeground.owns(foregroundLease)){
                if(!busy){try{
                    val old=row;val discoveryId=host?.pendingIncomingId;val next=parseCall(callRequest(token,"sera_call_media_status",payload().put("p_id",old?.id?:discoveryId?:JSONObject.NULL).put("p_video",true)));if(busy||row?.id!=old?.id){delay(1500);continue};failureSince=0L
                    if(next==null){if(old!=null){finishSummary("Call unavailable",old);cleanup()}}
                    else if(!next.live){if(old!=null){finishSummary(when(next.state){"missed"->if(old.callee==userId)"Missed call" else "No answer";"declined"->"Call declined";"failed"->"Connection lost";else->if(started>0)"Call ended" else "Call cancelled"},old);cleanup()}}
                    else if(next.state=="accepted"&&rtc==null){if(old!=null){cleanup();error="Call answered on another device."}}
                    else if(old!=null||next.callee==userId){
                        if(old==null){summary=null;silenced=false;minimized=false}
                        row=next
                        if(next.mediaMode=="video"){videoMode=true;videoCap=if(next.caller==userId)next.callerQuality else next.calleeQuality;rtc?.setVideoQuality(videoCap,videoSaver)}
                        if(next.state=="accepted"&&next.caller==userId&&next.answer!=null&&lastAnswer!=next.answer){rtc?.remoteAnswer(next.answer);lastAnswer=next.answer;if(started==0L){phase="Connecting…";if(recoverySince==0L)recoverySince=SystemClock.elapsedRealtime()}}
                        if(next.state=="accepted")try{recoverMedia(next)}catch(_:kotlinx.coroutines.TimeoutCancellationException){/* Keep the fixed recovery deadline; do not cancel status polling. */}
                    }
                    host?.completeDiscovery(discoveryId)
                }catch(e:CancellationException){throw e}catch(_:Exception){if(!busy&&row!=null){if(failureSince==0L)failureSince=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-failureSince>=45000L)end("fail","Call signaling lost. Try again.")}}}
            }
            delay(if(row!=null)1500 else 4000)
        }
    }
    LaunchedEffect(enabled,token,conversation,lifecycle){if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){try{val a=JSONArray(callRequest(token,"sera_call_history",payload())?:"[]");onHistory((0 until a.length()).map{val j=a.getJSONObject(it);SeraCallEvent(j.getString("id"),j.getString("caller"),j.getString("state"),j.getString("created_at"),if(j.isNull("connected_seconds"))null else j.optLong("connected_seconds"))})}catch(e:CancellationException){throw e}catch(_:Exception){};delay(20000)}}}
    DisposableEffect(row?.id,row?.state){
        val manager=context.getSystemService(android.net.ConnectivityManager::class.java)
        var registered=false
        var lastNetwork=manager?.activeNetwork
        val callback=object:android.net.ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network:android.net.Network){
                scope.launch{
                    if(lastNetwork!=null&&lastNetwork!=network&&row?.state=="accepted")networkChanged=true
                    lastNetwork=network
                }
            }
        }
        if(row?.state=="accepted"&&manager!=null)registered=runCatching{manager.registerDefaultNetworkCallback(callback);true}.getOrDefault(false)
        onDispose{if(registered)runCatching{manager?.unregisterNetworkCallback(callback)}}
    }
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
            if(videoMode)videoLevel=rtc?.videoLevel?:SeraVideoLevel.DATA_SAVER
            previous=sample
            delay(4000)
        }
    }
    LaunchedEffect(phase,row?.id,recoverySince){
        if(phase=="Connecting…"||phase=="Reconnecting…"){
            val deadline=(if(recoverySince>0L)recoverySince else SystemClock.elapsedRealtime())+45000L
            delay((deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            if(phase=="Connecting…"||phase=="Reconnecting…")end("fail",if(started>0)"Call connection lost. Try again." else "Could not establish the call.")
        }
    }
    val incoming=row?.state=="ringing"&&row?.callee==userId&&phase.isBlank()
    var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    LaunchedEffect(minimized,resumed){if((minimized||!resumed)&&cameraOn){cameraWanted=false;rtc?.camera(false);cameraOn=false}}
    val latestRow by rememberUpdatedState(row)
    val latestPhase by rememberUpdatedState(phase)
    DisposableEffect(lifecycle,conversation){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_RESUME){resumed=true;micGranted=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED}
            if(event==Lifecycle.Event.ON_PAUSE){resumed=false;if(cameraOn||cameraWanted){cameraWanted=false;rtc?.camera(false);cameraOn=false}}
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
    LaunchedEffect(incoming,resumed,row?.id,host?.pendingAnswerId,permissionPending){
        val id=row?.id
        if(incoming&&resumed&&id!=null&&!permissionPending&&host?.consumeAnswer(id)==true)request(true)
    }
    // On the first visible incoming call, ask the OS for permission without recording or answering.
    LaunchedEffect(incoming,resumed,row?.id,host?.pendingAnswerId,permissionPending,micGranted){
        if(incoming&&resumed&&row!=null&&host?.pendingAnswerId==null&&!permissionPending&&!micGranted&&!audioPrefs.getBoolean("asked",false)){
            audioPrefs.edit().putBoolean("asked",true).apply()
            permissionPending=true
            incomingMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
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
        val discoveryPending=host.pendingIncomingId!=null
        SideEffect{host.report(conversation,active||busy||permissionPending||summary!=null||discoveryPending||videoPicker,enabled);host.reportVideo(conversation,videoAccess?.optBoolean("enabled")==true)}
        var seenVideo by remember{mutableIntStateOf(host.videoRequest)}
        LaunchedEffect(host.videoRequest){if(seenVideo!=host.videoRequest){seenVideo=host.videoRequest;videoPicker=true}}
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
    if(host==null&&videoAccess?.optBoolean("enabled")==true&&!active)IconButton(onClick={videoPicker=true},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Videocam,voiceText("Video call"),tint=ink)}
    if(videoPicker&&!active){
        val premium=videoAccess?.optBoolean("premium")==true;val ready=videoAccess?.optBoolean("peer_ready")==true
        AlertDialog(onDismissRequest={videoPicker=false},title={Text(voiceText("Video quality"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(voiceText("Video adapts to your network. Voice always comes first."))
            if(!ready)Text(voiceText("Recipient must open Sera to receive video calls."))
            listOf("standard" to "Standard · 480p","hd" to "HD · 720p","super_hd" to "Super HD · 1080p").forEach{(q,label)->
                OutlinedButton(onClick={request(false,true,true,q)},enabled=ready&&(q=="standard"||premium),modifier=Modifier.fillMaxWidth()){
                    Icon(if(q!="standard"&&!premium)Icons.Outlined.Lock else Icons.Outlined.Videocam,null);Spacer(Modifier.width(8.dp));Text(label+" · "+voiceText(if(q=="standard")"Free" else "Premium"))
                }
            }
            if(!premium)Text(voiceText("HD and Super HD require an active subscription."))
        }},confirmButton={TextButton(onClick={videoPicker=false}){Text(voiceText("Close"))}})
    }
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
        Box(Modifier.fillMaxSize()){
        SeraCallBackdrop(photo,bg,halo,Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal=24.dp,vertical=12.dp).testTag("sera_voice_screen"),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                if(!incoming)IconButton(onClick={minimized=true}){AndroidMaterialIcon(Icons.Rounded.KeyboardArrowDown,voiceText("Minimize call"),tint=primary)}else Spacer(Modifier.size(48.dp))
                Text(if(videoMode)"Sera Video" else "Sera Voice",color=teal,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                val engine=rtc;val egl=engine?.videoEgl;val remote=remoteVideo;val local=engine?.localCameraTrack
                if(videoMode&&!incoming&&egl!=null&&(remote!=null||cameraOn&&local!=null)){
                    Box(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF102326),RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))){
                        if(remote!=null)SeraCallVideoView(remote,egl,false,Modifier.fillMaxSize())
                        if(cameraOn&&local!=null)SeraCallVideoView(local,egl,true,Modifier.align(Alignment.BottomEnd).padding(8.dp).width(88.dp).height(116.dp))
                    }
                }else SeraVoiceAvatar(name,photo,teal,seraCallShouldAnimate(started,cancelRequested,incoming||active))
                Spacer(Modifier.height(20.dp))
                Text(name,color=primary,fontSize=27.sp,fontWeight=FontWeight.SemiBold,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(targetState=if(incoming)if(videoMode)"Incoming video call" else "Incoming voice call" else phase.ifBlank{"Calling…"},label="call status"){status->
                    Text(voiceText(status),color=if(status=="Reconnecting…")colors.error else secondary,fontSize=15.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                if(busy&&!cancelRequested){Spacer(Modifier.height(18.dp));LinearProgressIndicator(Modifier.fillMaxWidth(.65f));Spacer(Modifier.height(8.dp));Text(voiceText("Secure voice connection"),color=secondary,fontSize=12.sp)}
                if(started>0){Spacer(Modifier.height(10.dp));Text(seraCallDuration(seconds),color=primary,fontSize=28.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,modifier=Modifier.testTag("sera_voice_timer"))}
                if(videoMode&&row?.state=="accepted"){Spacer(Modifier.height(6.dp));Text(voiceText(if(cameraOn)videoLevel.label else "Camera off"),color=secondary,fontSize=12.sp)}
                if(muted){Spacer(Modifier.height(8.dp));Text(voiceText("Microphone muted"),color=teal,fontSize=12.sp)}
                quality?.let{Spacer(Modifier.height(8.dp));Text(voiceText(it),color=colors.error,fontSize=12.sp)}
            }
            Column(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                if(incoming){
                    if(!micGranted&&!permissionPending){Text(voiceText("Microphone permission is required for voice calling."),color=secondary,fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center);TextButton(onClick={openAudioSettings()}){Text(voiceText("Open microphone settings"))}}
                    TextButton(onClick={silenced=true;row?.let{SeraPush.silenceCall(context,it.id,userId)}},enabled=!silenced){AndroidMaterialIcon(Icons.Rounded.VolumeOff,null);Spacer(Modifier.width(6.dp));Text(voiceText(if(silenced)"Ringtone silenced" else "Silence ringtone"))}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                        SeraVoiceControl(Icons.Rounded.CallEnd,"Decline",destructive=true){scope.launch{end("decline")}}
                        SeraVoiceControl(if(videoMode)Icons.Rounded.Videocam else Icons.Rounded.Call,if(videoMode)"Answer with video" else "Answer",accept=true){request(true,withCamera=videoMode)}
                        if(videoMode)SeraVoiceControl(Icons.Rounded.Mic,"Audio only"){request(true,withCamera=false)}
                    }
                }else{
                    if(row?.state=="accepted"){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                            SeraVoiceControl(if(muted)Icons.Rounded.MicOff else Icons.Rounded.Mic,if(muted)"Unmute" else "Mute",active=muted){muted=!muted;rtc?.mute(muted)}
                            Box{
                                SeraVoiceControl(Icons.Rounded.VolumeUp,routeLabel,active=speaker){routes=runCatching{rtc?.routes().orEmpty()}.getOrDefault(emptyList());routeMenu=true}
                                DropdownMenu(expanded=routeMenu,onDismissRequest={routeMenu=false}){
                                    routes.forEach{route->DropdownMenuItem(text={Text(voiceText(route.label))},onClick={routeMenu=false;if(rtc?.selectRoute(route.id)==true){routeLabel=route.label;speaker=route.label=="Speaker"}else error="This audio device is no longer available."})}
                                    if(routes.isEmpty())DropdownMenuItem(text={Text(voiceText("No audio routes available"))},onClick={routeMenu=false})
                                }
                            }
                            if(videoMode)SeraVoiceControl(if(cameraOn)Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,if(cameraOn)"Camera off" else "Camera on",active=cameraOn){toggleCamera()}
                            SeraVoiceControl(Icons.Rounded.ChatBubbleOutline,"Messages"){minimized=true}
                        }
                    }
                    if(videoMode&&row?.state=="accepted"){
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                            TextButton(onClick={videoSaver=!videoSaver;rtc?.setVideoQuality(videoCap,videoSaver)}){Text(voiceText("Data Saver")+": "+voiceText(if(videoSaver)"On" else "Off"))}
                            var qualityMenu by remember{mutableStateOf(false)}
                            Box{TextButton(onClick={qualityMenu=true}){Text(voiceText("Video quality"))};DropdownMenu(expanded=qualityMenu,onDismissRequest={qualityMenu=false}){
                                listOf("standard" to "Standard · 480p","hd" to "HD · 720p","super_hd" to "Super HD · 1080p").forEach{(q,label)->DropdownMenuItem(text={Text(label)},enabled=q=="standard"||videoAccess?.optBoolean("premium")==true,onClick={qualityMenu=false;scope.launch{val id=row?.id?:return@launch;try{val result=parseCall(callRequest(token,"sera_call_video_quality",payload().put("p_id",id).put("p_quality",q)));if(result!=null&&row?.id==id){row=result;videoCap=q;rtc?.setVideoQuality(q,videoSaver)}else error="Subscription required"}catch(_:Exception){error="Video quality unavailable"}}})}
                            }}
                        }
                    }
                    SeraVoiceControl(Icons.Rounded.CallEnd,if(cancelRequested)"Cancelling…" else "End call",destructive=true,enabled=!cancelRequested){
                        if(busy){cancelPreparation()}else scope.launch{end("end")}
                    }
                }
                Text(if(videoMode)"Sera · Video call" else voiceText("Sera · Voice call"),color=secondary,fontSize=11.sp)
            }
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
    if(error.isNotBlank())AlertDialog(onDismissRequest={error=""},title={Text(voiceText("Voice call"))},text={Text(voiceText(error))},confirmButton={TextButton(onClick={if(error=="Microphone permission is required for voice calling.")openAudioSettings();error=""}){Text(voiceText(if(error=="Microphone permission is required for voice calling.")"Open microphone settings" else "OK"))}},dismissButton={if(error=="Microphone permission is required for voice calling.")TextButton(onClick={error=""}){Text(voiceText("Close"))}})
}
private suspend fun managedCallConfig(token:String,conversation:String,issue:Boolean):JSONObject?=withContext(Dispatchers.IO){
 val c=(java.net.URL("https://sera.hisabtech.com/api/native/call-config").openConnection() as java.net.HttpURLConnection).apply{requestMethod="POST";doOutput=true;instanceFollowRedirects=false;connectTimeout=10000;readTimeout=15000;setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json")}
 try{c.outputStream.use{it.write(JSONObject().put("conversation",conversation).put("issue",issue).toString().toByteArray(Charsets.UTF_8))};if(c.responseCode !in 200..299)null else JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}
}
