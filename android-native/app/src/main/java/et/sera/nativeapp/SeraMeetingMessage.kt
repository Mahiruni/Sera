package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*

internal data class SeraMeeting(val title:String,val at:Instant,val minutes:Int)
internal fun parseSeraMeeting(body:String):SeraMeeting?=runCatching{
    val lines=body.split("\n")
    require(lines.size==3&&lines[0].startsWith("Meeting proposal: ")&&lines[1].startsWith("When: ")&&lines[2].startsWith("Duration: "))
    val title=lines[0].removePrefix("Meeting proposal: ").trim()
    val at=Instant.parse(lines[1].removePrefix("When: ").trim())
    val minutes=lines[2].removePrefix("Duration: ").removeSuffix(" minutes").toInt()
    require(title.length in 1..120&&minutes in listOf(15,30,45,60,90,120))
    SeraMeeting(title,at,minutes)
}.getOrNull()
@Composable internal fun SeraMeetingDialog(onDismiss:()->Unit,onAdd:(String)->Unit,initialTitle:String=""){
    var title by remember{mutableStateOf(initialTitle)}
    var date by remember{mutableStateOf(LocalDate.now().plusDays(1).toString())}
    var time by remember{mutableStateOf("10:00")}
    var minutes by remember{mutableIntStateOf(30)}
    var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Propose a meeting")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(title,{title=it.take(120).replace("\n"," ")},label={Text("Meeting title")},singleLine=true)
            OutlinedTextField(date,{date=it.take(10)},label={Text("Date (YYYY-MM-DD)")},singleLine=true)
            OutlinedTextField(time,{time=it.take(5)},label={Text("Time (HH:mm)")},singleLine=true)
            Text("Time zone: "+ZoneId.systemDefault().id,style=MaterialTheme.typography.bodySmall)
            Row{listOf(15,30,60).forEach{n->FilterChip(selected=minutes==n,onClick={minutes=n},label={Text("$n min")},modifier=Modifier.padding(end=4.dp))}}
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick={
        val at=runCatching{LocalDateTime.of(LocalDate.parse(date),LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant()}.getOrNull()
        if(title.isBlank()||at==null||at<=Instant.now()){error="Enter a title and a future date and time.";return@TextButton}
        onAdd("Meeting proposal: "+title.trim()+"\nWhen: "+at+"\nDuration: "+minutes+" minutes")
    }){Text("Add to message")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable internal fun SeraMeetingCard(meeting:SeraMeeting,canReply:Boolean,onReply:()->Unit){
    val local=meeting.at.atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm z"))
    Column(Modifier.fillMaxWidth().padding(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Text("Meeting proposal",style=MaterialTheme.typography.labelMedium)
        Text(meeting.title,style=MaterialTheme.typography.titleMedium)
        Text(local,style=MaterialTheme.typography.bodySmall)
        Text("${meeting.minutes} minutes",style=MaterialTheme.typography.bodySmall)
        if(canReply)TextButton(onClick=onReply,contentPadding=PaddingValues(0.dp)){Text("Respond to proposal")}
    }
}
