package et.sera.nativeapp
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
private fun portfolioRequest(token:String,path:String,body:JSONObject?=null):Pair<String?,String>{
 var c:HttpURLConnection?=null
 return try{c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};if(c.responseCode in 200..299)c.inputStream.bufferedReader().use{it.readText()} to "" else null to "Could not save or load portfolio. Check your details."}catch(_:Exception){null to "Connection unavailable."}finally{c?.disconnect()}
}
private data class Showcase(val id:String="",val kind:String="project",val title:String="",val description:String="",val issuer:String="",val url:String="",val shared:Boolean=false,val featured:Boolean=false)
@Composable internal fun SeraPortfolioSettings(token:String,userId:String){
 var items by remember(userId){mutableStateOf<List<Showcase>>(emptyList())};var edit by remember{mutableStateOf<Showcase?>(null)};var deleting by remember{mutableStateOf<Showcase?>(null)};var reload by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 LaunchedEffect(token,userId,reload){val r=withContext(Dispatchers.IO){portfolioRequest(token,"sera_portfolio_items?select=id,kind,title,description,issuer,external_url,shared,featured_at&user_id=eq.$userId&order=featured_at.desc.nullslast,created_at.desc&limit=50")};error=r.second;r.first?.let{raw->runCatching{val a=JSONArray(raw);items=(0 until a.length()).map{val o=a.getJSONObject(it);Showcase(o.getString("id"),o.getString("kind"),o.getString("title"),o.getString("description"),o.getString("issuer"),o.getString("external_url"),o.getBoolean("shared"),!o.isNull("featured_at"))}}}}
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
 Text("Portfolio & achievements",style=MaterialTheme.typography.titleMedium)
 Text("Showcase your work and self-reported credentials. Public sharing is optional.",style=MaterialTheme.typography.bodySmall)
 FilledTonalButton(onClick={edit=Showcase()},modifier=Modifier.fillMaxWidth()){Text("Add showcase")}
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 items.forEach{item->Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.fillMaxWidth().padding(12.dp)){Text(item.kind.replaceFirstChar{it.uppercase()}+" · "+if(item.shared)"Included when published" else "Private",style=MaterialTheme.typography.labelSmall);if(item.featured)Text("Featured",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall);Text(item.title,style=MaterialTheme.typography.titleMedium);if(item.issuer.isNotBlank())Text(item.issuer,style=MaterialTheme.typography.bodySmall);Text(item.description);if(item.kind=="project")SeraProjectGallery(token,item.id,userId);Row{TextButton(onClick={scope.launch{val r=withContext(Dispatchers.IO){portfolioRequest(token,"rpc/sera_feature_portfolio_item",JSONObject().put("p_id",item.id).put("p_feature",!item.featured))};error=r.second;reload++}}){Text(if(item.featured)"Unfeature" else "Feature")};TextButton(onClick={edit=item}){Text("Edit")};TextButton(onClick={deleting=item}){Text("Remove")}}}}}
 }
 deleting?.let{item->AlertDialog(onDismissRequest={deleting=null},title={Text("Remove showcase?")},text={Text("This item will be removed from your portfolio.")},confirmButton={TextButton(onClick={deleting=null;scope.launch{val r=withContext(Dispatchers.IO){portfolioRequest(token,"rpc/sera_delete_portfolio_item",JSONObject().put("p_id",item.id))};error=r.second;reload++}}){Text("Remove")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})}
 edit?.let{item->SeraSignatureSheet(onDismissRequest={if(!busy)edit=null}){Column(Modifier.fillMaxWidth().imePadding().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
 Text("Professional showcase",style=MaterialTheme.typography.titleLarge)
 Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("project","award").forEach{k->FilterChip(selected=item.kind==k,onClick={edit=item.copy(kind=k)},label={Text(k)})}}
 Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("certification","achievement").forEach{k->FilterChip(selected=item.kind==k,onClick={edit=item.copy(kind=k)},label={Text(k)})}}
 OutlinedTextField(item.title,{edit=item.copy(title=it.take(120))},label={Text("Title")},modifier=Modifier.fillMaxWidth())
 OutlinedTextField(item.issuer,{edit=item.copy(issuer=it.take(120))},label={Text("Organization or issuer")},modifier=Modifier.fillMaxWidth())
 OutlinedTextField(item.description,{edit=item.copy(description=it.take(2000))},label={Text("Description")},modifier=Modifier.fillMaxWidth(),maxLines=3)
 OutlinedTextField(item.url,{edit=item.copy(url=it.take(500))},label={Text("HTTPS link")},modifier=Modifier.fillMaxWidth(),singleLine=true)
 Row(verticalAlignment=Alignment.CenterVertically){Text("Include in public portfolio",Modifier.weight(1f));Switch(item.shared,{edit=item.copy(shared=it)})}
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 Button(onClick={busy=true;scope.launch{val r=withContext(Dispatchers.IO){portfolioRequest(token,"rpc/sera_save_portfolio_item",JSONObject().put("p_id",if(item.id.isBlank())JSONObject.NULL else item.id).put("p_kind",item.kind).put("p_title",item.title).put("p_description",item.description).put("p_issuer",item.issuer).put("p_url",item.url).put("p_shared",item.shared))};busy=false;error=r.second;if(r.first!=null){edit=null;reload++}}},enabled=!busy&&item.title.isNotBlank()&&(item.url.isBlank()||item.url.startsWith("https://")),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Saving…" else "Save showcase")}
 }}}
}
