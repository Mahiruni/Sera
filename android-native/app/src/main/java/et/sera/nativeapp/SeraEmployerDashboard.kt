package et.sera.nativeapp
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
@Composable internal fun SeraEmployerDashboard(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var data by remember(token,userId){mutableStateOf<JSONObject?>(null)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var stage by remember{mutableStateOf("all")};var inbox by remember{mutableStateOf(false)}
 if(inbox){SeraCompanyInbox(token,userId,onBack={inbox=false});return}
 androidx.activity.compose.BackHandler(onBack=onBack)
 LaunchedEffect(token,userId,refresh){loading=true;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_employer_dashboard")};data=runCatching{JSONObject(r.first?:"null")}.getOrNull();error=if(data==null)r.second.ifBlank{"Unable to load hiring activity."}else "";loading=false}
 val d=data;val stages=d?.optJSONObject("stages")?:JSONObject();val keys=listOf("applied","screened","interview","offer","rejected","withdrawn");val labels=listOf("Submitted","In review","Interview","Offer","Not selected","Withdrawn")
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 TextButton(onClick=onBack){Text("Back")}
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF123A40),Color(0xFF087E83)))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Hiring workspace",color=Color.White,style=MaterialTheme.typography.headlineSmall);Text("Build your next great team.",color=Color(0xFFCBEBE7));if(d!=null)Text("${d.optInt("open_jobs")} open roles · ${keys.sumOf{stages.optInt(it)}} applications",color=Color.White,style=MaterialTheme.typography.titleMedium)}
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++}){Text("Retry")}}
 if(d!=null){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilledTonalButton(onClick={inbox=true}){Text("Company inbox")};TextButton(onClick={refresh++}){Text("Refresh")}}
 LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){
 item{Text("Hiring pipeline",style=MaterialTheme.typography.titleLarge)}
 item{Column(verticalArrangement=Arrangement.spacedBy(6.dp)){for(i in keys.indices step 2){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(j in i..minOf(i+1,keys.lastIndex)){ElevatedCard(onClick={stage=keys[j]},modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){Text(stages.optInt(keys[j]).toString(),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary);Text(labels[j],style=MaterialTheme.typography.bodySmall)}}}}}}}
 item{Row{Text("Recent applicants",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick={stage="all"}){Text("All")}}}
 val apps=d.optJSONArray("applications");val rows=(0 until (apps?.length()?:0)).mapNotNull{apps?.optJSONObject(it)}.filter{stage=="all"||it.optString("stage")==stage}
 items(rows,key={it.getString("id")}){p->ElevatedCard(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(p.optString("candidate_name"),style=MaterialTheme.typography.titleMedium);Text(p.optString("job_title"));Text(labels.getOrElse(keys.indexOf(p.optString("stage"))){"Status unavailable"},color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)}}}
 if(rows.isEmpty())item{Text("No recent applicants in this stage.")}
 item{Text("Your postings",style=MaterialTheme.typography.titleLarge)}
 val jobs=d.optJSONArray("jobs");items((0 until (jobs?.length()?:0)).mapNotNull{jobs?.optJSONObject(it)},key={it.getString("id")}){j->ElevatedCard(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(j.optString("title"),style=MaterialTheme.typography.titleMedium);Text(j.optString("company"));Text(j.optString("status")+" · "+j.optInt("applicants")+" applicants",style=MaterialTheme.typography.bodySmall)}}}
 }}}
}
