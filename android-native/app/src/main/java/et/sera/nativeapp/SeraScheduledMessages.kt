package et.sera.nativeapp
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class SeraScheduledEntry(val id:String,val body:String,val at:String,val state:String)
private fun scheduledRows(token:String,conversation:String):List<SeraScheduledEntry>{
 val rows=searchRows(token,"sera_scheduled_messages",mapOf("select" to "id,body,send_at,state","conversation_id" to "eq."+conversation,"order" to "send_at.desc","limit" to "100"))
 return (0 until rows.length()).map{val r=rows.getJSONObject(it);SeraScheduledEntry(r.getString("id"),r.getString("body"),r.getString("send_at"),r.getString("state"))}
}
internal fun scheduledTime(date:LocalDate,time:LocalTime,zone:ZoneId):Instant?{
 val local=LocalDateTime.of(date,time);val offsets=zone.rules.getValidOffsets(local)
 return if(offsets.size==1)local.toInstant(offsets.first())else null
}
@Composable internal fun SeraScheduledMessagesScreen(token:String,conversation:String,peer:String,initialBody:String?,onDismiss:()->Unit,onCreated:(String)->Unit){
 var rows by remember(conversation){mutableStateOf<List<SeraScheduledEntry>>(emptyList())}
 var revision by remember{mutableIntStateOf(0)}
 var loading by remember{mutableStateOf(true)}
 var error by remember{mutableStateOf("")}
 var editor by remember{mutableStateOf<SeraScheduledEntry?>(initialBody?.let{SeraScheduledEntry(UUID.randomUUID().toString(),it,"","new")})}
 var cancel by remember{mutableStateOf<SeraScheduledEntry?>(null)}
 var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 LaunchedEffect(token,conversation,revision){loading=true;error="";val r=withContext(Dispatchers.IO){runCatching{scheduledRows(token,conversation)}};r.onSuccess{rows=it}.onFailure{error="Could not load scheduled messages."};loading=false}
 Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface){
   Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Row(Modifier.heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onDismiss,enabled=!busy){Icon(Icons.Outlined.ArrowBack,"Back")};Column(Modifier.weight(1f)){Text("Scheduled messages",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(peer,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Outlined.Refresh,"Refresh")}}
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Schedule,null,tint=MaterialTheme.colorScheme.onPrimaryContainer);Text("Messages are sent by SERA even when your phone is offline. Times use your device time zone.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)}}
    Button(onClick={editor=SeraScheduledEntry(UUID.randomUUID().toString(),"","","new")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("Schedule a message")}
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={revision++}){Text("Retry")}}
    if(!loading&&rows.isEmpty()&&error.isBlank())Text("No scheduled messages yet.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){
     items(rows,key={it.id}){entry->Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Icon(if(entry.state=="sent")Icons.Outlined.Check else Icons.Outlined.Schedule,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(6.dp));Text(entry.state.replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
      Text(entry.body,maxLines=4,overflow=TextOverflow.Ellipsis)
      Text(runCatching{Instant.parse(entry.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm z"))}.getOrDefault(entry.at),style=MaterialTheme.typography.bodySmall)
      if(entry.state=="scheduled")Row(horizontalArrangement=Arrangement.End,modifier=Modifier.fillMaxWidth()){TextButton(enabled=!busy,onClick={editor=entry}){Text("Edit")};TextButton(enabled=!busy,onClick={cancel=entry}){Text("Cancel",color=MaterialTheme.colorScheme.error)}}
     }}}
    }
   }
  }
 }
 editor?.let{entry->key(entry.id){SeraScheduleEditor(token,conversation,entry,onDismiss={editor=null},onSaved={body->editor=null;revision++;if(entry.state=="new")onCreated(body)})}}
 cancel?.let{entry->AlertDialog(onDismissRequest={if(!busy)cancel=null},title={Text("Cancel scheduled message?")},text={Text("The message will not be delivered if cancellation succeeds before sending.")},confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_cancel_scheduled_message",JSONObject().put("p_id",entry.id))};if(r.first?.trim()=="true"){cancel=null;revision++}else{cancel=null;error="The message may have already been sent. Refresh to check."}}finally{busy=false}}}){Text("Cancel message",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(enabled=!busy,onClick={cancel=null}){Text("Keep message")}})}
}
@Composable private fun SeraScheduleEditor(token:String,conversation:String,entry:SeraScheduledEntry,onDismiss:()->Unit,onSaved:(String)->Unit){
 val context=LocalContext.current
 val zone=ZoneId.systemDefault()
 val initial=remember(entry.id){runCatching{Instant.parse(entry.at).atZone(zone)}.getOrElse{ZonedDateTime.now(zone).plusMinutes(5).withSecond(0).withNano(0)}}
 var date by remember{mutableStateOf(initial.toLocalDate())};var time by remember{mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0))}
 var body by remember{mutableStateOf(entry.body)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(entry.state=="new")"Schedule send" else "Edit scheduled message")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  OutlinedTextField(body,{if(it.length<=4000)body=it},label={Text("Message")},minLines=2,maxLines=5,enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
  OutlinedButton(enabled=!busy,onClick={DatePickerDialog(context,{_,y,m,d->date=LocalDate.of(y,m+1,d)},date.year,date.monthValue-1,date.dayOfMonth).apply{datePicker.minDate=System.currentTimeMillis()}.show()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DateRange,null);Spacer(Modifier.width(8.dp));Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))}
  OutlinedButton(enabled=!busy,onClick={TimePickerDialog(context,{_,h,m->time=LocalTime.of(h,m)},time.hour,time.minute,android.text.format.DateFormat.is24HourFormat(context)).show()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Schedule,null);Spacer(Modifier.width(8.dp));Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))+" · "+zone.id)}
  Text("Choose a time 1 minute to 90 days in the future.",style=MaterialTheme.typography.bodySmall)
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 }},confirmButton={Button(enabled=!busy&&body.isNotBlank(),onClick={
  val at=scheduledTime(date,time,zone)
  if(at==null||at.isBefore(Instant.now().plusSeconds(60))||at.isAfter(Instant.now().plusSeconds(90*86400L))){error="Choose a valid future time.";return@Button}
  busy=true;scope.launch{try{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_save_scheduled_message",JSONObject().put("p_id",entry.id).put("p_conversation",conversation).put("p_body",body).put("p_send_at",at.toString()))};if(r.first!=null)onSaved(body)else error=r.second.ifBlank{"Could not schedule. Try again."}}finally{busy=false}}
 }){Text(if(busy)"Saving…" else "Save schedule")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Back")}})
}
