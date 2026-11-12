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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
internal fun spaceCall(token:String,rpc:String,p:JSONObject=JSONObject()):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/rpc/"+rpc).openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(p.toString().toByteArray())};if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText()} to "" else null to "Unable to complete space action."}catch(_:Exception){null to "Connection unavailable."}finally{c?.disconnect()}
}
private data class Space(val id:String,val name:String,val description:String,val owner:String,val status:String,val count:Int)
internal fun spaceRows(raw:String?):List<JSONObject> = runCatching{val a=JSONArray(raw?:"[]");(0 until a.length()).map{a.getJSONObject(it)}}.getOrDefault(emptyList())
@Composable internal fun SeraCollaboration(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var chat by remember{mutableStateOf(false)};var spaces by remember{mutableStateOf<List<Space>>(emptyList())};var selected by remember{mutableStateOf<String?>(null)};var roster by remember{mutableStateOf<List<JSONObject>>(emptyList())};var peers by remember{mutableStateOf<List<JSONObject>>(emptyList())};var creating by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var description by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 fun act(rpc:String,p:JSONObject,done:()->Unit={}){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,rpc,p)};busy=false;error=r.second;if(r.first!=null){done();refresh++}}}
 if(chat){val team=spaces.find{it.id==selected&&it.status=="active"};if(team!=null){SeraSpaceChat(token,userId,team.id,team.name,modifier){chat=false};return}}
 androidx.activity.compose.BackHandler{if(selected!=null)selected=null else onBack()}
 LaunchedEffect(token,refresh){loading=true;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_list_spaces")};spaces=spaceRows(r.first).map{Space(it.getString("id"),it.getString("name"),it.getString("description"),it.getString("owner_id"),it.getString("status"),it.optInt("member_count"))};error=r.second;loading=false}
 LaunchedEffect(token,selected,refresh){roster=emptyList();val id=selected?:return@LaunchedEffect;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_space_members",JSONObject().put("p_space",id))};roster=spaceRows(r.first);if(r.second.isNotBlank())error=r.second
 val rel=withContext(Dispatchers.IO){loadSeraRelations(token,userId)}.first;val ids=rel?.requests?.filter{it.status=="accepted"}?.map{if(it.requester==userId)it.recipient else it.requester}?.distinct().orEmpty();peers=if(ids.isEmpty())emptyList() else spaceRows(withContext(Dispatchers.IO){spaceCall(token,"member_cards",JSONObject().put("ids",JSONArray(ids)))}.first)}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 TextButton(onClick={if(selected!=null)selected=null else onBack()}){Text("Back")};Text("Collaboration spaces",style=MaterialTheme.typography.headlineSmall)
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 val s=spaces.find{it.id==selected&&it.status=="active"}
 if(s==null){Text("Private teams for shared professional initiatives.");Button(onClick={creating=!creating},modifier=Modifier.fillMaxWidth()){Text("Create space")}
 if(creating){OutlinedTextField(name,{name=it.take(80)},label={Text("Space name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(description,{description=it.take(500)},label={Text("Description")},modifier=Modifier.fillMaxWidth());Button(onClick={act("sera_create_space",JSONObject().put("p_name",name).put("p_description",description)){creating=false;name="";description=""}},enabled=!busy&&name.trim().length>=3){Text("Create")}}
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){items(spaces,key={it.id}){g->ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(g.name,style=MaterialTheme.typography.titleLarge);Text(g.description);Text(g.status+" · "+g.count+" members");if(g.status=="active")Button(onClick={selected=g.id}){Text("Open space")}else Row{Button(onClick={act("sera_space_respond",JSONObject().put("p_space",g.id).put("p_accept",true))},enabled=!busy){Text("Accept")};TextButton(onClick={act("sera_space_respond",JSONObject().put("p_space",g.id).put("p_accept",false))},enabled=!busy){Text("Decline")}}}}}}
 }else{Text(s.name,style=MaterialTheme.typography.titleLarge);Text(s.description);Button(onClick={chat=true},modifier=Modifier.fillMaxWidth()){Text("Team conversation")};Text("Team members",style=MaterialTheme.typography.titleMedium)
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(roster,key={it.getString("member_id")}){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(m.optString("full_name"));Text(m.optString("status"),style=MaterialTheme.typography.bodySmall)};if(s.owner==userId&&m.optString("member_id")!=userId)TextButton(onClick={act("sera_space_remove",JSONObject().put("p_space",s.id).put("p_member",m.optString("member_id")))},enabled=!busy){Text("Remove")}}}
 if(s.owner==userId){item{Text("Invite a connection",style=MaterialTheme.typography.titleMedium)};items(peers.filter{p->roster.none{it.optString("member_id")==p.optString("id")}},key={it.getString("id")}){p->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(p.optString("full_name"));TextButton(onClick={act("sera_space_invite",JSONObject().put("p_space",s.id).put("p_member",p.optString("id")))},enabled=!busy){Text("Invite")}}}}
 };if(s.owner!=userId)OutlinedButton(onClick={act("sera_space_remove",JSONObject().put("p_space",s.id).put("p_member",userId)){selected=null}},enabled=!busy){Text("Leave space")}}
 }
}
