package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
internal fun openTalentConversation(token:String,peer:String):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/rpc/sera_open_talent_conversation").openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("p_peer",peer).toString().toByteArray())};if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText().trim().trim('"')} to "" else null to "Connect with this professional before messaging."}catch(_:Exception){null to "Connection unavailable."}finally{c?.disconnect()}
}
@Composable internal fun SeraTalentActions(token:String,userId:String,peer:String,onMessage:(String)->Unit){
 var relation by remember(token,userId,peer){mutableStateOf<SeraNetworkRelations?>(null)};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var refresh by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 LaunchedEffect(token,userId,peer,refresh){val r=withContext(Dispatchers.IO){loadSeraRelations(token,userId)};relation=r.first;error=r.second}
 val r=relation;val pair=r?.requests?.filter{(it.requester==userId&&it.recipient==peer)||(it.requester==peer&&it.recipient==userId)}.orEmpty();val connected=pair.any{it.status=="accepted"};val pending=pair.firstOrNull{it.status=="pending"};val following=r?.follows?.contains(peer)==true
 fun perform(action:()->String){busy=true;scope.launch{val e=withContext(Dispatchers.IO){action()};busy=false;if(e.isBlank())refresh++ else error=e}}
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
 if(r!=null&&peer!=userId){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
 OutlinedButton(onClick={perform{changeSeraFollow(token,userId,peer,!following)}},enabled=!busy){Text(if(following)"Following" else "Follow")}
 if(connected)Button(onClick={busy=true;scope.launch{val v=withContext(Dispatchers.IO){openTalentConversation(token,peer)};busy=false;if(v.first!=null)onMessage(v.first!!) else error=v.second}},enabled=!busy){Text("Message")}
 else if(pending!=null)Button(onClick={perform{resolveSeraConnection(token,pending,userId,if(pending.recipient==userId)"accepted" else "withdrawn")}},enabled=!busy){Text(if(pending.recipient==userId)"Accept" else "Withdraw")}
 else if(pair.isEmpty())Button(onClick={perform{requestSeraConnection(token,userId,peer)}},enabled=!busy){Text("Connect")}
 else Text("Invitation resolved")
 };if(connected)Text("Connected",style=MaterialTheme.typography.labelSmall);if(pending?.recipient==userId)TextButton(onClick={perform{resolveSeraConnection(token,pending,userId,"declined")}},enabled=!busy){Text("Decline invitation")}}
 if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);TextButton(onClick={refresh++}){Text("Retry")}}
 }
}
