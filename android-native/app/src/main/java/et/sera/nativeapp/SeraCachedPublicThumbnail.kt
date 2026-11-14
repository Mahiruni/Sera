package et.sera.nativeapp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
/** Public originals only, following a live feed authorization. Never persists signed URLs. */
@Composable internal fun SeraCachedPublicThumbnail(owner:String,token:String,postId:String,previewUrl:String,version:String,originalUrl:String,modifier:Modifier){
 val context=LocalContext.current.applicationContext
 var bitmap by remember(owner,token,postId,previewUrl,version){mutableStateOf<Bitmap?>(null)}
 var fallback by remember(owner,token,postId,previewUrl,version){mutableStateOf(false)}
 var denied by remember(owner,token,postId,previewUrl,version){mutableStateOf(false)}
 LaunchedEffect(owner,token,postId,previewUrl,version){
  var rejected=false
  val result=withContext(Dispatchers.IO){
   runCatching{
    if(!Regex("https://sera[.]hisabtech[.]com/api/post-media/[0-9a-fA-F-]{36}").matches(previewUrl))return@runCatching null
    val store=SeraLocalCache.get(context)
    val item=postId+"|"+previewUrl.substringAfterLast('/')+"|"+version
    val generation=store.generation(owner)
    val cached=store.read(owner,"thumbnail",item)?.let{Base64.decode(it,Base64.NO_WRAP)}
    if(cached!=null)BitmapFactory.decodeByteArray(cached,0,cached.size)?.let{return@runCatching it}
    val request=Request.Builder().url(previewUrl+"?w=480").header("Authorization","Bearer "+token).header("Accept","image/*").build()
    SeraHttp.client.newCall(request).execute().use{r->
     if(r.code in listOf(401,403,404)){rejected=true;store.remove(owner,"thumbnail",item);return@use null}
     if(!r.isSuccessful||r.body==null)return@use null
     val body=r.body!!
     if(body.contentLength()>400000)return@use null
     val buffer=java.io.ByteArrayOutputStream()
     val chunk=ByteArray(8192)
     body.byteStream().use{input->while(true){val n=input.read(chunk);if(n<0)break;if(buffer.size()+n>400000){buffer.reset();break};buffer.write(chunk,0,n)}}
     val bytes=buffer.toByteArray()
     val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
     BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
     if(opts.outWidth !in 1..480||opts.outHeight !in 1..480)return@use null
     val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return@use null
     runCatching{store.write(owner,"thumbnail",item,Base64.encodeToString(bytes,Base64.NO_WRAP),System.currentTimeMillis(),300000L,60,generation)}
     decoded
    }
   }.getOrNull()
  }
  bitmap=result;denied=rejected;fallback=result==null&&!rejected
 }
 val image=bitmap
 if(image!=null)Image(image.asImageBitmap(),contentDescription=stringResource(R.string.post_image),modifier=modifier,contentScale=ContentScale.Crop)
 else if(fallback)SeraMediaImage(originalUrl,stringResource(R.string.post_image),1080,720,modifier,accessToken=token,previewUrl=previewUrl)
 else if(!denied)Box(modifier.heightIn(min=160.dp))
}
