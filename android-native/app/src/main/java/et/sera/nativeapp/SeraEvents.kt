package et.sera.nativeapp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
private fun eventTime(raw:String):String=runCatching{java.time.OffsetDateTime.parse(raw).atZoneSameInstant(java.time.ZoneId.of("Africa/Addis_Ababa")).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm"))}.getOrDefault(raw)
@Composable internal fun SeraEvents(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var events by remember{mutableStateOf<List<JSONObject>>(emptyList())};var mine by remember{mutableStateOf(false)};var editor by remember{mutableStateOf(false)};var title by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var date by remember{mutableStateOf("")};var time by remember{mutableStateOf("")};var capacity by remember{mutableStateOf("50")};var selected by remember{mutableStateOf<String?>(null)};var cancel by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var reload by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 fun act(rpc:String,p:JSONObject,done:()->Unit={}){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,rpc,p)};busy=false;error=r.second;if(r.first!=null){done();reload++}}}
 fun back(){if(selected!=null)selected=null else onBack()}
 androidx.activity.compose.BackHandler{back()}
 LaunchedEffect(token,mine,reload){loading=true;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_events_list",JSONObject().put("p_mine",mine))};events=spaceRows(r.first);error=r.second;loading=false}
 val detail=events.firstOrNull{it.optString("id")==selected}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 TextButton(onClick={back()}){Text("Back")}
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF123A40),Color(0xFF087E83)))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(detail?.optString("title")?:"Professional events",color=Color.White,style=MaterialTheme.typography.headlineSmall);Text(detail?.let{eventTime(it.optString("starts_at"))}?: "Meet, learn and build your network.",color=Color(0xFFCBEBE7),style=MaterialTheme.typography.bodyMedium)}
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(detail==null){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!mine,onClick={mine=false;selected=null},label={Text("Discover")});FilterChip(selected=mine,onClick={mine=true;selected=null},label={Text("My events")})};Button(onClick={editor=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("Create event")}
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){items(events,key={it.getString("id")}){e->ElevatedCard(onClick={selected=e.getString("id")},shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().animateContentSize()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(eventTime(e.optString("starts_at")),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge);Text(e.optString("title"),style=MaterialTheme.typography.titleLarge);Text(e.optString("location"),style=MaterialTheme.typography.bodyMedium);Text(if(e.optBoolean("cancelled"))"Cancelled" else "${e.optInt("attendees")} / ${e.optInt("capacity")} attending",style=MaterialTheme.typography.labelMedium)}}};if(events.isEmpty()&&!loading)item{Text("No events yet. Create one or check back soon.")}}
 }else{LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Hosted by "+detail.optString("host_name"),style=MaterialTheme.typography.titleMedium);Text(detail.optString("location"));Text(detail.optString("description"));Text("${detail.optInt("attendees")} of ${detail.optInt("capacity")} attending");val going=detail.optBoolean("going");val cancelled=detail.optBoolean("cancelled");if(cancelled)Text("Event cancelled",color=MaterialTheme.colorScheme.error);if(!cancelled||going)Button(onClick={act("sera_event_rsvp",JSONObject().put("p_event",detail.getString("id")).put("p_going",!going))},enabled=!busy&&(going||(!cancelled&&detail.optInt("attendees")<detail.optInt("capacity"))),modifier=Modifier.fillMaxWidth()){Text(if(going)"Cancel RSVP" else "Attend event")};if(detail.optString("owner_id")==userId&&!cancelled)TextButton(onClick={cancel=detail.getString("id")}){Text("Cancel event")}}}}
 }
 cancel?.let{id->AlertDialog(onDismissRequest={cancel=null},title={Text("Cancel event?")},text={Text("This event will no longer accept attendance.")},confirmButton={TextButton(onClick={cancel=null;act("sera_cancel_event",JSONObject().put("p_event",id)){selected=null;mine=true}}){Text("Cancel event")}},dismissButton={TextButton(onClick={cancel=null}){Text("Keep event")}})}
 if(editor)SeraSignatureSheet(onDismissRequest={if(!busy)editor=false}){Column(Modifier.fillMaxWidth().imePadding().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Create event",style=MaterialTheme.typography.headlineSmall);OutlinedTextField(title,{title=it.take(120)},label={Text("Event title")},modifier=Modifier.fillMaxWidth());OutlinedTextField(desc,{desc=it.take(2000)},label={Text("Description")},modifier=Modifier.fillMaxWidth(),maxLines=3);OutlinedTextField(location,{location=it.take(180)},label={Text("Venue or online location")},modifier=Modifier.fillMaxWidth());OutlinedTextField(date,{date=it.take(10)},label={Text("Date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(time,{time=it.take(5)},label={Text("Time (HH:mm, Addis Ababa)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(capacity,{capacity=it.filter{c->c.isDigit()}.take(3)},label={Text("Capacity (1–500)")},modifier=Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={val start=runCatching{java.time.LocalDateTime.parse(date+"T"+time).atZone(java.time.ZoneId.of("Africa/Addis_Ababa")).toInstant().toString()}.getOrNull();if(start==null)error="Enter a valid date and time." else act("sera_create_event",JSONObject().put("p_title",title).put("p_description",desc).put("p_location",location).put("p_starts",start).put("p_capacity",capacity.toIntOrNull()?:0)){editor=false;title="";desc="";location="";date="";time="";mine=true}},enabled=!busy&&title.trim().length>=3&&location.trim().length>=2&&(capacity.toIntOrNull()?:0) in 1..500,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Saving…" else "Publish event")}}}
}
