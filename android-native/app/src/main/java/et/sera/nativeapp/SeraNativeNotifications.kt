package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
private fun notificationCall(token:String,path:String,patch:Boolean=false):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");if(patch){c.requestMethod="PATCH";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("read_at",java.time.Instant.now().toString()).toString().toByteArray())}};if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText()} to "" else null to "Notifications unavailable."}catch(_:Exception){null to "Connection unavailable."}finally{c?.disconnect()}
}
@Composable internal fun SeraNativeNotifications(token:String,userId:String,modifier:Modifier=Modifier){
 var rows by remember(token,userId){mutableStateOf<List<JSONObject>>(emptyList())};var names by remember(token,userId){mutableStateOf<Map<String,String>>(emptyMap())};var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var refresh by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 fun mark(id:String?){scope.launch{val r=withContext(Dispatchers.IO){notificationCall(token,"professional_notifications?recipient_id=eq.$userId&read_at=is.null"+(id?.let{"&id=eq.$it"}?:""),true)};error=r.second;if(r.first!=null)refresh++}}
 LaunchedEffect(token,userId,refresh){if(userId.isBlank())return@LaunchedEffect;loading=true;val r=withContext(Dispatchers.IO){notificationCall(token,"professional_notifications?select=id,actor_id,kind,created_at,read_at&recipient_id=eq.$userId&order=created_at.desc&limit=80")};rows=spaceRows(r.first);error=r.second;val ids=rows.map{it.optString("actor_id")}.distinct();names=if(ids.isEmpty())emptyMap() else spaceRows(withContext(Dispatchers.IO){spaceCall(token,"member_cards",JSONObject().put("ids",JSONArray(ids)))}.first).associate{it.optString("id") to it.optString("full_name")};loading=false}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text("Notifications",style=MaterialTheme.typography.headlineMedium);Row{TextButton(onClick={refresh++}){Text("Refresh")};TextButton(onClick={mark(null)},enabled=rows.any{it.isNull("read_at")}){Text("Mark all read")}}
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(rows,key={it.getString("id")}){n->val unread=n.isNull("read_at");val action=when(n.optString("kind")){"like"->"liked your post";"comment"->"commented on your post";"share"->"shared your post";"follow"->"followed you";"connection_request"->"sent a connection invitation";"connection_accepted"->"accepted your invitation";else->"interacted with you"};Surface(shape=RoundedCornerShape(18.dp),color=if(unread)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.fillMaxWidth().padding(16.dp)){Text((names[n.optString("actor_id")]?:"SERA member")+" "+action,style=MaterialTheme.typography.bodyLarge);Text(n.optString("created_at").take(16).replace('T',' '),style=MaterialTheme.typography.labelSmall);if(unread)TextButton(onClick={mark(n.getString("id"))}){Text("Mark read")}}}};if(rows.isEmpty()&&!loading&&error.isBlank())item{Text("You're all caught up.")}}
 }
}
