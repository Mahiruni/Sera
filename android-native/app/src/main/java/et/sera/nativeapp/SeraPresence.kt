package et.sera.nativeapp
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun presenceRequest(token:String,path:String,body:JSONObject?=null):String?{
 var c:HttpURLConnection?=null
 return try{
 c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection
 c.connectTimeout=8000;c.readTimeout=8000
 c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token")
 if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}}
 if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText()}else null
 }catch(_:Exception){null}finally{c?.disconnect()}
}
internal fun presenceHeartbeat(token:String,visibility:String?=null):String?=presenceRequest(token,"rpc/sera_presence_heartbeat",JSONObject().apply{if(visibility!=null)put("p_visibility",visibility)})?.trim()?.trim('"')
internal fun feedPresence(token:String,ids:List<String>):Set<String>?{
 if(ids.isEmpty())return emptySet()
 val raw=presenceRequest(token,"rpc/sera_feed_presence",JSONObject().put("p_members",JSONArray(ids.take(100))))?:return null
 return runCatching{val arr=JSONArray(raw);(0 until arr.length()).map{arr.getJSONObject(it).getString("member_id")}.toSet()}.getOrNull()
}
internal fun peerPresence(token:String,id:String):String?{
 if(!id.matches(Regex("[0-9a-f-]{36}")))return null
 val raw=presenceRequest(token,"professional_presence?select=last_seen_at,visibility&member_id=eq.$id")?:return null
 return runCatching{
 val row=JSONArray(raw).optJSONObject(0)?:return null
 if(row.optString("visibility")!="connections")return null
 val seconds=java.time.Duration.between(java.time.Instant.parse(row.getString("last_seen_at")),java.time.Instant.now()).seconds.coerceAtLeast(0)
 when {seconds<90->"Active now";seconds<3600->"Active ${seconds/60}m ago";seconds<86400->"Active ${seconds/3600}h ago";else->null}
 }.getOrNull()
}
@Composable internal fun SeraPresenceHeartbeat(token:String,userId:String){
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 LaunchedEffect(token,userId,lifecycle){if(token.isNotBlank()&&userId.isNotBlank())lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){withContext(Dispatchers.IO){presenceHeartbeat(token)};delay(30000)}}}
}
@Composable internal fun SeraPresenceSettings(token:String,userId:String){
 var visibility by remember(userId){mutableStateOf<String?>(null)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 LaunchedEffect(token,userId){visibility=withContext(Dispatchers.IO){presenceHeartbeat(token)}}
 Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
 Text("Activity status",style=MaterialTheme.typography.titleMedium)
 Text("Show when you are active to accepted connections. Employer accounts are not shown as online in the public feed.",fontSize=12.sp,color=Color(0xFF607478))
 Row(verticalAlignment=Alignment.CenterVertically){
 Text(if(visibility=="hidden")"Hidden" else "Visible to connections",Modifier.weight(1f))
 Switch(checked=visibility=="connections",enabled=visibility!=null&&!busy,onCheckedChange={enabled->busy=true;scope.launch{val result=withContext(Dispatchers.IO){presenceHeartbeat(token,if(enabled)"connections" else "hidden")};if(result!=null){visibility=result;error=""}else error="Could not save activity preference. Try again.";busy=false}})
 }
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
 }
}
@Composable internal fun SeraOnlineDot(modifier:Modifier=Modifier){Box(modifier.size(12.dp).background(Color.White,CircleShape).padding(2.dp).background(Color(0xFF16A879),CircleShape))}
