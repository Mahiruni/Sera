package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
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
 val label=when(event.state){"missed"->"Missed voice call";"declined"->"Declined voice call";"cancelled"->"Cancelled voice call";"failed"->"Voice call failed";else->"Voice call ended"}
 val time=runCatching{java.time.Instant.parse(event.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("")
 Box(Modifier.fillMaxWidth().padding(vertical=6.dp),contentAlignment=Alignment.Center){
  Surface(shape=RoundedCornerShape(16.dp),color=surface){
   Row(Modifier.padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Icon(Icons.Outlined.Call,null,tint=accent,modifier=Modifier.size(19.dp))
    Column{Text(label,color=ink,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text((if(event.caller==userId)"Outgoing"else "Incoming")+" · "+time,color=muted,fontSize=10.sp)}
   }
  }
 }
}
