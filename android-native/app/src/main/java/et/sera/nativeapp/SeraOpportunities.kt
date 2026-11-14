package et.sera.nativeapp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
@Composable internal fun SeraOpportunities(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var rows by remember{mutableStateOf<List<JSONObject>>(emptyList())};var applicants by remember{mutableStateOf<List<JSONObject>>(emptyList())};var mine by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<String?>(null)};var editor by remember{mutableStateOf(false)};var title by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var skills by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var reload by remember{mutableIntStateOf(0)};var confirm by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 val context=androidx.compose.ui.platform.LocalContext.current
 var live by remember(token,userId){mutableStateOf(false)}
 var savedPreview by remember(token,userId){mutableStateOf(false)}
 fun act(rpc:String,p:JSONObject,done:()->Unit={}){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,rpc,p)};busy=false;error=r.second;if(r.first!=null){withContext(Dispatchers.IO){SeraDirectoryCache(context,userId).clear("explore")};done();reload++}}}
 fun back(){if(selected!=null)selected=null else onBack()}
 androidx.activity.compose.BackHandler{back()}
 LaunchedEffect(token,userId,mine,reload){
  loading=true;live=false;savedPreview=false;rows=emptyList()
  if(token.isBlank()||userId.isBlank()){loading=false;return@LaunchedEffect}
  val cache=SeraDirectoryCache(context,userId);val generation=cache.generation()
  if(!mine)withContext(Dispatchers.IO){cache.readExplore("opportunities")}?.let{rows=it;savedPreview=true}
  val r=withContext(Dispatchers.IO){spaceCall(token,"sera_opportunities_list",JSONObject().put("p_mine",mine))}
  if(r.first!=null){rows=spaceRows(r.first);live=true;savedPreview=false;if(!mine)withContext(Dispatchers.IO){cache.writeExplore("opportunities",rows,generation)}}
  else if(seraCacheDenied(r.second)){rows=emptyList();selected=null;savedPreview=false;withContext(Dispatchers.IO){cache.clear("explore")}}
  error=r.second;loading=false
 }
 val detail=if(live)rows.firstOrNull{it.optString("id")==selected} else null
 LaunchedEffect(token,selected,reload,detail?.optString("owner_id")){applicants=emptyList();if(detail?.optString("owner_id")==userId){val r=withContext(Dispatchers.IO){spaceCall(token,"sera_opportunity_applicants",JSONObject().put("p_id",selected))};applicants=spaceRows(r.first)}}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 TextButton(onClick={back()}){Text("Back")}
 if(savedPreview)Text("Saved previews · checking availability",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF123A40),Color(0xFF087E83)))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(detail?.optString("title")?:"Build together",color=Color.White,style=MaterialTheme.typography.headlineSmall);Text("Find opportunities to create something meaningful.",color=Color(0xFFCBEBE7),style=MaterialTheme.typography.bodyMedium)}
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={reload++}){Text("Retry")}}
 if(detail==null){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!mine,onClick={mine=false},label={Text("Discover")});FilterChip(selected=mine,onClick={mine=true},label={Text("My opportunities")})};Button(onClick={editor=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("Publish opportunity")}
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){items(rows,key={it.getString("id")}){o->ElevatedCard(onClick={selected=o.getString("id")},enabled=live,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().animateContentSize()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(o.optString("title"),style=MaterialTheme.typography.titleLarge);Text("By "+o.optString("full_name"),style=MaterialTheme.typography.bodySmall);Text(o.optString("skills"),color=MaterialTheme.colorScheme.primary);if(o.optString("matched_skills").isNotBlank())Text("Matches your skills: "+o.optString("matched_skills"),style=MaterialTheme.typography.labelMedium);Text(if(o.optBoolean("closed"))"Closed" else "${o.optInt("interest_count")} interested",style=MaterialTheme.typography.labelSmall)}}};if(rows.isEmpty()&&!loading)item{Text("No opportunities yet. Share an idea with the community.")}}
 }else{LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("By "+detail.optString("full_name"),style=MaterialTheme.typography.titleMedium);Text(detail.optString("description"));Text("Skills needed",style=MaterialTheme.typography.titleMedium);Text(detail.optString("skills"));val interested=detail.optBoolean("interested");val closed=detail.optBoolean("closed");if(detail.optString("owner_id")==userId){if(!closed)OutlinedButton(onClick={confirm=detail.getString("id")}){Text("Close opportunity")};Text("Interested professionals",style=MaterialTheme.typography.titleMedium)}else if(!closed||interested)Button(onClick={act("sera_opportunity_interest",JSONObject().put("p_id",detail.getString("id")).put("p_interested",!interested))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(interested)"Withdraw interest" else "I'm interested")}}
 items(applicants,key={it.getString("member_id")}){p->ElevatedCard(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(p.optString("full_name"),style=MaterialTheme.typography.titleMedium);Text("Interested in collaborating",style=MaterialTheme.typography.bodySmall)}}}}
 }}
 confirm?.let{id->AlertDialog(onDismissRequest={confirm=null},title={Text("Close opportunity?")},text={Text("New expressions of interest will no longer be accepted.")},confirmButton={TextButton(onClick={confirm=null;act("sera_close_opportunity",JSONObject().put("p_id",id)){selected=null;mine=true}}){Text("Close")}},dismissButton={TextButton(onClick={confirm=null}){Text("Keep open")}})}
 if(editor)SeraSignatureSheet(onDismissRequest={if(!busy)editor=false}){Column(Modifier.fillMaxWidth().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Publish collaboration",style=MaterialTheme.typography.headlineSmall);OutlinedTextField(title,{title=it.take(120)},label={Text("Project or idea")},modifier=Modifier.fillMaxWidth());OutlinedTextField(desc,{desc=it.take(2000)},label={Text("What do you want to build?")},modifier=Modifier.fillMaxWidth(),maxLines=4);OutlinedTextField(skills,{skills=it.take(500)},label={Text("Skills needed (comma-separated)")},modifier=Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={act("sera_create_opportunity",JSONObject().put("p_title",title).put("p_description",desc).put("p_skills",skills)){editor=false;title="";desc="";skills="";mine=true}},enabled=!busy&&title.trim().length>=3&&desc.trim().length>=3&&skills.trim().length>=2,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Publishing…" else "Publish opportunity")}}}
}
