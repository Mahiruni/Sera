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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.delay

@Composable
internal fun SeraChatVoice(enabled:Boolean,onReady:(NativeChatSelection)->Unit,onError:(String)->Unit){
    val context=LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableIntStateOf(0) }
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
            r.prepare();r.start();started=SystemClock.elapsedRealtime();seconds=0
        }catch(_:Exception){runCatching{recorder?.release()};recorder=null;file?.delete();file=null;onError("Microphone is unavailable.")}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)start() else onError("Microphone permission is required for voice messages.")}
    LaunchedEffect(recorder){while(recorder!=null){seconds=((SystemClock.elapsedRealtime()-started)/1000).toInt().coerceIn(0,120);delay(250)}}
    DisposableEffect(Unit){onDispose{if(recorder!=null)finish(false)}}
    if(recorder!=null){Row{Text("%d:%02d".format(seconds/60,seconds%60));IconButton(onClick={finish(true)}){Icon(Icons.Outlined.Stop,"Finish recording")};IconButton(onClick={finish(false)}){Icon(Icons.Outlined.Close,"Cancel recording")}}}
    else IconButton(enabled=enabled,onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start() else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Mic,"Record voice message")}
}
