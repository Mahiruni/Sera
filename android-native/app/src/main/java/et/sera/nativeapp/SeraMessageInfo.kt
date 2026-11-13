package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun isUnreadIncoming(m:NativeMessage,user:String)=m.senderId!=user&&!m.unsent&&(m.readAt.isBlank()||m.readAt=="null")

internal fun unreadTimelineIndex(messages:List<NativeMessage>,calls:List<SeraCallEvent>,boundary:String?,limit:Int):Int?{
    if(boundary==null)return null
    val rows=(messages.map{it.createdAt to it.id}+calls.map{it.createdAt to "call-"+it.id}).sortedBy{runCatching{Instant.parse(it.first).toEpochMilli()}.getOrDefault(0)}
    val index=rows.indexOfFirst{it.second==boundary}
    return if(index<0)null else index+if(messages.size>=limit&&limit<1000)1 else 0
}

private fun receiptTime(value:String):String?=runCatching{Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))}.getOrNull()

@Composable internal fun SeraMessageInfo(message:NativeMessage,own:Boolean,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text("Message info")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            InfoLine("Sent",receiptTime(message.createdAt)?:"Unavailable")
            if(own){
                InfoLine("Delivered",receiptTime(message.deliveredAt)?:if(receiptTime(message.readAt)!=null)"Delivered" else "Not confirmed")
                InfoLine("Read",receiptTime(message.readAt)?:"Not confirmed")
            }
            if(message.files.isNotEmpty())InfoLine("Attachments",message.files.size.toString())
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
@Composable private fun InfoLine(label:String,value:String){
    Column(Modifier.fillMaxWidth()){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(value,style=MaterialTheme.typography.bodyMedium)}
}
