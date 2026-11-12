package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
private data class NotificationBatch(val rows:List<JSONObject>?,val people:Map<String,NotificationPerson>,val error:String)
private fun loadNotifications(token:String):NotificationBatch{
 // RLS restricts this query to the authenticated recipient, exactly as on web.
 val r=notificationCall(token,"professional_notifications?select=id,actor_id,kind,post_id,created_at,read_at&order=created_at.desc,id.desc&limit=80")
 if(r.first==null)return NotificationBatch(null,emptyMap(),r.second)
 val rows=runCatching{val a=JSONArray(r.first);(0 until a.length()).map{a.getJSONObject(it)}}.getOrElse{return NotificationBatch(null,emptyMap(),"Unable to read notifications.")}
 val ids=rows.map{it.optString("actor_id")}.filter{it.isNotBlank()}.distinct()
 val people=if(ids.isEmpty())emptyMap() else spaceRows(spaceCall(token,"member_cards",JSONObject().put("ids",JSONArray(ids))).first).associate{card->
  val path=card.optString("avatar_path").takeUnless{it.isBlank()||it=="null"}
  card.optString("id") to NotificationPerson(card.optString("full_name").takeUnless{it.isBlank()||it=="null"}?:"SERA member",path?.let{signedPostImage(token,it,"sera-account-private")})
 }
 return NotificationBatch(rows,people,"")
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SeraNativeNotifications(token:String,userId:String,modifier:Modifier=Modifier){
 var rows by remember(token){mutableStateOf<List<JSONObject>>(emptyList())}
 var people by remember(token){mutableStateOf<Map<String,NotificationPerson>>(emptyMap())}
 var error by remember(token){mutableStateOf("")}
 var loading by remember(token){mutableStateOf(true)}
 var refresh by remember(token){mutableIntStateOf(0)}
 var selectedPost by remember(token){mutableStateOf<String?>(null)}
 var marking by remember(token){mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 val teal=Color(0xFF087E83)
 fun mark(id:String?){
  if(marking)return
  marking=true
  scope.launch{
   val r=withContext(Dispatchers.IO){notificationCall(token,"professional_notifications?read_at=is.null"+(id?.let{"&id=eq."+it}?:""),true)}
   marking=false;error=r.second
   if(r.first!=null){refresh++;SeraUnreadRefresh.changed()}
  }
 }
 LaunchedEffect(token,refresh,lifecycle){
  if(token.isBlank()){loading=false;error="Sign in to view notifications.";return@LaunchedEffect}
  lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
   while(true){
    val result=withContext(Dispatchers.IO){loadNotifications(token)}
    if(result.rows!=null){rows=result.rows;people=result.people}
    error=result.error;loading=false
    delay(20000)
   }
  }
 }
 val unread=rows.count{it.isNull("read_at")}
 Column(modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){Text("Notifications",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(unread>0)"${unread} unread" else "Your professional activity",style=MaterialTheme.typography.labelMedium,color=teal)}
   TextButton(onClick={loading=true;refresh++},enabled=!loading){Text("Refresh")}
   TextButton(onClick={mark(null)},enabled=unread>0&&!marking){Text("Read all")}
  }
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
   items(rows,key={it.getString("id")}){n->
    val isUnread=n.isNull("read_at");val actor=people[n.optString("actor_id")]
    val post=n.optString("post_id").takeUnless{it.isBlank()||it=="null"}
    val action=when(n.optString("kind")){"like"->"liked your post";"comment"->"commented on your post";"share"->"shared your post";"repost"->"reposted your post";"reply"->"replied to your comment";"follow"->"started following you";"connection_request"->"sent a connection invitation";"connection_accepted"->"accepted your invitation";else->"interacted with you"}
    Surface(shape=RoundedCornerShape(18.dp),color=if(isUnread)Color(0xFFF3E8FF) else Color.White,modifier=Modifier.fillMaxWidth().clickable{if(isUnread)mark(n.getString("id"));if(post!=null)selectedPost=post}){
     Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
      if(actor?.avatar!=null)SeraMediaImage(actor.avatar,"Member photo",96,96,Modifier.size(38.dp).clip(CircleShape)) else Box(Modifier.size(38.dp).background(Color(0xFFD0EBE7),CircleShape),contentAlignment=Alignment.Center){Text((actor?.name?:"S").take(1),color=teal,fontWeight=FontWeight.Bold)}
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
       Text(androidx.compose.ui.text.buildAnnotatedString{pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight=FontWeight.SemiBold));append(actor?.name?:"SERA member");pop();append(" "+action)},color=Color(0xFF183B40),fontSize=13.sp,lineHeight=17.sp)
       Text(runCatching{DateTimeFormatter.ofPattern("d MMM · HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.parse(n.optString("created_at")))}.getOrDefault(""),fontSize=11.sp,lineHeight=14.sp,color=Color(0xFF657D80))
      }
      if(isUnread)Box(Modifier.size(8.dp).background(SeraPurple,CircleShape))
     }
    }
   }
   if(rows.isEmpty()&&!loading&&error.isBlank())item{Column(Modifier.fillMaxWidth().padding(top=40.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("You're all caught up",style=MaterialTheme.typography.titleLarge);Text("New activity will appear here.",color=Color(0xFF657D80))}}
  }
 }
 selectedPost?.let{SeraNativePostDetail(token,it,onClose={selectedPost=null;refresh++})}
}
