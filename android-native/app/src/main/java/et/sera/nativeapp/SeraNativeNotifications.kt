package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun notificationCall(token:String,path:String,patch:Boolean=false):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{
  c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection
  c.connectTimeout=10000;c.readTimeout=15000
  c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer "+token)
  if(patch){c.requestMethod="PATCH";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Prefer","return=minimal");c.outputStream.use{it.write(JSONObject().put("read_at",Instant.now().toString()).toString().toByteArray())}}
  val status=c.responseCode
  if(status in 200..299)c.inputStream.bufferedReader().use{it.readText()} to "" else null to if(status==401)"Your session expired. Sign in again." else "Notifications unavailable (HTTP "+status+"). Please retry."
 }catch(_:Exception){null to "Connection unavailable. Please retry."}finally{c?.disconnect()}
}
private data class NotificationPerson(val name:String,val avatar:String?)
private data class NotificationBatch(val rows:List<JSONObject>?,val people:Map<String,NotificationPerson>,val jobs:Map<String,JSONObject>,val enabled:Boolean,val error:String)
private val notificationTabs=listOf("All","Unread","Social","Network","Opportunities","Collaboration","Calls")
private val notificationKinds=mapOf("Social" to listOf("like","comment","share","repost","reply"),"Network" to listOf("follow","connection_request","connection_accepted"),"Opportunities" to listOf("job_match","application_update","opportunity_match"),"Collaboration" to listOf("space_invitation","community_invitation","project_invitation","space_message"),"Calls" to listOf("missed_call"))
private fun JSONObject.field(k:String)=optString(k).takeUnless{it=="null"}.orEmpty()
private fun loadNotifications(token:String,userId:String,tab:String,page:Int):NotificationBatch{
 val filter=if(tab=="Unread")"&read_at=is.null" else notificationKinds[tab]?.let{"&kind=in.("+it.joinToString(",")+")"}.orEmpty()
 val path="professional_notifications?select=id,actor_id,kind,post_id,job_id,match_reasons,created_at,read_at,space_id,conversation_id&recipient_id=eq."+userId+filter+"&order=created_at.desc,id.desc&limit=31&offset="+((page-1)*30)
 val r=notificationCall(token,path)
 if(r.first==null)return NotificationBatch(null,emptyMap(),emptyMap(),false,r.second)
 val rows=runCatching{val a=JSONArray(r.first);(0 until a.length()).map{a.getJSONObject(it)}}.getOrElse{return NotificationBatch(null,emptyMap(),emptyMap(),false,"Unable to read notifications.")}
 val ids=rows.filter{it.field("kind")!="job_match"}.map{it.field("actor_id")}.filter{it.isNotBlank()}.distinct()
 val people=if(ids.isEmpty())emptyMap() else spaceRows(spaceCall(token,"member_cards",JSONObject().put("ids",JSONArray(ids))).first).associate{card->
  val path=card.field("avatar_path").takeIf{it.isNotBlank()}
  card.field("id") to NotificationPerson(card.field("full_name").ifBlank{"Sera member"},path?.let{signedPostImage(token,it,"sera-account-private")})
 }
 val jobIds=rows.filter{it.field("kind")=="job_match"}.map{it.field("job_id")}.filter{it.isNotBlank()}.distinct()
 val jobs=if(jobIds.isEmpty())emptyMap() else spaceRows(notificationCall(token,"jobs?select=id,title,company,location,work_type&status=eq.open&id=in.("+jobIds.joinToString(",")+")").first).associateBy{it.field("id")}
 val pref=if(tab=="Opportunities")notificationCall(token,"sera_job_alert_preferences?select=enabled&user_id=eq."+userId+"&limit=1") else null
 return NotificationBatch(rows,people,jobs,spaceRows(pref?.first).firstOrNull()?.optBoolean("enabled")==true,pref?.second.orEmpty())
}
private data class NativeNotificationGroup(val latest:JSONObject,val events:MutableList<JSONObject>,val actors:MutableList<String>)
private fun notificationDay(r:JSONObject)=runCatching{Instant.parse(r.field("created_at")).atZone(ZoneId.of("Africa/Addis_Ababa")).toLocalDate()}.getOrNull()
private fun groupNativeNotifications(rows:List<JSONObject>):List<NativeNotificationGroup>{
 val groups=mutableListOf<NativeNotificationGroup>();val seen=mutableSetOf<String>()
 rows.forEach{r->
  if(!seen.add(r.field("id")))return@forEach
  val g=groups.lastOrNull();val p=g?.latest
  val span=runCatching{java.time.Duration.between(Instant.parse(r.field("created_at")),Instant.parse(p?.field("created_at").orEmpty())).seconds}.getOrDefault(-1)
  if(g!=null&&p!=null&&r.field("kind")==p.field("kind")&&((r.field("kind") in listOf("like","comment","share","repost")&&r.field("post_id").isNotBlank()&&r.field("post_id")==p.field("post_id"))||(r.field("kind")=="space_message"&&r.field("space_id").isNotBlank()&&r.field("space_id")==p.field("space_id")))&&span in 0..21600&&notificationDay(r)==notificationDay(p)){
   g.events.add(r);if(r.field("actor_id") !in g.actors)g.actors.add(r.field("actor_id"))
  }else groups.add(NativeNotificationGroup(r,mutableListOf(r),mutableListOf(r.field("actor_id"))))
 }
 return groups
}
private fun dateGroup(r:JSONObject):String{val now=java.time.LocalDate.now(ZoneId.of("Africa/Addis_Ababa"));return when(notificationDay(r)){now->"Today";now.minusDays(1)->"Yesterday";else->"Earlier"}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SeraNativeNotifications(token:String,userId:String,modifier:Modifier=Modifier,onOpenConversation:(String)->Unit={}){
 var tab by remember(token,userId){mutableStateOf("All")};var page by remember(token,userId){mutableIntStateOf(1)}
 var rows by remember(token,userId){mutableStateOf<List<JSONObject>>(emptyList())};var people by remember(token,userId){mutableStateOf<Map<String,NotificationPerson>>(emptyMap())};var jobs by remember(token,userId){mutableStateOf<Map<String,JSONObject>>(emptyMap())}
 var enabled by remember(token,userId){mutableStateOf(false)};var error by remember(token,userId){mutableStateOf("")};var loading by remember(token,userId){mutableStateOf(true)};var refresh by remember(token,userId){mutableIntStateOf(0)}
 var selectedPost by remember(token,userId){mutableStateOf<String?>(null)};var selectedSpace by remember(token,userId){mutableStateOf<String?>(null)};var selectedJob by remember(token,userId){mutableStateOf<SearchHit?>(null)};var marking by remember(token,userId){mutableStateOf(false)};var sounds by remember{mutableStateOf(false)}
 val context=androidx.compose.ui.platform.LocalContext.current
 var savedActivity by remember(token,userId){mutableStateOf(false)}
 var liveActivity by remember(token,userId){mutableStateOf(false)}
 val scope=rememberCoroutineScope();val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle;val teal=Color(0xFF087E83)
 fun mark(ids:List<String>?){
  if(marking||!liveActivity)return;marking=true
  scope.launch{val r=withContext(Dispatchers.IO){notificationCall(token,"professional_notifications?recipient_id=eq."+userId+"&read_at=is.null"+(ids?.let{"&id=in.("+it.joinToString(",")+")"}.orEmpty()),true)};marking=false;error=r.second;if(r.first!=null){withContext(Dispatchers.IO){SeraDirectoryCache(context,userId).clear("notifications")};refresh++;SeraUnreadRefresh.changed()}}
 }
 fun preference(value:Boolean){marking=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,"sera_set_job_alert_preference",JSONObject().put("p_enabled",value))};marking=false;error=r.second;if(r.first!=null){enabled=value;refresh++}}}
 LaunchedEffect(token,userId,tab,page,refresh,lifecycle){
  liveActivity=false;savedActivity=false;loading=true
  if(token.isBlank()||userId.isBlank()){rows=emptyList();people=emptyMap();jobs=emptyMap();loading=false;error="Sign in to view notifications.";return@LaunchedEffect}
  val cache=SeraDirectoryCache(context,userId)
  val cached=withContext(Dispatchers.IO){cache.readNotifications(tab,page)}
  if(cached!=null){
   rows=spaceRows(cached.optJSONArray("rows")?.toString())
   val names=cached.optJSONObject("names")
   people=names?.let{obj->obj.keys().asSequence().associateWith{NotificationPerson(obj.optString(it),null)}}?:emptyMap()
   jobs=spaceRows(cached.optJSONArray("jobs")?.toString()).associateBy{it.field("id")}
   enabled=cached.optBoolean("enabled");savedActivity=true
  }
  lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){
   val generation=cache.generation()
   val result=withContext(Dispatchers.IO){loadNotifications(token,userId,tab,page)}
   if(result.rows!=null){
    rows=result.rows;people=result.people;jobs=result.jobs;enabled=result.enabled;liveActivity=true;savedActivity=false
    withContext(Dispatchers.IO){cache.writeNotifications(tab,page,result.rows,result.people.mapValues{it.value.name},result.jobs,result.enabled,generation)}
   }else{
    liveActivity=false;savedActivity=rows.isNotEmpty()
    if(seraCacheDenied(result.error)){rows=emptyList();people=emptyMap();jobs=emptyMap();savedActivity=false;selectedPost=null;selectedJob=null;selectedSpace=null;withContext(Dispatchers.IO){cache.clear("notifications")}}
   }
   error=result.error;loading=false;delay(30000)
  }}
 }
 val unread=rememberSeraUnreadActivity(token,userId,"Notifications").first
 selectedSpace?.let{id->SeraCollaboration(token,userId,modifier,initialSpace=id,initialChat=true){selectedSpace=null;refresh++};return}
 if(sounds){SeraSoundSettings(modifier,userId){sounds=false};return}
 selectedJob?.let{hit->SearchResultDetail(token,userId,hit,modifier){selectedJob=null;refresh++};return}
 val groups=groupNativeNotifications(rows.take(30))
 Column(modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){Text("Notifications",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(unread>0)"$unread unread" else "Your professional activity",style=MaterialTheme.typography.labelMedium,color=teal)}
   TextButton(onClick={sounds=true}){Text("Sounds")};TextButton(onClick={mark(null)},enabled=unread>0&&!marking&&liveActivity){Text("Read all")}
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){notificationTabs.forEach{t->FilterChip(selected=tab==t,onClick={if(tab!=t){tab=t;page=1;rows=emptyList();liveActivity=false;loading=true}},label={Text(t)})}}
  if(savedActivity)Text("Saved activity · checking for updates",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
   if(tab=="Opportunities")item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Career alerts",style=MaterialTheme.typography.titleMedium);Row(verticalAlignment=Alignment.CenterVertically){Text("Automatic job matches",Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={preference(it)},enabled=!marking&&!loading&&liveActivity)};Text("Up to 3 relevant opportunities per day.",style=MaterialTheme.typography.bodySmall);TextButton(enabled=!marking,onClick={marking=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,"sera_refresh_job_alerts")};marking=false;error=r.second;if(r.first!=null){refresh++;SeraUnreadRefresh.changed()}}}){Text("Refresh job matches")}}}}
   groups.forEachIndexed{index,g->
    val n=g.latest;val day=dateGroup(n);if(index==0||dateGroup(groups[index-1].latest)!=day)item(key="day-"+day){Text(day,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
    item(key=n.field("id")){
     val unreadIds=g.events.filter{it.isNull("read_at")}.map{it.field("id")};val isUnread=liveActivity&&unreadIds.isNotEmpty();val actor=people[n.field("actor_id")];val post=n.field("post_id").takeIf{it.isNotBlank()};val job=jobs[n.field("job_id")];val isJob=n.field("kind")=="job_match"
     val names=g.actors.map{people[it]?.name?:"Sera member"};val name=if(names.size<=2)names.joinToString(" and ")else names.take(2).joinToString(", ")+" and "+(names.size-2)+" other"+if(names.size==3)"" else "s"
     val action=when(n.field("kind")){"like"->"liked your post";"comment"->"commented on your post";"share"->"shared your post";"repost"->"reposted your post";"reply"->"replied to your comment";"follow"->"started following you";"connection_request"->"sent a connection invitation";"connection_accepted"->"accepted your invitation";"space_message"->"sent a message in your space";"missed_call"->"called you while you were away";else->"interacted with you"}
     Surface(shape=RoundedCornerShape(18.dp),color=if(isUnread)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().clickable(enabled=liveActivity){if(isUnread)mark(unreadIds);if(post!=null)selectedPost=post;if(job!=null)selectedJob=SearchHit(job.field("id"),"Jobs",job.field("title"),job.field("company"));if(n.field("kind")=="space_message"&&SeraMessageAlerts.validId(n.field("space_id")))selectedSpace=n.field("space_id");if(n.field("kind")=="missed_call"&&SeraMessageAlerts.validId(n.field("conversation_id")))onOpenConversation(n.field("conversation_id"))}){
      Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
       if(!isJob&&actor?.avatar!=null)SeraMediaImage(actor.avatar,"Member photo",96,96,Modifier.size(40.dp).clip(CircleShape),accessToken=token)else Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer,CircleShape),contentAlignment=Alignment.Center){Text(if(isJob)"J" else name.take(1),fontWeight=FontWeight.Bold)}
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(if(isJob)job?.field("title")?:"Opportunity no longer available" else "$name $action",fontWeight=FontWeight.SemiBold,fontSize=14.sp)
        if(isJob&&job!=null){Text(job.field("company"),style=MaterialTheme.typography.bodySmall);val arr=n.optJSONArray("match_reasons");if(arr!=null)Text("Matches: "+(0 until arr.length()).map{arr.optString(it)}.joinToString(", "),color=teal,style=MaterialTheme.typography.labelSmall)}
        if(n.field("kind")=="missed_call")Text("Open conversation to call back",color=teal,style=MaterialTheme.typography.labelSmall)
        if(n.field("kind")=="space_message")Text("Open team conversation",color=teal,style=MaterialTheme.typography.labelSmall)
        if(g.events.size>1)Text("${g.events.size} activities"+if(isUnread)" · ${unreadIds.size} unread"else "",style=MaterialTheme.typography.labelSmall)
        Text(runCatching{DateTimeFormatter.ofPattern("d MMM · HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.parse(n.field("created_at")))}.getOrDefault(""),style=MaterialTheme.typography.labelSmall)
       }
       if(isUnread)Box(Modifier.size(8.dp).background(teal,CircleShape))
      }
     }
    }
   }
   if(rows.isEmpty()&&!loading&&error.isBlank())item{Text("You're all caught up",Modifier.padding(24.dp),style=MaterialTheme.typography.titleMedium)}
   if(page>1||rows.size>30)item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={page--;rows=emptyList();liveActivity=false;loading=true},enabled=page>1){Text("Previous")};Text("Page $page");TextButton(onClick={page++;rows=emptyList();liveActivity=false;loading=true},enabled=rows.size>30&&page<100){Text("Next")}}}
  }
 }
 selectedPost?.let{SeraNativePostDetail(token,it,onClose={selectedPost=null;refresh++})}
}
