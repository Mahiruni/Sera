package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val companyTeal=Color(0xFF087E83)
private fun companyRequest(token:String,path:String,payload:JSONObject?=null): Pair<String?,String> {
    var c:HttpURLConnection?=null
    return try {
        c=(URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path).openConnection() as HttpURLConnection).apply {
            connectTimeout=12000;readTimeout=15000;requestMethod=if(payload==null)"GET" else "POST"
            setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);setRequestProperty("Authorization","Bearer $token")
            if(payload!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}
        }
        if(payload!=null)c.outputStream.use{it.write(payload.toString().toByteArray())}
        if(c.responseCode in 200..299) (c.inputStream.bufferedReader().use{it.readText()}) to "" else null to "Company messaging request failed. Please retry."
    } catch(_:Exception){null to "Connection interrupted. Please retry."} finally{c?.disconnect()}
}
internal fun startCompanyJobChat(token:String,job:String):Pair<String?,String>{
    val (raw,error)=companyRequest(token,"rpc/sera_start_job_chat",JSONObject().put("p_job",job))
    return raw?.trim()?.trim('"') to error
}
private data class CompanyThread(val id:String,val name:String,val job:String,val employer:Boolean)
private data class CompanyMessage(val id:String,val sender:String,val body:String,val automatic:Boolean)
@Composable
internal fun SeraCompanyInbox(token:String,userId:String,initialThread:String?=null,onBack:()->Unit) {
    var threads by remember{mutableStateOf<List<CompanyThread>>(emptyList())}
    var selected by remember{mutableStateOf<CompanyThread?>(null)}
    var messages by remember{mutableStateOf<List<CompanyMessage>>(emptyList())}
    var draft by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var sending by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(true)}
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val listState=rememberLazyListState()
    androidx.activity.compose.BackHandler { if(selected!=null && initialThread==null){selected=null;messages=emptyList()}else onBack() }
    LaunchedEffect(token,userId,initialThread){
        val (raw,e)=withContext(Dispatchers.IO){companyRequest(token,"company_job_threads?select=id,company_name,job_title,employer_id&order=last_message_at.desc&limit=100")}
        error=e
        if(raw!=null)runCatching{
            val arr=JSONArray(raw)
            threads=(0 until arr.length()).map{val r=arr.getJSONObject(it);CompanyThread(r.getString("id"),r.getString("company_name"),r.getString("job_title"),r.getString("employer_id")==userId)}
            if(initialThread!=null)selected=threads.firstOrNull{it.id==initialThread}
        }.onFailure{error="Unable to read company conversations."}
        loading=false
    }
    LaunchedEffect(token,lifecycle){
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(true){withContext(Dispatchers.IO){companyRequest(token,"rpc/sera_company_inbox_heartbeat",JSONObject())};delay(25000)}
        }
    }
    LaunchedEffect(token,selected?.id,lifecycle){
        val thread=selected?:return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(true){
                val (raw,e)=withContext(Dispatchers.IO){companyRequest(token,"company_job_messages?select=id,sender_id,body,automatic&thread_id=eq.${thread.id}&order=created_at.desc,id.desc&limit=100")}
                if(raw!=null)runCatching{val arr=JSONArray(raw);messages=(0 until arr.length()).map{val r=arr.getJSONObject(it);CompanyMessage(r.getString("id"),r.optString("sender_id"),r.getString("body"),r.optBoolean("automatic"))}.asReversed()}
                error=e
                delay(3500)
            }
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id){if(messages.isNotEmpty())listState.scrollToItem(messages.lastIndex)}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(selected!=null && initialThread==null){selected=null;messages=emptyList();draft=""}else onBack()}){Icon(Icons.Outlined.ArrowBack,"Back")}
            Column(Modifier.weight(1f)){
                Text(selected?.name?:"Company inbox",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color(0xFF183B40))
                Text(selected?.job?:"Job inquiries and recruitment conversations",fontSize=12.sp,color=Color(0xFF607478))
            }
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(selected==null){
            if(!loading && threads.isEmpty())Text("No job conversations yet. Open a job listing to contact its employer.",color=Color(0xFF607478))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
                items(threads,key={it.id}){thread->
                    ElevatedCard(onClick={selected=thread;messages=emptyList();draft=""},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(thread.name,fontWeight=FontWeight.Bold);Text(thread.job,color=companyTeal)
                            Text(if(thread.employer)"Recruitment inquiry" else "Company conversation",fontSize=12.sp)
                        }
                    }
                }
            }
        }else{
            val thread=selected!!
            LazyColumn(Modifier.weight(1f),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(messages,key={it.id}){m->
                    val own=m.sender==userId
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(own)Arrangement.End else Arrangement.Start){
                        Surface(shape=RoundedCornerShape(18.dp),color=if(own)companyTeal else Color(0xFFF0F7F6),modifier=Modifier.widthIn(max=310.dp)){
                            Column(Modifier.padding(12.dp)){
                                if(m.automatic)Text("Automatic company reply",fontSize=11.sp,fontWeight=FontWeight.Bold,color=companyTeal)
                                Text(m.body,color=if(own)Color.White else Color(0xFF183B40))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().imePadding(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(draft,{if(it.length<=4000)draft=it},modifier=Modifier.weight(1f),placeholder={Text("Write a message…")},shape=RoundedCornerShape(20.dp),maxLines=4,enabled=!sending)
                Button(onClick={
                    val body=draft.trim();sending=true
                    scope.launch{
                        val (raw,e)=withContext(Dispatchers.IO){companyRequest(token,"rpc/sera_send_job_message",JSONObject().put("p_thread",thread.id).put("p_body",body))}
                        if(raw!=null){draft=""; withContext(Dispatchers.IO){SeraPush.dispatch(token,"company_message",raw.trim().trim('"'))}} else error=e
                        sending=false
                    }
                },enabled=draft.isNotBlank()&&!sending,contentPadding=PaddingValues(14.dp)){if(sending)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Color.White)else Icon(Icons.Outlined.Send,"Send")}
            }
        }
    }
}
