package et.sera.nativeapp
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.delay

@Composable
internal fun SeraChatVoice(enabled:Boolean,onReady:(NativeChatSelection)->Unit,onError:(String)->Unit,onRecordingChange:(Boolean)->Unit={},accent:Color=Color(0xFF08747A)){
    val context=LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableIntStateOf(0) }
    var levels by remember { mutableStateOf(List(18){0f}) }
    fun finish(keep:Boolean){
        val r=recorder?:return
        val f=file
        recorder=null;file=null
        val ok=runCatching{r.stop()}.isSuccess
        runCatching{r.release()}
        if(keep && ok && f!=null){
            val duration=(SystemClock.elapsedRealtime()-started).toInt().coerceIn(1,120000)
            val bytes=runCatching{f.readBytes()}.getOrNull()
            if(bytes!=null && bytes.size in 1..(5*1024*1024))onReady(NativeChatSelection("Voice message.m4a","audio/mp4",bytes,duration)) else onError("Recording could not be saved.")
        }else if(keep)onError("Recording was too short.")
        f?.delete()
    }
    fun start(){
        if(recorder!=null)return
        try{
            val f=File.createTempFile("sera_voice_",".m4a",context.cacheDir);file=f
            val r=if(Build.VERSION.SDK_INT>=31)MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            recorder=r
            r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioEncodingBitRate(64000);r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath);r.setMaxDuration(120000);r.setMaxFileSize(5L*1024*1024)
            r.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)finish(true)}
            r.prepare();r.start();started=SystemClock.elapsedRealtime();seconds=0;levels=List(18){0f}
        }catch(_:Exception){runCatching{recorder?.release()};recorder=null;file?.delete();file=null;onError("Microphone is unavailable.")}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)start() else onError("Microphone permission is required for voice messages.")}
    LaunchedEffect(recorder){onRecordingChange(recorder!=null);while(recorder!=null){seconds=((SystemClock.elapsedRealtime()-started)/1000).toInt().coerceIn(0,120);val level=runCatching{recorder?.maxAmplitude?:0}.getOrDefault(0);levels=levels.drop(1)+seraVoiceLevel(level);delay(100)}}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP&&recorder!=null)finish(false)};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);if(recorder!=null)finish(false)}}
    if(recorder!=null){
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                IconButton(onClick={finish(false)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Close,"Cancel recording")}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        Box(Modifier.size(7.dp).background(Color(0xFFD04452),CircleShape))
                        Text("Recording · %d:%02d".format(seconds/60,seconds%60),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    }
                    SeraLiveWaveform(levels,accent,Modifier.fillMaxWidth().height(24.dp))
                }
                FilledIconButton(onClick={finish(true)},modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color(0xFF08747A),contentColor=Color.White)){Icon(Icons.Outlined.Stop,"Finish recording")}
            }
        }
    }
    else FilledIconButton(colors=IconButtonDefaults.filledIconButtonColors(containerColor=accent,contentColor=Color(0xFF10383B)),enabled=enabled,onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start() else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Mic,"Record voice message",tint=if(androidx.compose.foundation.isSystemInDarkTheme())Color(0xFF10383B)else Color.White)}
}

internal fun seraVoiceLevel(amplitude:Int):Float = kotlin.math.sqrt((amplitude.coerceIn(0,32767)/32767f)).coerceIn(0f,1f)
