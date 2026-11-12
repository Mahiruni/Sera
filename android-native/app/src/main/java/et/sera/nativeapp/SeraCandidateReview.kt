package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
@Composable internal fun SeraCandidateReview(token:String,id:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var data by remember(token,id){mutableStateOf<JSONObject?>(null)};var note by remember(id){mutableStateOf("")};var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var busy by remember{mutableStateOf(false)};var reload by remember{mutableIntStateOf(0)};var menu by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 fun act(rpc:String,p:JSONObject){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,rpc,p)};busy=false;error=r.second;if(r.first!=null)reload++}}
 androidx.activity.compose.BackHandler(onBack=onBack)
 LaunchedEffect(token,id,reload){loading=true;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_candidate_review",JSONObject().put("p_application",id))};data=runCatching{JSONObject(r.first?:"null")}.getOrNull();note=data?.optString("notes").orEmpty();error=if(data==null)r.second.ifBlank{"Application unavailable."}else "";loading=false}
 val d=data
 Column(modifier.fillMaxSize().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 TextButton(onClick=onBack){Text("Back to hiring")}
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 if(d!=null){LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(d.optString("name"),style=MaterialTheme.typography.headlineMedium);Text(d.optString("job_title"),style=MaterialTheme.typography.titleMedium);Text(d.optString("headline"));Text(d.optString("stage"),color=MaterialTheme.colorScheme.primary)}}}
 item{if(d.optString("stage")!="withdrawn"){Box{Button(onClick={menu=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Update recruitment stage")};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){listOf("screened" to "In review","interview" to "Interview","offer" to "Offer","rejected" to "Not selected").forEach{(v,label)->DropdownMenuItem(text={Text(label)},onClick={menu=false;act("sera_review_stage",JSONObject().put("p_application",id).put("p_stage",v).put("p_expected",d.optString("stage")))})}}}}}
 listOf("Introduction" to "cover_note","Skills" to "skills","Experience" to "experience","Education" to "education").forEach{(label,key)->item{ElevatedCard(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,style=MaterialTheme.typography.titleMedium);Text(d.optString(key).ifBlank{"Not provided"})}}}}
 item{Text(if(d.optBoolean("has_cv"))"CV attached · Open the application on the website to download securely." else "No CV available.",style=MaterialTheme.typography.bodySmall)}
 item{SeraInterviewPanel(token,id)}
 item{Text("Private recruitment notes",style=MaterialTheme.typography.titleLarge);Text("Only the job owner can read these notes.",style=MaterialTheme.typography.bodySmall);OutlinedTextField(note,{note=it.take(4000)},label={Text("Review notes")},modifier=Modifier.fillMaxWidth(),minLines=3);Button(onClick={act("sera_save_recruitment_note",JSONObject().put("p_application",id).put("p_body",note))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Saving…" else "Save notes")}}
 }}
 }
}
