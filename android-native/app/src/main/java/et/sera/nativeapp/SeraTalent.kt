package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
private data class Talent(val id:String,val name:String,val headline:String,val skills:String,val username:String,val title:String,val kind:String,val matches:String)
private fun fetchTalent(token:String,q:String,offset:Int,saved:Boolean=false,recommended:Boolean=false):Pair<List<Talent>?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/rpc/"+(if(saved)"sera_list_saved_talent" else if(recommended)"sera_recommend_talent" else "sera_discover_talent")).openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write((if(saved||recommended) JSONObject() else JSONObject().put("p_query",q).put("p_offset",offset)).toString().toByteArray())};if(c.responseCode !in 200..299)null to "Unable to load talent. Please retry." else {val arr=JSONArray(c.inputStream.bufferedReader().use{it.readText()});(0 until arr.length()).map{val r=arr.getJSONObject(it)
 val field: (String) -> String = { k -> r.optString(k).takeUnless { value -> value == "null" }.orEmpty() }
 Talent(field("member_id"),field("full_name"),field("headline"),field("skills"),field("username"),field("showcase_title"),field("showcase_kind"),field("matched_skills"))} to ""}}catch(_:Exception){null to "Connection unavailable."}finally{c?.disconnect()}
}
@Composable internal fun SeraTalent(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var query by remember{mutableStateOf("")};var page by remember{mutableIntStateOf(0)};var rows by remember{mutableStateOf<List<Talent>>(emptyList())};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var retry by remember{mutableIntStateOf(0)};var selected by remember { mutableStateOf<Talent?>(null) };var thread by remember { mutableStateOf<String?>(null) };var savedOnly by remember{mutableStateOf(false)};var recommended by remember{mutableStateOf(false)};var savedIds by remember{mutableStateOf<Set<String>>(emptySet())};var saving by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 androidx.activity.compose.BackHandler(enabled=selected==null,onBack=onBack)
 LaunchedEffect(token,query,page,retry,savedOnly,recommended){loading=true;delay(300);val r=withContext(Dispatchers.IO){fetchTalent(token,query.trim(),page*30,savedOnly,recommended)};rows=r.first.orEmpty();error=r.second;loading=false}
 LaunchedEffect(token,retry){val r=withContext(Dispatchers.IO){fetchTalent(token,"",0,true)};r.first?.let{savedIds=it.map{p->p.id}.toSet()}}
 selected?.let { member ->
 if(thread!=null){SeraNativeInbox(token,userId,modifier,initialThread=thread,initialPeer=member.id,initialName=member.name,onThreadExit={thread=null});return}
 SeraTalentPortfolio(token,userId,member.id,modifier,onMessage={thread=it}){selected=null}; return }
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 TextButton(onClick=onBack){Text("Back")};Text("Discover talent",style=MaterialTheme.typography.headlineSmall);Text("Explore published work and professional achievements.",style=MaterialTheme.typography.bodySmall)
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!savedOnly&&!recommended,onClick={savedOnly=false;recommended=false;page=0},label={Text("Discover")});FilterChip(selected=recommended,onClick={recommended=true;savedOnly=false;page=0},label={Text("For you")});FilterChip(selected=savedOnly,onClick={savedOnly=true;recommended=false;page=0},label={Text("Shortlist")})}
 if(recommended) Text("Matches your profile skills",style=MaterialTheme.typography.bodySmall)
 if(!savedOnly&&!recommended) OutlinedTextField(query,{query=it.take(80);page=0},label={Text("Search skills or projects")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text("Retry")}}else if(!loading&&rows.isEmpty())Text(if(recommended)"No shared skills found yet. Add skills to your profile, separated by commas." else "No matching published portfolios.")
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){items(rows,key={it.id}){r->ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(r.name,style=MaterialTheme.typography.titleLarge);if(r.username.isNotBlank())Text("@"+r.username,color=MaterialTheme.colorScheme.primary);Text(r.headline);if(r.matches.isNotBlank())Text("Shared skills: "+r.matches,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium);if(r.skills.isNotBlank())Text(r.skills,style=MaterialTheme.typography.bodySmall);HorizontalDivider();Text(r.kind+" · Self-reported",style=MaterialTheme.typography.labelSmall);Text(r.title,style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={selected=r}){Text("View portfolio")};OutlinedButton(onClick={saving=true;scope.launch{val e=withContext(Dispatchers.IO){saveTalent(token,r.id,r.id !in savedIds)};saving=false;if(e.isBlank())retry++ else error=e}},enabled=!saving){Text(if(r.id in savedIds||savedOnly)"Remove" else "Save")}}}}}}
 if(!savedOnly&&!recommended) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={page--},enabled=page>0&&!loading){Text("Previous")};TextButton(onClick={page++},enabled=rows.size==30&&page<100&&!loading){Text("Next")}}
 }
}

private fun loadTalentPortfolio(token:String,id:String):JSONObject? {
 var c:HttpURLConnection?=null
 return try {
  c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/rpc/sera_talent_portfolio_detail").openConnection() as HttpURLConnection
  c.requestMethod="POST";c.doOutput=true;c.connectTimeout=10000;c.readTimeout=15000
  c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
  c.setRequestProperty("Authorization","Bearer $token")
  c.setRequestProperty("Content-Type","application/json")
  c.outputStream.use{it.write(JSONObject().put("p_member_id",id).toString().toByteArray())}
  if(c.responseCode !in 200..299) null else JSONObject(c.inputStream.bufferedReader().use{it.readText()})
 }catch(_:Exception){null}finally{c?.disconnect()}
}
private fun JSONObject.portfolioText(key:String)=optString(key).takeUnless{it=="null"}.orEmpty()
@Composable internal fun SeraTalentPortfolio(token:String,userId:String,id:String,modifier:Modifier,onMessage:(String)->Unit,onBack:()->Unit){
 var detail by remember(id,token){mutableStateOf<JSONObject?>(null)}
 var loading by remember(id,token){mutableStateOf(true)}
 var retry by remember(id,token){mutableIntStateOf(0)}
 val context=LocalContext.current
 val linkHandler=androidx.compose.ui.platform.LocalUriHandler.current
 androidx.activity.compose.BackHandler(onBack=onBack)
 LaunchedEffect(id,token,retry){loading=true;detail=null;detail=withContext(Dispatchers.IO){loadTalentPortfolio(token,id)};loading=false}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  TextButton(onClick=onBack){Text("Back to talent")}
  if(loading){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Loading portfolio")}
  else if(detail==null){Text("This portfolio is unavailable or could not be loaded.");TextButton(onClick={retry++}){Text("Retry")}}
  else detail?.let{p->
   val entries=p.optJSONArray("items")?:JSONArray()
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item { ElevatedCard(shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Professional portfolio",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge);Text(p.portfolioText("name"),style=MaterialTheme.typography.headlineMedium);Text(p.portfolioText("headline"));Text(p.portfolioText("location"),style=MaterialTheme.typography.bodySmall)}} }
    item { SeraTalentActions(token,userId,id,onMessage) }
    listOf("About" to "summary","Skills" to "skills","Experience" to "experience","Education" to "education").forEach{(label,key)->
     if(p.portfolioText(key).isNotBlank())item { Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,style=MaterialTheme.typography.titleMedium);Text(p.portfolioText(key))} }
    }
    item { Text("Projects & achievements",style=MaterialTheme.typography.titleLarge) }
    items(entries.length()){index->
     val entry=entries.getJSONObject(index)
     ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      if(entry.portfolioText("featured_at").isNotBlank())Text("Featured",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
      Text(entry.portfolioText("kind")+" · Self-reported",style=MaterialTheme.typography.labelSmall)
      Text(entry.portfolioText("title"),style=MaterialTheme.typography.titleLarge)
      if(entry.portfolioText("issuer").isNotBlank())Text(entry.portfolioText("issuer"),style=MaterialTheme.typography.bodySmall)
      Text(entry.portfolioText("description"))
      if(entry.portfolioText("kind")=="project")SeraProjectGallery(token,entry.portfolioText("id"))
      val url=entry.portfolioText("external_url")
      if(url.startsWith("https://"))TextButton(onClick={runCatching{linkHandler.openUri(url)}}){Text("View project or credential")}
     }}
    }
   }
  }
 }
}

private fun saveTalent(token:String,id:String,saved:Boolean):String{
 val c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/rpc/sera_save_talent").openConnection() as HttpURLConnection
 return try{c.requestMethod="POST";c.doOutput=true;c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("p_member",id).put("p_saved",saved).toString().toByteArray())};if(c.responseCode in 200..299)"" else "Unable to update shortlist."}catch(_:Exception){"Connection unavailable."}finally{c.disconnect()}
}
