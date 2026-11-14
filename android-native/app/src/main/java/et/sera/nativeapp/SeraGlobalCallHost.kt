package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONArray

internal data class SeraCallTarget(val conversation:String,val name:String,val photo:String?,val photoPath:String?=null)
internal class SeraGlobalCallState {
    var target by mutableStateOf<SeraCallTarget?>(null)
        private set
    var selected by mutableStateOf<SeraCallTarget?>(null)
        private set
    var pinned by mutableStateOf(false)
        private set
    var eligible by mutableStateOf(false)
        private set
    var videoEligible by mutableStateOf(false)
        private set
    var videoRequest by mutableIntStateOf(0)
        private set
    fun reportVideo(conversation:String,enabled:Boolean){if(target?.conversation==conversation)videoEligible=enabled}
    fun startVideo(conversation:String){if(target?.conversation==conversation&&videoEligible&&!pinned)videoRequest++}
    var callRequest by mutableIntStateOf(0)
        private set
    var returnRequest by mutableIntStateOf(0)
        private set
    var historyRequest by mutableIntStateOf(0)
        private set
    var pendingAnswerId by mutableStateOf<String?>(null)
        private set
    fun queueAnswer(id:String){pendingAnswerId=id}
    fun consumeAnswer(id:String):Boolean{if(pendingAnswerId!=id)return false;pendingAnswerId=null;return true}
    var pendingIncomingId by mutableStateOf<String?>(null)
        private set
    var pendingIncomingDeadline by mutableLongStateOf(0L)
        private set
    fun discover(value:SeraCallTarget,id:String,expiresAt:Long){
        if(pinned||target?.conversation==value.conversation||expiresAt<=System.currentTimeMillis())return
        target=value;eligible=false;videoEligible=false;pinned=true;pendingIncomingId=id;pendingIncomingDeadline=expiresAt
    }
    fun completeDiscovery(id:String?){
        if(id!=null&&pendingIncomingId==id){pendingIncomingId=null;pendingIncomingDeadline=0L}
    }
    private var pendingStart=false
    var inboxStartPending by mutableStateOf(false)
        private set
    fun startInbox(value:SeraCallTarget):Boolean{
        if(pinned)return false
        target=value;eligible=false;videoEligible=false;inboxStartPending=true;pinned=true
        return true
    }
    fun consumeInboxStart(conversation:String):Boolean{
        if(!inboxStartPending||target?.conversation!=conversation)return false
        inboxStartPending=false;return true
    }
    private var historySink:((List<SeraCallEvent>)->Unit)?=null
    private var audioStart:(()->Unit)?=null
    fun select(value:SeraCallTarget,onAudio:()->Unit,onHistory:(List<SeraCallEvent>)->Unit){
        selected=value;audioStart=onAudio;historySink=onHistory
        if(!pinned){
            if(target?.conversation!=value.conversation){eligible=false;videoEligible=false}
            target=value
        }else if(target?.conversation==value.conversation){target=value}
    }
    fun unselect(conversation:String){
        if(selected?.conversation!=conversation)return
        selected=null;audioStart=null;historySink=null
        if(!pinned){target=null;eligible=false;videoEligible=false}
    }
    fun report(conversation:String,keep:Boolean,enabled:Boolean){
        if(target?.conversation!=conversation)return
        pinned=keep||pendingStart||inboxStartPending||pendingIncomingId!=null;eligible=enabled
        if(!pinned&&selected?.conversation!=conversation){target=selected;eligible=false;videoEligible=false}
    }
    fun start(conversation:String){
        if(target?.conversation!=conversation||!eligible)return
        if(pinned)returnRequest++ else {pendingStart=true;pinned=true;callRequest++}
    }
    fun updatePhoto(conversation:String,path:String,url:String){val current=target?:return;if(current.conversation==conversation&&current.photoPath==path&&current.photo.isNullOrBlank())target=current.copy(photo=url)}
    fun consumeStart(){pendingStart=false}
    fun restore(){if(pinned)returnRequest++}
    fun history(conversation:String){if(target?.conversation==conversation&&eligible)historyRequest++}
    fun audioStarting(){audioStart?.invoke()}
    fun publishHistory(conversation:String,events:List<SeraCallEvent>){if(selected?.conversation==conversation)historySink?.invoke(events)}
}
internal val LocalSeraGlobalCall=staticCompositionLocalOf<SeraGlobalCallState?>{null}

@Composable internal fun SeraGlobalVoiceHost(state:SeraGlobalCallState,token:String,userId:String){
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(token,userId,lifecycle){
        if(BuildConfig.LIVE_CALLING_INCLUDED&&token.isNotBlank()&&userId.isNotBlank())lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){try{withContext(Dispatchers.IO){chatRpc(token,"sera_call_video_ready",JSONObject())}}catch(e:CancellationException){throw e}catch(_:Exception){};delay(25000)}}
    }
    LaunchedEffect(token,userId,lifecycle,state){
        if(token.isBlank()||userId.isBlank())return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(true){
                if(!state.pinned){
                    try{
                        val result=withContext(Dispatchers.IO){
                            val raw=chatRpc(token,"sera_call_media_pending_incoming",JSONObject()).first
                            val j=raw?.takeUnless{it=="null"}?.let{JSONObject(it)}
                            if(j==null)null else {
                                val id=j.optString("id");val conversation=j.optString("conversation_id");val caller=j.optString("caller")
                                val expires=runCatching{java.time.Instant.parse(j.optString("expires_at")).toEpochMilli()}.getOrDefault(0L)
                                if(!SeraMessageAlerts.validId(id)||!SeraMessageAlerts.validId(conversation)||!SeraMessageAlerts.validId(caller)||expires<=System.currentTimeMillis())null
                                else {
                                    val cards=runCatching{chatRpc(token,"member_cards",JSONObject().put("ids",JSONArray().put(caller))).first?.let{JSONArray(it)}}.getOrNull()
                                    val name=cards?.optJSONObject(0)?.optString("full_name")?.takeIf{it.isNotBlank()}?:"Sera member"
                                    val path=cards?.optJSONObject(0)?.optString("avatar_path")?.takeUnless{it.isBlank()||it=="null"}
                                    Triple(SeraCallTarget(conversation,name,null,path),id,expires)
                                }
                            }
                        }
                        if(result!=null)state.discover(result.first,result.second,result.third)
                    }catch(e:CancellationException){throw e}catch(_:Exception){/* FCM and per-thread polling remain available. */}
                }
                delay(8000)
            }
        }
    }
    LaunchedEffect(state.pendingIncomingId,state.pendingIncomingDeadline){
        val id=state.pendingIncomingId?:return@LaunchedEffect
        delay((state.pendingIncomingDeadline-System.currentTimeMillis()).coerceAtLeast(0L))
        state.completeDiscovery(id)
    }
    val target=state.target ?: return
    LaunchedEffect(token,target.conversation,target.photoPath){
        val path=target.photoPath?:return@LaunchedEffect
        if(target.photo.isNullOrBlank())withContext(Dispatchers.IO){signedPostImage(token,path,"sera-account-private")}?.let{state.updatePhoto(target.conversation,path,it)}
    }
    val c=MaterialTheme.colorScheme
    key(userId,target.conversation){
        SeraNativeVoiceCallButton(token,userId,target.conversation,target.name,target.photo,c.onSurface,c.primary,
            onAudioStart={state.audioStarting()},onHistory={state.publishHistory(target.conversation,it)},host=state)
    }
}

@Composable internal fun SeraConversationCallEntry(token:String,userId:String,conversation:String,name:String,photo:String?,ink:Color,accent:Color,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit,showHistoryIcon:Boolean=true){
    val host=LocalSeraGlobalCall.current
    if(host==null||!BuildConfig.LIVE_CALLING_INCLUDED){
        SeraNativeVoiceCallButton(token,userId,conversation,name,photo,ink,accent,onAudioStart,onHistory)
        return
    }
    val target=SeraCallTarget(conversation,name,photo)
    SideEffect{host.select(target,onAudioStart,onHistory)}
    DisposableEffect(host,conversation){onDispose{host.unselect(conversation)}}
    if(host.target?.conversation==conversation&&host.eligible){
        IconButton(onClick={host.start(conversation)},modifier=Modifier.size(44.dp)){
            Icon(Icons.Outlined.Call,voiceText(if(host.pinned)"Return to call" else "Voice call"),tint=if(MaterialTheme.colorScheme.surface.luminance()<.5f)Color(0xFFC4B5FD) else Color(0xFF4C1D95))
        }
        if(host.videoEligible&&!host.pinned)IconButton(onClick={host.startVideo(conversation)},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.Videocam,voiceText("Video call"),tint=ink)}
        if(showHistoryIcon&&!host.pinned)IconButton(onClick={host.history(conversation)},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.History,voiceText("Recent calls"),tint=ink)}
    }
}
