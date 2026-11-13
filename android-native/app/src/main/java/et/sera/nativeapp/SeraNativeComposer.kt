package et.sera.nativeapp

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.ui.text.style.TextOverflow
import java.util.UUID
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


@Composable
internal fun SeraNativeComposer(
    draft:String,attachment:NativeChatSelection?,album:List<NativeChatSelection>,reply:NativeMessage?,queued:NativePending?,sending:Boolean,checkingFile:Boolean,connectionAvailable:Boolean,sendFailed:Boolean,transferProgress:Int,recordingVoice:Boolean,
    ink:Color,soft:Color,muted:Color,accent:Color,teal:Color,playback:SeraChatPlayback,
    onDraftChange:(String)->Unit,onAttach:()->Unit,onRemoveAttachment:()->Unit,onRemovePhoto:(Int)->Unit,onCancelReply:()->Unit,onCancelUpload:()->Unit,onRetry:()->Unit,onEditQueued:()->Unit,onVoice:(NativeChatSelection)->Unit,onRecording:(Boolean)->Unit,onError:(String)->Unit,onSend:()->Unit
){
    val scope=rememberCoroutineScope()
    var pdfPreview by remember(attachment){mutableStateOf(false)}
    if(pdfPreview&&attachment?.mime=="application/pdf")SeraPdfPreview(attachment,onDismiss={pdfPreview=false})
    reply?.let{m->
        Surface(color=soft,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text("Replying to: "+m.body.take(100),modifier=Modifier.weight(1f),fontSize=12.sp,color=ink);IconButton(onClick=onCancelReply){Icon(Icons.Outlined.Close,"Cancel reply",tint=muted)}}
        }
    }
    queued?.takeIf{it.file!=null||it.album.isNotEmpty()}?.let{q->
        Surface(color=soft,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(10.dp)){
                Text(q.file?.name ?: "${q.album.size} photos",maxLines=1,color=ink,fontSize=12.sp)
                if(sending&&transferProgress in 0..99)LinearProgressIndicator(progress={transferProgress/100f},modifier=Modifier.fillMaxWidth().padding(vertical=6.dp),color=accent)
                Text(if(sending&&transferProgress in 0..99)"Uploading · $transferProgress%" else if(sending&&transferProgress==100)"Upload complete · sending message…" else if(sending)"Sending…" else if(q.paused)"Upload paused · retry" else if(!connectionAvailable)"Queued · waiting for connection" else if(sendFailed)"Not sent · retry" else "Queued",fontSize=12.sp,color=accent)
                if(sending&&!q.paused&&transferProgress in 0..99)TextButton(onClick=onCancelUpload){Text("Cancel upload")} else if(!sending&&(q.paused||sendFailed||!connectionAvailable))Row{TextButton(onClick=onRetry,enabled=connectionAvailable){Text("Retry")};TextButton(onClick=onEditQueued){Text("Edit message")}}
            }
        }
    }
    if(album.isNotEmpty())SeraAlbumPreview(album,soft,ink,muted,accent,sending,onRemovePhoto)
    attachment?.let{file->
        Surface(color=soft,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                if(file.mime=="video/mp4"){Box(Modifier.weight(1f)){SeraVideoDraftPreview(file,accent,onAudioStart={playback.pause()})}}else if(file.mime.startsWith("audio/")){
                    Box(Modifier.weight(1f)){SeraChatVoicePlayer(playback,"preview:"+System.identityHashCode(file),file.durationMs,accent,label="Voice preview",onPlay={scope.launch{playback.toggle("preview:"+System.identityHashCode(file),file.mime,{file.bytes},onError)}})}
                }else{
                    SeraAttachmentThumbnail(file,accent);Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){Text(file.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,fontSize=13.sp,color=ink);Text("${file.bytes.size/1024} KB · Ready to send",fontSize=11.sp,color=muted);if(file.mime=="application/pdf")TextButton(onClick={pdfPreview=true},contentPadding=PaddingValues(0.dp)){Text("Preview PDF",fontSize=12.sp,color=accent)}}
                }
                IconButton(onClick=onRemoveAttachment,enabled=!sending){Icon(Icons.Outlined.Close,"Remove attachment",tint=ink)}
            }
        }
    }
    val hasContent = draft.isNotBlank() || attachment != null || album.isNotEmpty()
    val locked = sending || queued != null
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(recordingVoice) { if (recordingVoice) keyboard?.hide() }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (!recordingVoice) {
            IconButton(onClick = onAttach, enabled = !locked && !checkingFile,
                modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.AttachFile, "Open attachment studio", tint = if (locked || checkingFile) muted else accent)
            }
            SeraMessageInput(draft, onDraftChange, ink, soft, muted, accent,
                readOnly = locked, modifier = Modifier.weight(1f))
        }
        if (recordingVoice || (!hasContent && !locked)) {
            SeraChatVoice(enabled = !locked && !checkingFile && attachment == null && album.isEmpty(),
                onReady = onVoice, onError = onError, onRecordingChange = onRecording, accent = accent)
        } else if (!recordingVoice) {
            SeraMessageSendButton(enabled = hasContent && !locked && !checkingFile,
                busy = sending, teal = teal, onSend = onSend)
        }
    }

}

@Composable private fun SeraAlbumPreview(album:List<NativeChatSelection>,soft:Color,ink:Color,muted:Color,accent:Color,sending:Boolean,onRemove:(Int)->Unit){
    Surface(color=soft,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("${album.size} photos · Ready to send",color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                itemsIndexed(album){index,file->Column(horizontalAlignment=Alignment.CenterHorizontally){SeraAttachmentThumbnail(file,accent);IconButton(onClick={onRemove(index)},enabled=!sending,modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.Close,"Remove photo ${index+1}",tint=muted)}}}
            }
        }
    }
}
