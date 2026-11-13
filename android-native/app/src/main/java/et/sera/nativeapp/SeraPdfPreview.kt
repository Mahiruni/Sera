package et.sera.nativeapp
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

/** Private, temporary PDF rendering: bounded page bitmap, no external URL. */
@Composable internal fun SeraPdfPreview(file:NativeChatSelection,onDismiss:()->Unit){
    val context=LocalContext.current
    var page by remember(file){mutableIntStateOf(0)}
    var count by remember(file){mutableIntStateOf(0)}
    var bitmap by remember(file){mutableStateOf<Bitmap?>(null)}
    var error by remember(file){mutableStateOf("")}
    LaunchedEffect(file,page){
        bitmap=null;error=""
        try{
            val result=withContext(Dispatchers.IO){
                val temp=File.createTempFile("sera-pdf-",".pdf",context.cacheDir)
                try{
                    temp.writeBytes(file.bytes)
                    val descriptor=ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer=try{PdfRenderer(descriptor)}catch(e:Exception){descriptor.close();throw e}
                    renderer.use{r->
                        require(r.pageCount>0)
                        r.openPage(page.coerceIn(0,r.pageCount-1)).use{p->
                            val factor=minOf(1.5f,1600f/maxOf(p.width,p.height).coerceAtLeast(1))
                            val image=Bitmap.createBitmap((p.width*factor).toInt().coerceAtLeast(1),(p.height*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                            image.eraseColor(android.graphics.Color.WHITE)
                            p.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            image to r.pageCount
                        }
                    }
                }finally{temp.delete()}
            }
            bitmap=result.first;count=result.second
        }catch(e:CancellationException){throw e}catch(_:Exception){error="This PDF cannot be previewed. It may be protected or damaged."}
    }
    Dialog(onDismissRequest=onDismiss){
        Surface(shape=MaterialTheme.shapes.large){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(file.name,style=MaterialTheme.typography.titleMedium,maxLines=2)
                Box(Modifier.fillMaxWidth().heightIn(min=160.dp,max=420.dp).verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center){
                    val image=bitmap
                    if(image!=null)Image(image.asImageBitmap(),"PDF page ${page+1}",Modifier.fillMaxWidth())
                    else if(error.isNotBlank())Text(error)else CircularProgressIndicator()
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={page--},enabled=page>0){Text("Previous")}
                    Text(if(count>0)"${page+1} / $count"else "")
                    TextButton(onClick={page++},enabled=page+1<count){Text("Next")}
                }
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)){Text("Done")}
            }
        }
    }
}
