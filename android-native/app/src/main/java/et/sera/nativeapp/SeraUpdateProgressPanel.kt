package et.sera.nativeapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

@Composable
internal fun SeraUpdateProgressPanel(highlights:List<String>,version:String,progress:Int,bytes:Long,total:Long,downloaded:Boolean,verifying:Boolean,preparing:Boolean,permission:Boolean,requested:Boolean,onInstall:()->Unit,onPermission:()->Unit,modifier:Modifier=Modifier) {
    val mint=Color(0xFF8EF3E4)
    val animated by animateFloatAsState((progress.coerceIn(0,100))/100f,tween(350),label="Update progress")
    val busy=verifying||preparing
    val title=when{verifying->"Verifying update";preparing->"Preparing installation";permission->"Allow installation";downloaded->"Ready to install";else->"Downloading SERA"}
    val detail=when{
        verifying->"Checking package integrity"
        preparing->"Connecting to Android installer"
        permission->"One-time Android permission required"
        downloaded&&requested->"Approve the Android installation"
        downloaded->"Verified package · v$version"
        total>0->"${"%.1f".format(bytes/1048576.0)} / ${"%.1f".format(total/1048576.0)} MB"
        else->"${"%.1f".format(bytes/1048576.0)} MB downloaded"
    }
    Surface(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Color(0xFF102E35),border=BorderStroke(1.dp,Color(0xFF427975)),shadowElevation=10.dp){
        Row(Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF102E35),Color(0xFF195058)))).padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){
            Box(Modifier.size(58.dp),contentAlignment=Alignment.Center){
                if(busy || (!downloaded&&total<=0)) CircularProgressIndicator(Modifier.fillMaxSize(),color=mint,strokeWidth=4.dp,trackColor=Color.White.copy(alpha=.12f),strokeCap=StrokeCap.Round)
                else CircularProgressIndicator(progress={if(downloaded)1f else animated},modifier=Modifier.fillMaxSize(),color=mint,strokeWidth=4.dp,trackColor=Color.White.copy(alpha=.12f),strokeCap=StrokeCap.Round)
                Text(if(downloaded)"100%" else if(total>0)"${progress}%" else "…",fontSize=12.sp,color=Color.White,fontWeight=FontWeight.Bold)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(title,fontSize=15.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text(detail,fontSize=11.sp,color=Color(0xFFB6D9D8))
                if(!downloaded && !busy && !permission) SeraUpdateHighlights(highlights)
                if(permission||downloaded&&!preparing&&!verifying) Button(onClick=if(permission)onPermission else onInstall,colors=ButtonDefaults.buttonColors(containerColor=mint,contentColor=Color(0xFF10383B)),contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp),modifier=Modifier.heightIn(min=36.dp)){Text(if(permission)"Allow" else "Install update",fontSize=12.sp,fontWeight=FontWeight.Bold)}
            }
            if(!downloaded&&!busy)Text("v$version",fontSize=11.sp,color=mint)
        }
    }
}

@Composable
private fun SeraUpdateHighlights(highlights: List<String>) {
    val phrases = remember(highlights) { highlights.map { it.trim().split(Regex("\\s+")).take(4).joinToString(" ").take(48) }.filter { it.isNotBlank() }.ifEmpty { listOf("Signed SERA update") } }
    var typed by remember(phrases) { mutableStateOf("") }
    LaunchedEffect(phrases) {
        while (true) {
            for (phrase in phrases) {
                for (n in 1..phrase.length) { typed = phrase.take(n); delay(55) }
                delay(1100)
                for (n in phrase.length - 1 downTo 0) { typed = phrase.take(n); delay(32) }
                delay(180)
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(15.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(typed + "▏",fontSize=9.sp,lineHeight=12.sp,color=Color(0xFF8EF3E4),maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
