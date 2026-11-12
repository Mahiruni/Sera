package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun communityRequest(token:String,path:String,body:JSONObject?=null):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection;c.connectTimeout=12000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText()} to "" else null to "Unable to complete community request. Please retry."}catch(_:Exception){null to "Connection unavailable."}finally{c?.disconnect()}
}
private data class Community(val id:String,val name:String,val description:String,val topic:String,val count:Int,val joined:Boolean,val owner:Boolean)
@Composable internal fun SeraCommunities(token:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var groups by remember{mutableStateOf<List<Community>>(emptyList())};var query by remember{mutableStateOf("")};var reload by remember{mutableIntStateOf(0)};var error by remember{mutableStateOf("")};var selected by remember{mutableStateOf<Community?>(null)};var messages by remember{mutableStateOf<List<Pair<String,String>>>(emptyList())};var draft by remember{mutableStateOf("")};var creating by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var description by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(true)};val scope=rememberCoroutineScope()
 androidx.activity.compose.BackHandler{if(selected!=null){selected=null;messages=emptyList()}else onBack()}
 LaunchedEffect(token,reload){loading=true;val r=withContext(Dispatchers.IO){communityRequest(token,"rpc/sera_community_directory",JSONObject().put("p_query","").put("p_topic",""))};error=r.second;r.first?.let{raw->runCatching{val a=JSONArray(raw);groups=(0 until a.length()).map{val g=a.getJSONObject(it);Community(g.getString("id"),g.getString("name"),g.getString("description"),g.getString("topic"),g.optInt("member_count"),g.optBoolean("joined"),g.optBoolean("is_owner"))}}};loading=false}
 LaunchedEffect(token,selected?.id,reload){val g=selected?:return@LaunchedEffect;val r=withContext(Dispatchers.IO){communityRequest(token,"sera_community_posts?select=id,body&community_id=eq.${g.id}&order=created_at.desc&limit=50")};error=r.second;r.first?.let{raw->runCatching{val a=JSONArray(raw);messages=(0 until a.length()).map{val p=a.getJSONObject(it);p.getString("id") to p.getString("body")}}}}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={if(selected!=null){selected=null;messages=emptyList()}else onBack()}){Text("Back")};Text(selected?.name?:"Communities",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color(0xFF183B40))}
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(selected==null){
 Text("Find your people. Build something together.",color=Color(0xFF607478))
 OutlinedTextField(query,{query=it.take(80)},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search communities")},singleLine=true,shape=RoundedCornerShape(16.dp))
 Button(onClick={creating=!creating},modifier=Modifier.fillMaxWidth()){Text(if(creating)"Close" else "Create community")}
 if(creating){OutlinedTextField(name,{name=it.take(70)},label={Text("Name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(description,{description=it.take(500)},label={Text("Description")},modifier=Modifier.fillMaxWidth());Button(onClick={busy=true;scope.launch{val r=withContext(Dispatchers.IO){communityRequest(token,"rpc/sera_create_community",JSONObject().put("p_name",name).put("p_description",description).put("p_topic","other"))};busy=false;error=r.second;if(r.first!=null){creating=false;name="";description="";reload++}}},enabled=!busy&&name.trim().length>=3){Text("Create")}}
 LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(groups.filter{(it.name+" "+it.description+" "+it.topic).contains(query,ignoreCase=true)},key={it.id}){g->ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(g.topic+" · "+g.count+" members",fontSize=12.sp,color=Color(0xFF087E83));Text(g.name,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(g.description);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(g.joined)Button(onClick={selected=g}){Text("Open")};if(!g.owner)OutlinedButton(onClick={busy=true;scope.launch{val r=withContext(Dispatchers.IO){communityRequest(token,"rpc/sera_join_community",JSONObject().put("p_id",g.id).put("p_join",!g.joined))};busy=false;error=r.second;if(r.first!=null)reload++}},enabled=!busy){Text(if(g.joined)"Leave" else "Join")}}}}}}
 }else{val g=selected!!;LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(messages,key={it.first}){m->Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFFF0F7F6)){Text(m.second,Modifier.fillMaxWidth().padding(14.dp))}}};OutlinedTextField(draft,{draft=it.take(2000)},placeholder={Text("Share a professional insight")},modifier=Modifier.fillMaxWidth(),maxLines=4);Button(onClick={busy=true;scope.launch{val r=withContext(Dispatchers.IO){communityRequest(token,"rpc/sera_publish_community_post",JSONObject().put("p_id",g.id).put("p_body",draft))};busy=false;error=r.second;if(r.first!=null){draft="";reload++}}},enabled=!busy&&draft.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Publish discussion")}}
 }
}
