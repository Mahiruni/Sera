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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
internal fun seraThumbnailMediaId(url:String?):String=if(url!=null&&Regex("https://sera[.]hisabtech[.]com/api/post-media/[0-9a-fA-F-]{36}").matches(url))url.substringAfterLast('/') else ""
internal fun seraThumbnailKey(postId:String,mediaId:String,version:String)="$postId|$mediaId|$version"
private val thumbnailDownloads=Semaphore(3)

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
    val mediaId=seraThumbnailMediaId(previewUrl)
    if(mediaId.isBlank())return@runCatching null
    val store=SeraLocalCache.get(context)
    val item=seraThumbnailKey(postId,mediaId,version)
    val generation=store.generation(owner)
    val cached=store.read(owner,"thumbnail",item)?.let{Base64.decode(it,Base64.NO_WRAP)}
    if(cached!=null)BitmapFactory.decodeByteArray(cached,0,cached.size)?.let{return@runCatching it}
    thumbnailDownloads.withPermit{
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
     runCatching{store.write(owner,"thumbnail",item,Base64.encodeToString(bytes,Base64.NO_WRAP),System.currentTimeMillis(),300000L,store.thumbnailLimit(),generation)}
     decoded
    }
    }
   }.getOrNull()
  }
  bitmap=result;denied=rejected;fallback=result==null&&!rejected&&withContext(Dispatchers.IO){
   val cm=context.getSystemService(android.net.ConnectivityManager::class.java)
   runCatching{val caps=cm.getNetworkCapabilities(cm.activeNetwork);caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true&&!cm.isActiveNetworkMetered&&SeraLocalCache.get(context).budgetMb()!=15}.getOrDefault(false)
  }
 }
 val image=bitmap
 if(image!=null)Image(image.asImageBitmap(),contentDescription=stringResource(R.string.post_image),modifier=modifier,contentScale=ContentScale.Crop)
 else if(fallback)SeraMediaImage(originalUrl,stringResource(R.string.post_image),1080,720,modifier,accessToken=token,previewUrl=previewUrl)
 else if(!denied)Box(modifier.heightIn(min=160.dp),contentAlignment=androidx.compose.ui.Alignment.Center){Text("Image preview · tap to view",style=MaterialTheme.typography.bodySmall)}
}

/** Cache-only preview. No network request or signed URL is used from saved cards. */
@Composable internal fun SeraSavedPostThumbnail(owner:String,post:SeraFeedPreview){
 if(owner.isBlank()||post.mediaId.isBlank())return
 val context=LocalContext.current.applicationContext
 val image by produceState<Bitmap?>(null,owner,post.id,post.mediaId,post.mediaVersion){
  value=withContext(Dispatchers.IO){runCatching{
   val raw=SeraLocalCache.get(context).read(owner,"thumbnail",seraThumbnailKey(post.id,post.mediaId,post.mediaVersion))?:return@runCatching null
   val bytes=Base64.decode(raw,Base64.NO_WRAP)
   BitmapFactory.decodeByteArray(bytes,0,bytes.size)
  }.getOrNull()}
 }
 image?.let{Image(it.asImageBitmap(),contentDescription=stringResource(R.string.post_image),modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=280.dp),contentScale=ContentScale.Crop)}
}
