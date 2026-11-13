package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun linkedJobWrite(token:String,table:String,method:String,body:JSONObject?=null,query:String=""):Int {
    val conn=(URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+table+query).openConnection() as HttpURLConnection)
    return try{
        conn.requestMethod=method;conn.connectTimeout=10000;conn.readTimeout=15000
        conn.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Authorization","Bearer "+token)
        conn.setRequestProperty("Content-Type","application/json")
        if(body!=null){conn.doOutput=true;conn.outputStream.use{it.write(body.toString().toByteArray())}}
        conn.responseCode
    }finally{conn.disconnect()}
}
private fun linkedJobApply(token:String,userId:String,id:String,note:String,attachCv:Boolean):String=try{
    val profile=searchRows(token,"profiles",mapOf("select" to "full_name,headline,skills,experience,education","id" to "eq."+userId,"limit" to "1")).optJSONObject(0)
    if(profile==null)"Complete your professional profile before applying."
    else{
        val payload=JSONObject().put("job_id",id).put("candidate_id",userId).put("cover_note",note.trim())
            .put("candidate_name",profile.optString("full_name")).put("candidate_headline",profile.optString("headline"))
            .put("skills",profile.optString("skills").takeUnless{it=="null"}.orEmpty()).put("experience",profile.optString("experience")).put("education",profile.optString("education"))
        if(attachCv){
            val cv=searchRows(token,"account_assets",mapOf("select" to "cv_path,cv_name","user_id" to "eq."+userId,"limit" to "1")).optJSONObject(0)
            if(cv==null||cv.optString("cv_path").isBlank()||cv.isNull("cv_path"))throw IllegalStateException("No CV found.")
            payload.put("cv_path",cv.optString("cv_path")).put("cv_name",cv.optString("cv_name","CV.pdf"))
        }
        when(val code=linkedJobWrite(token,"applications","POST",payload)){
            in 200..299->""
            409->"You already applied for this job."
            else->"Application not submitted. The listing may be closed or your account may need verification (HTTP "+code+")."
        }
    }
}catch(e:Exception){if(e.message=="No CV found.")"Upload a CV in your profile or untick Attach CV." else "Unable to submit your application. Please retry."}

@Composable internal fun SeraLinkedJobDetail(token:String,userId:String,id:String,onClose:()->Unit){
    var job by remember(id){mutableStateOf<JSONObject?>(null)}
    var profile by remember(id){mutableStateOf<JSONObject?>(null)}
    var saved by remember(id){mutableStateOf(false)}
    var applied by remember(id){mutableStateOf(false)}
    var loading by remember(id){mutableStateOf(true)}
    var error by remember(id){mutableStateOf("")}
    var reload by remember(id){mutableIntStateOf(0)}
    var busy by remember(id){mutableStateOf(false)}
    var note by remember(id){mutableStateOf("")}
    var consent by remember(id){mutableStateOf(false)}
    var attachCv by remember(id){mutableStateOf(false)}
    var thread by remember(id){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(token,id,reload){
        loading=true;error=""
        try{
            val data=withContext(Dispatchers.IO){
                val j=searchRows(token,"jobs",mapOf("select" to "id,title,company,location,work_type,description,requirements,status,employer_id","id" to "eq."+id,"limit" to "1")).optJSONObject(0)
                val p=searchRows(token,"profiles",mapOf("select" to "full_name,headline,skills,experience,education","id" to "eq."+userId,"limit" to "1")).optJSONObject(0)
                val a=searchRows(token,"applications",mapOf("select" to "id","job_id" to "eq."+id,"candidate_id" to "eq."+userId,"limit" to "1")).length()>0
                val s=searchRows(token,"saved_jobs",mapOf("select" to "job_id","job_id" to "eq."+id,"user_id" to "eq."+userId,"limit" to "1")).length()>0
                Triple(j,p,a to s)
            }
            job=data.first;profile=data.second;applied=data.third.first;saved=data.third.second
            if(job==null)error="This job is no longer available."
        }catch(_:Exception){error="Unable to load this job. Please retry."}
        loading=false
    }
    if(thread!=null){androidx.activity.compose.BackHandler{thread=null};SeraCompanyInbox(token,userId,thread,onBack={thread=null});return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick=onClose){Text("Back")}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);if(job==null)TextButton(onClick={reload++}){Text("Retry")}}
        job?.let{j->
            Text(j.optString("title"),style=MaterialTheme.typography.headlineMedium)
            Text(j.optString("company"),color=MaterialTheme.colorScheme.primary)
            Text(j.optString("location")+" · "+j.optString("work_type"))
            Text("Employer-provided listing. Never pay to apply.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!busy,onClick={busy=true;scope.launch{
                val ok=withContext(Dispatchers.IO){runCatching{if(saved)linkedJobWrite(token,"saved_jobs","DELETE",query="?job_id=eq."+id+"&user_id=eq."+userId) else linkedJobWrite(token,"saved_jobs","POST",JSONObject().put("job_id",id).put("user_id",userId))}.getOrDefault(0)}
                busy=false;if(ok in 200..299)saved=!saved else error="Could not update saved job."
            }}){Text(if(saved)"Remove saved job" else "Save job")}
            HorizontalDivider()
            Text("About the role",style=MaterialTheme.typography.titleLarge);Text(j.optString("description"))
            Text("Requirements",style=MaterialTheme.typography.titleLarge);Text(j.optString("requirements"))
            if(j.optString("employer_id")!=userId){
                FilledTonalButton(enabled=!busy,onClick={busy=true;scope.launch{val result=withContext(Dispatchers.IO){startCompanyJobChat(token,id)};busy=false;if(result.first!=null)thread=result.first else error=result.second}},modifier=Modifier.fillMaxWidth()){Text("Message company")}
                if(applied)Text("Application submitted",color=MaterialTheme.colorScheme.primary)
                else if(j.optString("status")=="open"){
                    HorizontalDivider();Text("Apply for this job",style=MaterialTheme.typography.titleLarge)
                    Text("Share your profile and a cover note with this employer. Review your information before submitting.")
                    profile?.let{p->Text(listOf("full_name","headline","skills","experience","education").map{p.optString(it)}.filter{it.isNotBlank()}.joinToString("\n"),style=MaterialTheme.typography.bodyMedium)}
                    OutlinedTextField(note,{note=it.take(4000)},label={Text("Cover note (20–4,000 characters)")},modifier=Modifier.fillMaxWidth(),minLines=4)
                    Row{Checkbox(checked=consent,onCheckedChange={consent=it});Text("Share my profile details with this employer",Modifier.padding(top=12.dp))}
                    Row{Checkbox(checked=attachCv,onCheckedChange={attachCv=it});Text("Attach my uploaded CV",Modifier.padding(top=12.dp))}
                    Button(enabled=!busy&&consent&&note.trim().length>=20&&profile!=null,onClick={busy=true;error="";scope.launch{
                        val result=withContext(Dispatchers.IO){linkedJobApply(token,userId,id,note,attachCv)}
                        busy=false;if(result.isBlank()){applied=true;note=""}else error=result
                    }},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Please wait…" else "Submit application")}
                }else Text("This job is closed.")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
