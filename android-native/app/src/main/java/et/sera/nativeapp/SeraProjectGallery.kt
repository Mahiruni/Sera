package et.sera.nativeapp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.UUID

private fun galleryCall(token:String,path:String,method:String="GET",body:ByteArray?=null,mime:String="application/json"):ByteArray {
 val c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+path).openConnection() as HttpURLConnection
 try {
 c.requestMethod=method;c.connectTimeout=12000;c.readTimeout=20000;c.instanceFollowRedirects=false
 c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token")
 if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type",mime);c.setFixedLengthStreamingMode(body.size);c.outputStream.use{it.write(body)}}
 check(c.responseCode in 200..299){"Gallery request failed"}
 return c.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;out.write(b,0,n);check(out.size()<=5242880){"Image too large"}};out.toByteArray()}
 }finally{c.disconnect()}
}
private fun galleryList(token:String,item:String):List<String>{
 val a=JSONArray(String(galleryCall(token,"/rest/v1/rpc/sera_list_gallery","POST",JSONObject().put("p_item",item).toString().toByteArray())))
 return (0 until a.length()).map{a.getJSONObject(it).getString("path")}.filter{it.matches(Regex("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}[.](jpg|png|webp)"))}
}
private fun galleryBitmap(token:String,path:String):Bitmap?{
 val b=galleryCall(token,"/storage/v1/object/authenticated/sera-portfolio-images/"+path)
 val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(b,0,b.size,bounds)
 var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2
 return BitmapFactory.decodeByteArray(b,0,b.size,BitmapFactory.Options().apply{inSampleSize=sample})
}
@Composable private fun GalleryImage(token:String,path:String,modifier:Modifier,scale:ContentScale){
 var image by remember(token,path){mutableStateOf<Bitmap?>(null)};var failed by remember(token,path){mutableStateOf(false)}
 LaunchedEffect(token,path){image=withContext(Dispatchers.IO){runCatching{galleryBitmap(token,path)}.getOrNull()};failed=image==null}
 val bmp=image
 if(bmp!=null)Image(bmp.asImageBitmap(),"Project image",modifier,contentScale=scale)
 else Box(modifier,contentAlignment=androidx.compose.ui.Alignment.Center){if(failed)Text("Image unavailable")else CircularProgressIndicator(Modifier.size(24.dp))}
}
@Composable internal fun SeraProjectGallery(token:String,item:String,owner:String?=null){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var paths by remember(token,item){mutableStateOf<List<String>>(emptyList())};var reload by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var preview by remember{mutableStateOf<String?>(null)};var remove by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(token,item,reload){val r=withContext(Dispatchers.IO){runCatching{galleryList(token,item)}};r.onSuccess{paths=it;error=""}.onFailure{paths=emptyList();error="Gallery unavailable. Try again."}}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null&&owner!=null){busy=true;scope.launch{val r=withContext(Dispatchers.IO){runCatching{
 val f=selectChatFile(context,uri);require(f.mime in setOf("image/jpeg","image/png","image/webp"));val ext=when(f.mime){"image/png"->"png";"image/webp"->"webp";else->"jpg"}
 galleryCall(token,"/storage/v1/object/sera-portfolio-images/$owner/$item/${UUID.randomUUID()}.$ext","POST",f.bytes,f.mime)
 }};busy=false;if(r.isSuccess)reload++ else error="Image upload failed. Choose a valid image under 5 MB."}}}
 Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
 if(paths.isNotEmpty()){Text("Project gallery",style=MaterialTheme.typography.titleSmall);LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(paths,key={it}){path->Column{GalleryImage(token,path,Modifier.size(116.dp).clickable{preview=path},ContentScale.Crop);if(owner!=null)TextButton(onClick={remove=path},enabled=!busy){Text("Remove")}}}}}
 if(owner!=null)TextButton(onClick={picker.launch(arrayOf("image/jpeg","image/png","image/webp"))},enabled=!busy){Text(if(busy)"Uploading…" else "Add project image")}
 if(error.isNotBlank()){Text(error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error);TextButton(onClick={reload++}){Text("Retry")}}
 }
 preview?.let{path->Dialog(onDismissRequest={preview=null},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){Column{TextButton(onClick={preview=null}){Text("Close image")};GalleryImage(token,path,Modifier.weight(1f).fillMaxWidth(),ContentScale.Fit)}}}}
 remove?.let{path->AlertDialog(onDismissRequest={remove=null},title={Text("Remove image?")},text={Text("This image will be removed from the showcase.")},confirmButton={TextButton(onClick={remove=null;busy=true;scope.launch{val r=withContext(Dispatchers.IO){runCatching{galleryCall(token,"/storage/v1/object/sera-portfolio-images/"+path,"DELETE")}};busy=false;if(r.isSuccess)reload++ else error="Unable to remove image."}}){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
}
