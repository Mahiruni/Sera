package et.sera.nativeapp
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
private fun usernameRequest(token:String,path:String,body:JSONObject?=null):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=10000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText()} to "" else null to (runCatching{JSONObject(c.errorStream.bufferedReader().use{it.readText()}).optString("message")}.getOrDefault("Request failed"))}catch(_:Exception){null to "Connection unavailable"}finally{c?.disconnect()}
}
@Composable internal fun SeraUsernameSettings(token:String,userId:String){
 var current by remember(userId){mutableStateOf("")};var value by remember(userId){mutableStateOf("")};var status by remember{mutableStateOf("")};var available by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();val context=LocalContext.current
 LaunchedEffect(token,userId){val result=withContext(Dispatchers.IO){usernameRequest(token,"professional_usernames?select=username&user_id=eq.$userId")};current=runCatching{JSONArray(result.first?:"[]").optJSONObject(0)?.optString("username").orEmpty()}.getOrDefault("");value=current}
 LaunchedEffect(value,current,token){available=false;if(value!=current&&value.matches(Regex("[a-z][a-z_]{2,29}"))){delay(400);val result=withContext(Dispatchers.IO){usernameRequest(token,"rpc/sera_username_available",JSONObject().put("p_username",value))};available=result.first?.trim()=="true";status=if(available)"Available" else "Unavailable or reserved"}}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Professional username",style=MaterialTheme.typography.titleMedium);Text("Letters and underscores only · 3–30 characters · change once every 60 days",style=MaterialTheme.typography.bodySmall);OutlinedTextField(value,{if(it.length<=30){value=it.lowercase();status=""}},label={Text("Username")},prefix={Text("@")},singleLine=true,modifier=Modifier.fillMaxWidth());if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall);Button(onClick={busy=true;scope.launch{val result=withContext(Dispatchers.IO){usernameRequest(token,"rpc/sera_set_username",JSONObject().put("p_username",value))};if(result.first!=null){current=value;status="Username saved"}else status=result.second;busy=false}},enabled=available&&!busy,modifier=Modifier.fillMaxWidth()){Text("Save username")};if(current.isNotBlank()){Text("@"+current,style=MaterialTheme.typography.titleMedium);TextButton(onClick={val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,"https://sera.hisabtech.com/"+current)};context.startActivity(android.content.Intent.createChooser(intent,"Share profile"))}){Text("Share profile link")}}}
}
