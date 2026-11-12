package et.sera.nativeapp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private suspend fun loadPrivateImage(token:String,file:NativeChatFile,maxSide:Int):Bitmap=withContext(Dispatchers.IO){
    val bytes=readChatFile(token,file)
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    require(bounds.outWidth>0&&bounds.outHeight>0)
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSide)sample*=2
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Image unavailable.")
}
@Composable internal fun SeraPrivateImageCard(token:String,file:NativeChatFile,onOpen:()->Unit,foreground:Color){
    var image by remember(token,file.path){mutableStateOf<Bitmap?>(null)}
    var failed by remember(token,file.path){mutableStateOf(false)}
    LaunchedEffect(token,file.path){try{image=loadPrivateImage(token,file,480)}catch(e:CancellationException){throw e}catch(_:Exception){failed=true}}
    Column(Modifier.widthIn(max=220.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick=onOpen)){
        val bitmap=image
        if(bitmap!=null)Image(bitmap.asImageBitmap(),file.name,Modifier.width(210.dp).heightIn(min=90.dp,max=240.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Fit)
        else Box(Modifier.width(190.dp).height(100.dp).background(foreground.copy(alpha=.09f)),contentAlignment=Alignment.Center){if(failed)Icon(Icons.Outlined.Image,"Open image",tint=foreground)else CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp,color=foreground)}
        Text(if(failed)"Open photo" else file.name,fontSize=11.sp,color=foreground,maxLines=1,modifier=Modifier.padding(top=4.dp))
    }
}
@Composable internal fun SeraPrivateImageViewer(token:String,file:NativeChatFile,onDismiss:()->Unit){
    var image by remember(token,file.path){mutableStateOf<Bitmap?>(null)}
    var error by remember{mutableStateOf(false)}
    var scale by remember{mutableFloatStateOf(1f)}
    var panX by remember{mutableFloatStateOf(0f)}
    var panY by remember{mutableFloatStateOf(0f)}
    LaunchedEffect(token,file.path){try{image=loadPrivateImage(token,file,1800)}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Column(Modifier.fillMaxSize().background(Color(0xFF111D21)).systemBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(file.name,color=Color.White,fontSize=14.sp,maxLines=1,modifier=Modifier.weight(1f));IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Close photo",tint=Color.White)}}
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp)),contentAlignment=Alignment.Center){
                val bitmap=image
                if(bitmap!=null)Image(bitmap.asImageBitmap(),file.name,Modifier.fillMaxSize().pointerInput(file.path){detectTransformGestures{_,pan,zoom,_->scale=(scale*zoom).coerceIn(1f,4f);if(scale>1f){panX=(panX+pan.x).coerceIn(-size.width.toFloat(),size.width.toFloat());panY=(panY+pan.y).coerceIn(-size.height.toFloat(),size.height.toFloat())}else{panX=0f;panY=0f}}}.graphicsLayer(scaleX=scale,scaleY=scale,translationX=panX,translationY=panY),contentScale=ContentScale.Fit)
                else if(error)Text("Photo unavailable or access expired.",color=Color.White)else CircularProgressIndicator(color=Color(0xFF6DE0D8))
            }
            Text("Pinch to zoom",color=Color(0xFFA8C3C5),fontSize=12.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }
    }
}
