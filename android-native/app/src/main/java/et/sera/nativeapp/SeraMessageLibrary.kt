package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal fun seraCanEdit(created:String,now:Instant=Instant.now()):Boolean=runCatching{
 val sent=Instant.parse(created);!sent.isAfter(now)&&sent.plusSeconds(900).isAfter(now)
}.getOrDefault(false)
internal data class SeraLibraryEntry(val id:String,val sender:String,val body:String,val at:String,val edited:Boolean)
private fun libraryPage(token:String,thread:String,mode:String,query:String="",before:SeraLibraryEntry?=null):Pair<List<SeraLibraryEntry>?,String>{
 val request=JSONObject().put("p_conversation",thread).put("p_mode",mode).put("p_query",query).put("p_limit",30)
 if(before!=null)request.put("p_before",before.at).put("p_before_id",before.id)
 val (raw,error)=chatRpc(token,"sera_message_library",request)
 if(raw==null)return null to error
 return runCatching{val rows=JSONArray(raw);(0 until rows.length()).map{val x=rows.getJSONObject(it);SeraLibraryEntry(x.getString("id"),x.getString("sender_id"),x.optString("body"),x.getString("created_at"),!x.isNull("edited_at"))} to ""}.getOrElse{null to "Could not load messages."}
}
@Composable internal fun SeraMessageLibraryBar(token:String,thread:String,revision:Int,accent:Color,onOpen:(String)->Unit){
 val pin by produceState<SeraLibraryEntry?>(null,token,thread,revision){
  value=null
  while(true){val result=withContext(Dispatchers.IO){libraryPage(token,thread,"pin")};if(result.first!=null)value=result.first?.firstOrNull();delay(15000)}
 }
 Column(Modifier.fillMaxWidth()){
  pin?.let{entry->TextButton(onClick={onOpen("pin")},modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth()){Text("Pinned message",color=accent,style=MaterialTheme.typography.labelMedium);Text(entry.body,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurface)}}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={onOpen("star")}){Text("Starred messages")};TextButton(onClick={onOpen("search")}){Text("Search history")}}
 }
}
@Composable internal fun SeraMessageLibraryDialog(token:String,thread:String,userId:String,mode:String,onDismiss:()->Unit,onJump:(String)->Boolean){
 var query by remember{mutableStateOf("")}
 var rows by remember{mutableStateOf<List<SeraLibraryEntry>>(emptyList())}
 var loading by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var more by remember{mutableStateOf(false)}
 var generation by remember{mutableIntStateOf(0)}
 val scope=rememberCoroutineScope()
 LaunchedEffect(token,thread,mode,query,generation){
  rows=emptyList();more=false;error=""
  if(mode=="search"&&query.isBlank()){loading=false;return@LaunchedEffect}
  loading=true
  if(mode=="search")delay(300)
  val r=withContext(Dispatchers.IO){libraryPage(token,thread,mode,query.trim())}
  rows=r.first.orEmpty();more=rows.size==30;error=r.second;loading=false
 }
 AlertDialog(onDismissRequest=onDismiss,title={Text(when(mode){"star"->"Starred messages";"pin"->"Pinned message";else->"Search conversation"})},text={
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   if(mode=="search")OutlinedTextField(query,{query=it.take(120)},label={Text("Search entire history")},singleLine=true,modifier=Modifier.fillMaxWidth())
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
   if(!loading&&rows.isEmpty()&&error.isBlank())Text(if(mode=="search"&&query.isBlank())"Enter a word or phrase." else "No messages found.")
   LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    items(rows,key={it.id}){entry->Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.fillMaxWidth().padding(10.dp)){
     Text(if(entry.sender==userId)"You" else "Contact",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
     Text(entry.body,style=MaterialTheme.typography.bodyMedium)
     Text(entry.at.take(16).replace("T"," ")+(if(entry.edited)" · Edited" else ""),style=MaterialTheme.typography.labelSmall)
     TextButton(onClick={if(!onJump(entry.id))error="This older message is available here, but is outside the loaded chat timeline."}){Text("Go to message")}
    }}}
    if(more)item{TextButton(enabled=!loading,onClick={
     val cursor=rows.lastOrNull()?:return@TextButton;val q=query;val g=generation
     loading=true;scope.launch{val r=withContext(Dispatchers.IO){libraryPage(token,thread,mode,q.trim(),cursor)};if(query==q&&generation==g){if(r.first!=null){rows=(rows+r.first.orEmpty()).distinctBy{it.id};more=r.first?.size==30}else error=r.second;loading=false}}
    }){Text("Load more")}}
   }
  }
 },confirmButton={TextButton(onClick=onDismiss){Text("Done")}},dismissButton={if(error.isNotBlank())TextButton(onClick={generation++}){Text("Refresh")}})
}
@Composable internal fun SeraEditMessageDialog(token:String,message:NativeMessage,onDismiss:()->Unit,onSaved:()->Unit){
 var body by remember(message.id){mutableStateOf(message.body)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Edit message")},text={Column{
  OutlinedTextField(body,{if(it.length<=SERA_MESSAGE_LIMIT)body=it},minLines=2,maxLines=5,enabled=!busy,label={Text("Message")})
  Text("Edited messages are labeled. The edit window is 15 minutes.",style=MaterialTheme.typography.bodySmall)
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 }},confirmButton={TextButton(enabled=!busy&&body.trim().isNotEmpty()&&body.trim()!=message.body,onClick={busy=true;scope.launch{
  val r=withContext(Dispatchers.IO){chatRpc(token,"sera_edit_message",JSONObject().put("p_message",message.id).put("p_body",body))}
  busy=false;if(r.first!=null)onSaved()else error="Could not edit. Check your connection and the 15-minute edit window."
 }}){Text(if(busy)"Saving…" else "Save changes")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}})
}
@Composable internal fun SeraMessageLibraryActions(token:String,thread:String,message:NativeMessage,userId:String,open:Boolean,onClose:()->Unit,onChanged:()->Unit,onError:(String)->Unit){
 val scope=rememberCoroutineScope()
 var starred by remember(message.id){mutableStateOf<Boolean?>(null)}
 var pinned by remember(message.id){mutableStateOf<Boolean?>(null)}
 LaunchedEffect(open,message.id,token){if(open){
  starred=null;pinned=null
  val flags=withContext(Dispatchers.IO){runCatching{
   val stars=searchRows(token,"sera_starred_messages",mapOf("select" to "message_id","user_id" to "eq."+userId,"message_id" to "eq."+message.id,"limit" to "1"))
   val pins=searchRows(token,"sera_pinned_messages",mapOf("select" to "message_id","conversation_id" to "eq."+thread,"message_id" to "eq."+message.id,"limit" to "1"))
   (stars.length()>0) to (pins.length()>0)
  }.getOrNull()}
  starred=flags?.first;pinned=flags?.second
 }}
 fun change(rpc:String,payload:JSONObject){scope.launch{val r=withContext(Dispatchers.IO){chatRpc(token,rpc,payload)};if(r.first!=null){onChanged();onClose()}else{onError(r.second);onClose()}}}
 DropdownMenuItem(text={Text(if(starred==true)"Remove star" else "Star message")},enabled=starred!=null,onClick={change("sera_set_message_star",JSONObject().put("p_message",message.id).put("p_star",starred!=true))})
 DropdownMenuItem(text={Text(if(pinned==true)"Unpin message" else "Pin for everyone")},enabled=pinned!=null,onClick={change("sera_set_message_pin",JSONObject().put("p_conversation",thread).put("p_message",message.id).put("p_pin",pinned!=true))})
}
