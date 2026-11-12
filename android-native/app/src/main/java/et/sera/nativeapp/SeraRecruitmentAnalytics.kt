package et.sera.nativeapp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
@Composable internal fun SeraRecruitmentAnalytics(token:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var days by remember{mutableIntStateOf(30)}
 var data by remember(token){mutableStateOf<JSONObject?>(null)}
 var loading by remember{mutableStateOf(true)}
 var error by remember{mutableStateOf("")}
 var retry by remember{mutableIntStateOf(0)}
 androidx.activity.compose.BackHandler(onBack=onBack)
 LaunchedEffect(token,days,retry){loading=true;data=null;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_recruitment_analytics",JSONObject().put("p_days",days))};data=runCatching{JSONObject(r.first?:"null")}.getOrNull();error=if(data==null)r.second.ifBlank{"Unable to load analytics."}else "";loading=false}
 val d=data
 val teal=Color(0xFF087E83)
 Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 TextButton(onClick=onBack){Text("Back to hiring")}
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF123A40),teal))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
 Text("Recruitment analytics",color=Color.White,style=MaterialTheme.typography.headlineSmall)
 Text("Understand your hiring activity.",color=Color(0xFFCBEBE7))
 if(d!=null)Text(d.optInt("total").toString()+" applications",color=Color.White,style=MaterialTheme.typography.headlineMedium)
 }
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(7,30,90).forEach{n->FilterChip(selected=days==n,onClick={days=n},label={Text("$n days")})}}
 Text("Application cohorts · Addis Ababa time",style=MaterialTheme.typography.labelSmall)
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text("Retry")}}
 if(d!=null){
 val total=d.optInt("total")
 val metrics=listOf("Interview accepted / completed" to d.optInt("interviewed"),"Completed interviews" to d.optInt("completed"),"Currently at offer" to d.optInt("offers"))
 metrics.forEach{(label,n)->ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(label,style=MaterialTheme.typography.labelLarge);Text(n.toString(),style=MaterialTheme.typography.headlineMedium,color=teal);Text(if(total==0)"No applications in this period" else "${(100.0*n/total).toInt()}% of applications",style=MaterialTheme.typography.bodySmall)}}}
 Text("Application trend",style=MaterialTheme.typography.titleLarge)
 val daily=spaceRows(d.optJSONArray("daily")?.toString());val max=daily.maxOfOrNull{it.optInt("count")}?.coerceAtLeast(1)?:1
 ElevatedCard(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Row(Modifier.fillMaxWidth().height(100.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(2.dp)){daily.forEach{v->val ratio=v.optInt("count").toFloat()/max;val animated by animateFloatAsState(ratio,tween(300),label="daily applications");Box(Modifier.weight(1f).fillMaxHeight(animated.coerceAtLeast(.02f)).clip(RoundedCornerShape(3.dp)).background(teal))}}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(daily.firstOrNull()?.optString("day").orEmpty(),style=MaterialTheme.typography.labelSmall);Text(daily.lastOrNull()?.optString("day").orEmpty(),style=MaterialTheme.typography.labelSmall)}
 Text("Peak: $max applications per day".replace("Peak: 1 applications",if(daily.all{it.optInt("count")==0})"Peak: 0 applications" else "Peak: 1 application"),style=MaterialTheme.typography.bodySmall)
 }}
 Text("Current pipeline stages",style=MaterialTheme.typography.titleLarge)
 val stages=d.optJSONObject("stages")?:JSONObject()
 listOf("applied" to "Submitted","screened" to "In review","interview" to "Interview","offer" to "Offer","rejected" to "Not selected","withdrawn" to "Withdrawn").forEach{(key,label)->val n=stages.optInt(key);Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Text(n.toString(),color=teal)};LinearProgressIndicator(progress={if(total>0)n.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth().height(6.dp),color=teal)}
 Text("Vacancy performance",style=MaterialTheme.typography.titleLarge)
 val jobs=spaceRows(d.optJSONArray("jobs")?.toString())
 jobs.forEach{j->ElevatedCard(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(j.optString("title"),style=MaterialTheme.typography.titleMedium);Text(j.optInt("applicants").toString()+" applicants · "+j.optInt("offers")+" offers",color=teal);Text(j.optInt("interviewed").toString()+" accepted/completed interviews",style=MaterialTheme.typography.bodySmall)}}}
 if(jobs.isEmpty())Text("Publish a vacancy to see its performance.")
 Text("Stage totals are current snapshots, not historical conversion rates. Interview figures count distinct applicants, including interviews scheduled outside the application period.",style=MaterialTheme.typography.bodySmall)
 }
 }
}
