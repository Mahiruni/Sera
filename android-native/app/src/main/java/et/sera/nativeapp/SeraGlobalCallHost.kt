package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal data class SeraCallTarget(val conversation:String,val name:String,val photo:String?)
internal class SeraGlobalCallState {
    var target by mutableStateOf<SeraCallTarget?>(null)
        private set
    var selected by mutableStateOf<SeraCallTarget?>(null)
        private set
    var pinned by mutableStateOf(false)
        private set
    var eligible by mutableStateOf(false)
        private set
    var callRequest by mutableIntStateOf(0)
        private set
    var returnRequest by mutableIntStateOf(0)
        private set
    var historyRequest by mutableIntStateOf(0)
        private set
    private var pendingStart=false
    private var historySink:((List<SeraCallEvent>)->Unit)?=null
    private var audioStart:(()->Unit)?=null
    fun select(value:SeraCallTarget,onAudio:()->Unit,onHistory:(List<SeraCallEvent>)->Unit){
        selected=value;audioStart=onAudio;historySink=onHistory
        if(!pinned){
            if(target?.conversation!=value.conversation)eligible=false
            target=value
        }
    }
    fun unselect(conversation:String){
        if(selected?.conversation!=conversation)return
        selected=null;audioStart=null;historySink=null
        if(!pinned){target=null;eligible=false}
    }
    fun report(conversation:String,keep:Boolean,enabled:Boolean){
        if(target?.conversation!=conversation)return
        pinned=keep||pendingStart;eligible=enabled
        if(!pinned&&selected?.conversation!=conversation){target=selected;eligible=false}
    }
    fun start(conversation:String){
        if(target?.conversation!=conversation||!eligible)return
        if(pinned)returnRequest++ else {pendingStart=true;pinned=true;callRequest++}
    }
    fun consumeStart(){pendingStart=false}
    fun restore(){if(pinned)returnRequest++}
    fun history(conversation:String){if(target?.conversation==conversation&&eligible)historyRequest++}
    fun audioStarting(){audioStart?.invoke()}
    fun publishHistory(conversation:String,events:List<SeraCallEvent>){if(selected?.conversation==conversation)historySink?.invoke(events)}
}
internal val LocalSeraGlobalCall=staticCompositionLocalOf<SeraGlobalCallState?>{null}

@Composable internal fun SeraGlobalVoiceHost(state:SeraGlobalCallState,token:String,userId:String){
    val target=state.target ?: return
    val c=MaterialTheme.colorScheme
    key(userId,target.conversation){
        SeraNativeVoiceCallButton(token,userId,target.conversation,target.name,target.photo,c.onSurface,c.primary,
            onAudioStart={state.audioStarting()},onHistory={state.publishHistory(target.conversation,it)},host=state)
    }
}

@Composable internal fun SeraConversationCallEntry(token:String,userId:String,conversation:String,name:String,photo:String?,ink:Color,accent:Color,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit){
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
            Icon(Icons.Outlined.Call,voiceText(if(host.pinned)"Return to call" else "Voice call"),tint=if(host.pinned)accent else ink)
        }
        if(!host.pinned)IconButton(onClick={host.history(conversation)},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.History,voiceText("Recent calls"),tint=ink)}
    }
}
