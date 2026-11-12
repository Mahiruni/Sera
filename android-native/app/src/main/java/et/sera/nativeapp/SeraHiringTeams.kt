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
@Composable internal fun SeraHiringTeams(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var rows by remember{mutableStateOf<List<JSONObject>>(emptyList())};var candidates by remember{mutableStateOf<List<JSONObject>>(emptyList())};var job by remember{mutableStateOf<String?>(null)};var reload by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var review by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 fun act(rpc:String,p:JSONObject){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,rpc,p)};busy=false;error=r.second;if(r.first!=null){review=null;reload++}}}
 androidx.activity.compose.BackHandler{if(job!=null)job=null else onBack()}
 LaunchedEffect(token,reload){val r=withContext(Dispatchers.IO){spaceCall(token,"sera_hiring_list")};rows=spaceRows(r.first);error=r.second}
 LaunchedEffect(token,job,reload){candidates=emptyList();val id=job?:return@LaunchedEffect;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_hiring_candidates",JSONObject().put("p_job",id))};candidates=spaceRows(r.first);error=r.second}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick={if(job!=null)job=null else onBack()}){Text("Back")};Text("Hiring teams",style=MaterialTheme.typography.headlineMedium);Text("Review candidates for vacancies you have been invited to.");if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){if(job==null){items(rows,key={it.optString("job_id")+it.optString("member_id")}){m->ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(m.optString("job_title"),style=MaterialTheme.typography.titleLarge);Text(m.optString("full_name")+" · "+m.optString("role")+" · "+m.optString("status"));val id=m.getString("job_id");if(m.optString("owner_id")==userId){TextButton(onClick={act("sera_hiring_remove",JSONObject().put("p_job",id).put("p_member",m.getString("member_id")))},enabled=!busy){Text("Remove")}}else if(m.optString("status")=="invited"){Row{Button(onClick={act("sera_hiring_respond",JSONObject().put("p_job",id).put("p_accept",true))},enabled=!busy){Text("Accept")};TextButton(onClick={act("sera_hiring_respond",JSONObject().put("p_job",id).put("p_accept",false))},enabled=!busy){Text("Decline")}}}else{Button(onClick={job=id}){Text("Review candidates")};TextButton(onClick={act("sera_hiring_respond",JSONObject().put("p_job",id).put("p_accept",false))},enabled=!busy){Text("Leave team")}}}}};item{Text("Invite colleagues to your vacancies from the SERA website hiring team page.")}}else{items(candidates,key={it.getString("id")}){c->ElevatedCard(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(c.optString("candidate_name"),style=MaterialTheme.typography.titleMedium);Text(c.optString("headline"));Text(c.optString("skills"));Text(c.optString("stage"));Button(onClick={review=c.getString("id");note=c.optString("feedback")}){Text("Review feedback")}}}}}}
 }
 review?.let{id->SeraSignatureSheet(onDismissRequest={if(!busy)review=null}){Column(Modifier.fillMaxWidth().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Candidate feedback",style=MaterialTheme.typography.headlineSmall);OutlinedTextField(note,{note=it.take(4000)},label={Text("Evidence-based feedback")},minLines=3,modifier=Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={act("sera_hiring_submit_feedback",JSONObject().put("p_application",id).put("p_feedback",note))},enabled=!busy&&note.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Save feedback")}}}}
}
