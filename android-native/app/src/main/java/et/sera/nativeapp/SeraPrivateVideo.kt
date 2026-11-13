package et.sera.nativeapp

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun SeraVideoAttachment(token:String,file:NativeChatFile,color:Color,onAudioStart:()->Unit){
    var open by remember(file.path){mutableStateOf(false)}
    Column(Modifier.widthIn(max=220.dp)){
        TextButton(onClick={onAudioStart();open=true},colors=ButtonDefaults.textButtonColors(contentColor=color)){Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(6.dp));Text(file.name,maxLines=2)}
        Text("MP4 · ${file.bytes/1024} KB",fontSize=11.sp,color=color)
    }
    if(open)SeraPrivateVideoDialog(file.name,{readChatFile(token,file)}){open=false}
}
@Composable internal fun SeraVideoDraftPreview(file:NativeChatSelection,color:Color,onAudioStart:()->Unit){
    var open by remember(file){mutableStateOf(false)}
    var thumbnail by remember(file){mutableStateOf<android.graphics.Bitmap?>(null)}
    var duration by remember(file){mutableStateOf<String?>(null)}
    val context=LocalContext.current
    LaunchedEffect(file){
        val result=withContext(Dispatchers.IO){
            val tmp=File.createTempFile("sera-video-preview-",".mp4",context.cacheDir)
            try{
                tmp.writeBytes(file.bytes);val retriever=MediaMetadataRetriever()
                try{retriever.setDataSource(tmp.absolutePath);val ms=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull();val frame=if(Build.VERSION.SDK_INT>=27)retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,160,120)else null;frame to ms}
                finally{retriever.release()}
            }catch(e:CancellationException){throw e}catch(_:Exception){null to null}finally{tmp.delete()}
        }
        thumbnail=result.first;duration=result.second?.let{ms->"%d:%02d".format(ms/60000,(ms/1000)%60)}
    }
    TextButton(onClick={onAudioStart();open=true},colors=ButtonDefaults.textButtonColors(contentColor=color)){
        thumbnail?.let{Image(it.asImageBitmap(),"Video thumbnail",Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))}?:Icon(Icons.Outlined.PlayArrow,"Preview video")
        Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(file.name,maxLines=1);Text("MP4 · "+(duration?:"Preview")+" · ${file.bytes.size/1024} KB",fontSize=11.sp)}
    }
    if(open)SeraPrivateVideoDialog(file.name,{file.bytes}){open=false}
}
@Composable private fun SeraPrivateVideoDialog(name:String,load:()->ByteArray,onDismiss:()->Unit){
    val context=LocalContext.current
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var path by remember{mutableStateOf<File?>(null)}
    var error by remember{mutableStateOf(false)}
    var player by remember{mutableStateOf<VideoView?>(null)}
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit){
        var temp:File?=null
        try{
            withContext(Dispatchers.IO){val bytes=load();require(bytes.size in 1..5*1024*1024);temp=File.createTempFile("sera-private-video-",".mp4",context.cacheDir);temp!!.writeBytes(bytes)}
            path=temp
        }catch(e:CancellationException){temp?.delete();throw e}catch(_:Exception){temp?.delete();error=true}
    }
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)latestDismiss()}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);player?.stopPlayback();path?.delete()}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Column(Modifier.fillMaxSize().background(Color(0xFF111D21)).systemBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(name,color=Color.White,modifier=Modifier.weight(1f),maxLines=1);IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Close video",tint=Color.White)}}
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
                val file=path
                if(error)Text("Video unavailable or unsupported.",color=Color.White)
                else if(file==null)CircularProgressIndicator(color=Color(0xFF6DE0D8))
                else AndroidView(factory={ctx->VideoView(ctx).apply{player=this;setMediaController(MediaController(ctx).also{it.setAnchorView(this)});setVideoURI(Uri.fromFile(file));setOnPreparedListener{seekTo(1)};setOnErrorListener{_,_,_->error=true;true}}},modifier=Modifier.fillMaxWidth().heightIn(max=460.dp))
            }
            Text("Tap the video to reveal playback controls",color=Color(0xFFA8C3C5),fontSize=12.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }
    }
}
