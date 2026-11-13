package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TextSendStage { Saving, Ready, Failed, StorageFailed }
internal data class NativeQueuedText(val id:String,val body:String,val replyId:String?,val createdAt:String,val stage:TextSendStage){
    fun acknowledged(serverId:String,userId:String)=NativeMessage(serverId,userId,body,"",createdAt,"",emptyList(),replyId.orEmpty(),clientId=id)
}
internal fun messageTimelineKey(m:NativeMessage):String = if(m.clientId.isNotBlank()&&m.clientId!="null") "client-"+m.senderId+"-"+m.clientId else m.id
internal fun mergeAcknowledgedText(server:List<NativeMessage>,acknowledged:List<NativeMessage>):List<NativeMessage>{
    val ids=server.map{it.id}.toSet()
    val clients=server.map{it.senderId to it.clientId}.toSet()
    return server+acknowledged.filter{it.id !in ids&&(it.senderId to it.clientId) !in clients}
}

@Composable
internal fun SeraPendingTextBubble(p:NativeQueuedText,online:Boolean,outgoing:Color,maxWidth:Dp,onRetry:()->Unit){
    val failed=p.stage==TextSendStage.Failed||p.stage==TextSendStage.StorageFailed
    val status=when{failed->"Not sent";!online->"Waiting for connection";else->"Sending"}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
        SeraCompactMessageBubble(true,outgoing,Modifier.widthIn(max=maxWidth)){
            Text(p.body,color=Color.White,fontSize=14.sp,lineHeight=20.sp)
            Row(Modifier.align(Alignment.End),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                val time=runCatching{java.time.Instant.parse(p.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("")
                Text(time,color=Color.White.copy(alpha=.8f),fontSize=10.sp)
                if(failed){
                    Icon(Icons.Outlined.ErrorOutline,status,tint=Color(0xFFFFC3C3),modifier=Modifier.size(15.dp))
                    TextButton(onClick=onRetry,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Text("Retry",color=Color.White,fontSize=12.sp)}
                }else Icon(Icons.Outlined.AccessTime,status,tint=Color.White.copy(alpha=.8f),modifier=Modifier.size(15.dp))
            }
        }
    }
}
