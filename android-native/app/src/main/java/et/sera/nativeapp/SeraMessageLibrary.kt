package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.rememberLazyListState
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
internal data class SeraLibraryEntry(val id:String,val sender:String,val body:String,val at:String,val edited:Boolean,val conversationId:String)
private fun libraryPage(token:String,thread:String?,mode:String,query:String="",before:SeraLibraryEntry?=null,sender:String="all",from:String="",until:String=""):Pair<List<SeraLibraryEntry>?,String>{
 val request=JSONObject().put("p_conversation",thread?:JSONObject.NULL).put("p_mode",mode).put("p_query",query).put("p_limit",30).put("p_sender",sender)
 if(from.isNotBlank())request.put("p_from",java.time.LocalDate.parse(from).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString())
 if(until.isNotBlank())request.put("p_until",java.time.LocalDate.parse(until).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString())
 if(before!=null)request.put("p_before",before.at).put("p_before_id",before.id)
 val (raw,error)=chatRpc(token,"sera_message_library_v2",request)
 if(raw==null)return null to error
 return runCatching{val rows=JSONArray(raw);(0 until rows.length()).map{val x=rows.getJSONObject(it);SeraLibraryEntry(x.getString("id"),x.getString("sender_id"),x.optString("body"),x.getString("created_at"),!x.isNull("edited_at"),x.getString("conversation_id"))} to ""}.getOrElse{null to "Could not load messages."}
}
@Composable internal fun SeraMessageLibraryBar(token:String,thread:String,revision:Int,accent:Color,onOpen:(String)->Unit,onScheduled:()->Unit={}){
 val palette=seraChatColors()
 val pins by produceState<List<SeraLibraryEntry>>(emptyList(),token,thread,revision){
  value=emptyList()
  while(true){val result=withContext(Dispatchers.IO){libraryPage(token,thread,"pin")};if(result.first!=null)value=result.first.orEmpty();delay(15000)}
 }
 Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
  pins.firstOrNull()?.let{entry->
   Surface(color=palette.quote,border=BorderStroke(.5.dp,palette.border),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().clickable{onOpen("pin")}){
    Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
     Icon(Icons.Outlined.PushPin,null,tint=accent,modifier=Modifier.size(20.dp))
     Column(Modifier.weight(1f)){Text(if(pins.size==1)"Pinned message" else "${pins.size}${if(pins.size==30)"+" else ""} pinned messages",color=accent,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold);Text(entry.body,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=palette.ink)}
     Icon(Icons.Outlined.ChevronRight,"View pinned messages",tint=accent)
    }
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
   TextButton(onClick=onScheduled){Text("Scheduled")}
   TextButton(onClick={onOpen("star")}){Icon(Icons.Outlined.StarBorder,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Starred")}
  }
 }
}
@Composable internal fun SeraMessageLibraryDialog(token:String,thread:String?,userId:String,mode:String,onDismiss:()->Unit,onJump:(SeraLibraryEntry)->Unit){
 val palette=seraChatColors()
 var query by remember{mutableStateOf("")}
 var sender by remember{mutableStateOf("all")}
 var from by remember{mutableStateOf("")}
 var until by remember{mutableStateOf("")}
 val datesValid=runCatching{val start=from.takeIf{it.isNotBlank()}?.let(java.time.LocalDate::parse);val end=until.takeIf{it.isNotBlank()}?.let(java.time.LocalDate::parse);(start==null||end==null||!start.isAfter(end))}.getOrDefault(false)
 var rows by remember{mutableStateOf<List<SeraLibraryEntry>>(emptyList())}
 var loading by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var more by remember{mutableStateOf(false)}
 var generation by remember{mutableIntStateOf(0)}
 val scope=rememberCoroutineScope()
 LaunchedEffect(token,thread,mode,query,generation,sender,from,until){
  rows=emptyList();more=false;error=""
  if(!datesValid){loading=false;error="Enter valid dates (YYYY-MM-DD) in chronological order.";return@LaunchedEffect}
  if(mode=="search"&&query.isBlank()){loading=false;return@LaunchedEffect}
  loading=true
  if(mode=="search")delay(300)
  val r=withContext(Dispatchers.IO){libraryPage(token,thread,mode,query.trim(),sender=sender,from=from,until=until)}
  rows=r.first.orEmpty();more=rows.size==30;error=r.second;loading=false
 }
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=palette.canvas){
  Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){
    IconButton(onClick=onDismiss){Icon(Icons.Outlined.ArrowBack,"Back")}
    Column(Modifier.weight(1f)){Text(when(mode){"star"->"Starred messages";"pin"->"Pinned messages";else->"Search conversation"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(when(mode){"star"->"Private to you";"pin"->"Shared with participants";else->"Entire conversation history"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }
   if(mode=="search"){
    OutlinedTextField(query,{query=it.take(120)},label={Text("Find a message")},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"Clear search")}},singleLine=true,shape=RoundedCornerShape(20.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=palette.surface,focusedContainerColor=palette.surface,unfocusedBorderColor=palette.border),modifier=Modifier.fillMaxWidth().testTag("sera_library_search"))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("all" to "Everyone","you" to "You","contact" to "Contact").forEach{(key,label)->FilterChip(selected=sender==key,onClick={sender=key},label={Text(label)})}}
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(from,{from=it.take(10)},label={Text("From date")},placeholder={Text("YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(until,{until=it.take(10)},label={Text("To date")},placeholder={Text("YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))}
   }
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
   if(!loading&&rows.isEmpty()&&error.isBlank())Text(if(mode=="search"&&query.isBlank())"Enter a word or phrase." else "No messages found.")
   LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
    items(rows,key={it.id}){entry->SeraLibraryMessageCard(entry,userId,mode,query,onOpen={onJump(entry)})}
    if(more)item{TextButton(enabled=!loading,onClick={
     val cursor=rows.lastOrNull()?:return@TextButton;val q=query;val g=generation;val who=sender;val start=from;val end=until
     loading=true;scope.launch{val r=withContext(Dispatchers.IO){libraryPage(token,thread,mode,q.trim(),cursor,who,start,end)};if(query==q&&generation==g&&sender==who&&from==start&&until==end){if(r.first!=null){rows=(rows+r.first.orEmpty()).distinctBy{it.id};more=r.first?.size==30}else error=r.second;loading=false}}
    }){Text("Load more")}}
   }
   if(error.isNotBlank())TextButton(onClick={generation++}){Text("Retry")}
  }
  }
 }
}
/** One calm surface for pinned, starred and search results; the whole card opens its message. */
@Composable internal fun SeraLibraryMessageCard(entry:SeraLibraryEntry,userId:String,mode:String,query:String,onOpen:()->Unit){
 val palette=seraChatColors()
 val timestamp=remember(entry.at){runCatching{Instant.parse(entry.at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))}.getOrDefault(entry.at.take(16))}
 Surface(shape=RoundedCornerShape(20.dp),color=palette.surface,contentColor=palette.ink,border=BorderStroke(.5.dp,palette.border),
  modifier=Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel="Go to message",onClick=onOpen)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
    Icon(when(mode){"star"->Icons.Outlined.StarBorder;"pin"->Icons.Outlined.PushPin;else->Icons.Outlined.Search},null,tint=palette.accent,modifier=Modifier.size(16.dp))
    Text(if(entry.sender==userId)"You" else "Contact",style=MaterialTheme.typography.labelMedium,color=palette.accent,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
    Icon(Icons.Outlined.ChevronRight,null,tint=palette.muted,modifier=Modifier.size(18.dp))
   }
   Text(seraHighlightedMessage(entry.body,if(mode=="search")query.trim()else "",palette.selection,palette.ink),style=seraChatBody(MaterialTheme.typography.bodyLarge),maxLines=4,overflow=TextOverflow.Ellipsis)
   Text(timestamp+(if(entry.edited)" · Edited" else ""),style=seraChatMeta(MaterialTheme.typography.labelSmall),color=palette.muted)
  }
 }
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
 DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.StarBorder,null)},text={Text(if(starred==true)"Remove star" else "Star message")},enabled=starred!=null,onClick={change("sera_set_message_star",JSONObject().put("p_message",message.id).put("p_star",starred!=true))})
 DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.PushPin,null)},text={Text(if(pinned==true)"Unpin message" else "Pin for everyone")},enabled=pinned!=null,onClick={change("sera_set_message_pin",JSONObject().put("p_conversation",thread).put("p_message",message.id).put("p_pin",pinned!=true))})
}

internal fun seraHighlightedMessage(body:String,query:String,background:Color,foreground:Color):AnnotatedString=buildAnnotatedString{
 if(query.isBlank()){append(body);return@buildAnnotatedString}
 var offset=0
 while(offset<body.length){
  val index=body.indexOf(query,offset,ignoreCase=true)
  if(index<0){append(body.substring(offset));break}
  append(body.substring(offset,index))
  withStyle(SpanStyle(background=background,color=foreground,fontWeight=FontWeight.SemiBold)){append(body.substring(index,index+query.length))}
  offset=index+query.length
 }
}
@Composable internal fun SeraHistoricalMessageDialog(token:String,userId:String,thread:String,target:String,playback:SeraChatPlayback,peerName:String,onDismiss:()->Unit){
 var rows by remember(thread,target){mutableStateOf<List<NativeMessage>>(emptyList())}
 var loading by remember(thread,target){mutableStateOf(true)}
 var error by remember(thread,target){mutableStateOf("")}
 var anchor by remember(thread,target){mutableStateOf(target)}
 var image by remember(thread,target){mutableStateOf<NativeChatFile?>(null)}
 val list=rememberLazyListState()
 LaunchedEffect(token,thread,anchor){
  loading=true;error=""
  image=null
  val r=withContext(Dispatchers.IO){loadNativeMessageContext(token,thread,anchor)}
  rows=r.first.orEmpty();error=r.second;loading=false
  val index=rows.indexOfFirst{it.id==anchor}
  if(index>=0){withFrameNanos{};list.scrollToItem(index+1)}
 }
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
   Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=12.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Message history",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick=onDismiss){Text("Back")}}
    Text("Browsing a historical conversation window",style=MaterialTheme.typography.bodySmall)
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    LazyColumn(Modifier.weight(1f),state=list,verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(vertical=8.dp)){
     item{TextButton(enabled=!loading&&rows.firstOrNull{!it.unsent}?.id?.let{it!=anchor}==true,onClick={rows.firstOrNull{!it.unsent}?.let{anchor=it.id}}){Text("Earlier messages")}}
     items(rows,key={it.id}){m->
      if(m.id==target)Text("Selected message",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
      SeraNativeMessageRow(m,rows.firstOrNull{it.id==m.replyId},userId,peerName,token,playback,320.dp,seraChatColors().ink,seraChatColors().quote,seraChatColors().canvas,seraChatColors().muted,seraChatColors().accent,seraChatColors().outgoing,seraChatColors().incoming,false,{},{},{},{},{},{},{},{image=it},{error=it},readOnly=true)
     }
     item{TextButton(enabled=!loading&&rows.lastOrNull{!it.unsent}?.id?.let{it!=anchor}==true,onClick={rows.lastOrNull{!it.unsent}?.let{anchor=it.id}}){Text("Later messages")}}
    }
   }
  }
 }
 image?.let{SeraPrivateImageViewer(token,it,onDismiss={image=null})}
}
