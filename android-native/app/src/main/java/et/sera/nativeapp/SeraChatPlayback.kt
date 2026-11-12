package et.sera.nativeapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
internal class SeraChatPlayback(private val context:Context){
    var activeId by mutableStateOf<String?>(null);private set
    var playing by mutableStateOf(false);private set
    var loading by mutableStateOf(false);private set
    var duration by mutableIntStateOf(0);private set
    var position by mutableIntStateOf(0);private set
    private var player:MediaPlayer?=null
    private var temporary:File?=null
    private var generation=0
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focus:AudioFocusRequest?=null
    private fun abandon(){focus?.let{runCatching{manager.abandonAudioFocusRequest(it)}};focus=null}
    private fun start(p:MediaPlayer):Boolean{
        abandon()
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener{if(it<0)pause()}.build()
        if(manager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false
        focus=request
        return runCatching{p.start();playing=true;true}.getOrElse{abandon();false}
    }
    fun pause(){runCatching{player?.pause()};playing=false;abandon()}
    fun stop(){generation++;runCatching{player?.release()};player=null;temporary?.delete();temporary=null;abandon();activeId=null;playing=false;loading=false;position=0;duration=0}
    fun tick(){if(playing)runCatching{position=player?.currentPosition?:position}}
    fun seek(ms:Int){if(duration>0&&!loading)runCatching{player?.seekTo(ms.coerceIn(0,duration));position=ms.coerceIn(0,duration)}}
    suspend fun toggle(id:String,mime:String,load:()->ByteArray,onError:(String)->Unit){
        if(activeId==id){if(loading){stop();return};if(playing){pause();return};val p=player;if(p!=null){if(position>=duration&&duration>0)seek(0);if(!start(p))onError("Audio focus is unavailable.");return}}
        stop();val request=generation;activeId=id;loading=true
        var created:File?=null
        try{
            val extension=when(mime){"audio/mp4"->".m4a";"audio/webm"->".webm";"audio/ogg"->".ogg";else->".mp3"}
            val f=withContext(Dispatchers.IO){val data=load();require(data.isNotEmpty()&&data.size<=5*1024*1024);File.createTempFile("sera_chat_audio_",extension,context.cacheDir).also{created=it;it.writeBytes(data)}}
            if(request!=generation){f.delete();return}
            temporary=f
            val p=MediaPlayer();player=p;p.setAudioAttributes(attributes);p.setDataSource(f.absolutePath)
            p.setOnPreparedListener{if(request==generation){duration=it.duration.coerceAtLeast(0);loading=false;if(!start(it))onError("Audio focus is unavailable.")}}
            p.setOnCompletionListener{if(request==generation){playing=false;position=duration;abandon()}}
            p.setOnErrorListener{_,_,_->if(request==generation){stop();onError("Audio unavailable. Please retry.")};true}
            p.prepareAsync()
        }catch(e:CancellationException){created?.delete();if(request==generation)stop();throw e}
        catch(_:Exception){created?.delete();if(request==generation){stop();onError("Audio unavailable. Please retry.")}}
    }
}
private fun voiceTime(ms:Int):String="%d:%02d".format(ms.coerceAtLeast(0)/60000,(ms.coerceAtLeast(0)/1000)%60)
@Composable
internal fun SeraChatVoicePlayer(playback:SeraChatPlayback,id:String,durationHint:Int?,foreground:Color,label:String="Voice message",onPlay:()->Unit){
    val active=playback.activeId==id
    val duration=if(active&&playback.duration>0)playback.duration else durationHint?:0
    val position=if(active)playback.position else 0
    Row(Modifier.widthIn(min=155.dp,max=260.dp).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
        IconButton(onClick=onPlay,modifier=Modifier.size(48.dp)){
            if(active&&playback.loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=foreground)
            else Icon(if(active&&playback.playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(active&&playback.playing)"Pause voice message" else "Play voice message",tint=foreground)
        }
        Column(Modifier.weight(1f)){
            Text(label,color=foreground,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
            Slider(value=position.toFloat().coerceIn(0f,duration.coerceAtLeast(1).toFloat()),onValueChange={playback.seek(it.toInt())},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=active&&playback.duration>0&&!playback.loading,modifier=Modifier.fillMaxWidth().height(40.dp),colors=SliderDefaults.colors(thumbColor=foreground,activeTrackColor=foreground,inactiveTrackColor=foreground.copy(alpha=.25f),disabledThumbColor=foreground.copy(alpha=.5f),disabledInactiveTrackColor=foreground.copy(alpha=.25f)))
            Text(voiceTime(position)+" / "+voiceTime(duration),color=foreground.copy(alpha=.82f),fontSize=10.sp)
        }
    }
}
