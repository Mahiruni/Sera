package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
internal data class SeraTimelineItem(val at:String,val message:NativeMessage?,val event:SeraCallEvent?)
@Composable internal fun SeraCallTimelineRow(event:SeraCallEvent,userId:String,surface:Color,ink:Color,muted:Color,accent:Color){
 val label=event.outcome(userId)
 val incoming=event.incoming(userId)
 val icon=if(event.state=="missed"&&incoming)Icons.Outlined.CallMissed else if(incoming)Icons.Outlined.CallReceived else Icons.Outlined.CallMade
 val time=runCatching{java.time.Instant.parse(event.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("")
 Box(Modifier.fillMaxWidth().padding(vertical=6.dp),contentAlignment=Alignment.Center){
  Surface(shape=RoundedCornerShape(16.dp),color=surface){
   Row(Modifier.padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Icon(icon,null,tint=if(event.state=="missed"&&incoming)MaterialTheme.colorScheme.error else accent,modifier=Modifier.size(19.dp))
    Column{Text(voiceText(label),color=ink,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text(voiceText(if(event.caller==userId)"Outgoing"else "Incoming")+" · "+time+(event.connectedSeconds?.let{" · "+seraCallDuration(it)}?:""),color=muted,fontSize=10.sp)}
   }
  }
 }
}
