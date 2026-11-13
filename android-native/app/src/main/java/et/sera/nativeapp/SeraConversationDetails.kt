package et.sera.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SeraSharedItem(val messageId:String,val senderId:String,val at:String,val file:NativeChatFile?=null,val link:String?=null)
private val sharedLinkPattern=Regex("""https?://[^\s<>]+""",RegexOption.IGNORE_CASE)
internal fun seraConversationSharedItems(messages:List<NativeMessage>):List<SeraSharedItem> =
    messages.asReversed().filterNot{it.unsent}.flatMap { m ->
        m.files.map{SeraSharedItem(m.id,m.senderId,m.createdAt,file=it)} +
            sharedLinkPattern.findAll(m.body).mapNotNull{match->
                val link=match.value.trimEnd('.',',','!','?',')',']','}',':',';')
                val valid=runCatching{val uri=java.net.URI(link);uri.host!=null&&uri.userInfo==null&&uri.scheme.lowercase() in setOf("http","https")}.getOrDefault(false)
                if(valid)SeraSharedItem(m.id,m.senderId,m.createdAt,link=link)else null
            }.toList()
    }
internal fun sharedCategory(item:SeraSharedItem):String = when {
    item.link!=null -> "Links"
    item.file?.mime?.startsWith("audio/")==true -> "Voice"
    item.file?.mime?.startsWith("image/")==true || item.file?.mime?.startsWith("video/")==true -> "Media"
    else -> "Files"
}
internal fun seraFilterSharedItems(items:List<SeraSharedItem>,category:String,query:String,sender:String,userId:String):List<SeraSharedItem>{
    val term=query.trim()
    return items.filter{item->
        sharedCategory(item)==category &&
        (sender=="All" || (sender=="You"&&item.senderId==userId) || (sender=="Contact"&&item.senderId!=userId)) &&
        (term.isBlank() || listOfNotNull(item.file?.name,item.link).any{it.contains(term,ignoreCase=true)})
    }
}
@Composable internal fun SeraConversationDetails(
    conversation:NativeConversation,token:String,userId:String,peerActivity:String?,
    messages:List<NativeMessage>,historyLimit:Int,loading:Boolean,
    playback:SeraChatPlayback,ink:Color,soft:Color,muted:Color,accent:Color,
    onBack:()->Unit,onLoadEarlier:()->Unit,onChanged:()->Unit,onImage:(NativeChatFile)->Unit,
    onSearch:()->Unit,onJumpToMessage:(String)->Unit
){
    val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var query by remember(conversation.id){mutableStateOf("")}
    var sender by remember(conversation.id){mutableStateOf("All")}
    val headline by produceState("",token,conversation.peerId){
        value=if(!SeraMessageAlerts.validId(conversation.peerId)) "" else withContext(Dispatchers.IO){
            runCatching{searchRows(token,"post_authors",mapOf("id" to "eq."+conversation.peerId,"select" to "headline,location","limit" to "1")).optJSONObject(0)?.let{row->listOf("headline","location").map{row.optString(it).takeUnless{v->v=="null"}.orEmpty()}.filter{it.isNotBlank()}.joinToString(" · ")}.orEmpty()}.getOrDefault("")
        }
    }
    var category by remember(conversation.id){mutableStateOf("Media")}
    var error by remember(conversation.id){mutableStateOf("")}
    var opening by remember(conversation.id){mutableStateOf<String?>(null)}
    var selectedLink by remember(conversation.id){mutableStateOf<String?>(null)}
    val items=remember(messages){seraConversationSharedItems(messages)}
    val tabs=listOf("Media","Files","Links","Voice")
    val filtered=remember(items,category,query,sender,userId){seraFilterSharedItems(items,category,query,sender,userId)}
    val canLoadMore=messages.size>=historyLimit&&historyLimit<1000
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().height(58.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back to conversation",tint=ink)}
            Text("Conversation details",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=ink,modifier=Modifier.weight(1f))
            SeraConversationControlMenu(token,conversation,onChanged)
        }
        HorizontalDivider(color=soft)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item {
                Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Box(Modifier.size(88.dp).clip(CircleShape).background(soft),contentAlignment=Alignment.Center){
                        Text(conversation.peerName.take(1).uppercase(),style=MaterialTheme.typography.headlineLarge,color=accent)
                        conversation.photo?.let{SeraMediaImage(it,"Profile photo",176,176,Modifier.fillMaxSize())}
                    }
                    Text(conversation.peerName,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=ink)
                    if(headline.isNotBlank())Text(headline,style=MaterialTheme.typography.bodyMedium,color=ink)
                    Text(peerActivity?:"SERA professional",style=MaterialTheme.typography.bodyMedium,color=muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(enabled=SeraMessageAlerts.validId(conversation.peerId),onClick={
                            runCatching{uriHandler.openUri("https://sera.hisabtech.com/members/"+Uri.encode(conversation.peerId))}.onFailure{error="Could not open profile."}
                        }){Text("View profile")}
                        FilledTonalButton(onClick=onSearch){Text("Search chat")}
                    }
                }
            }
            item {
                Text("Shared content",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=ink,modifier=Modifier.padding(horizontal=8.dp))
                Text("Photos, videos, documents, links and voice messages shared in this conversation.",style=MaterialTheme.typography.bodySmall,color=muted,modifier=Modifier.padding(8.dp))
                ScrollableTabRow(selectedTabIndex=tabs.indexOf(category),edgePadding=0.dp,containerColor=Color.Transparent){
                    tabs.forEach{tab->Tab(selected=tab==category,onClick={category=tab},text={Text("$tab (${items.count{sharedCategory(it)==tab}})",maxLines=1)})}
                }
                OutlinedTextField(query,{query=it.take(120)},modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp),singleLine=true,label={Text("Search shared content")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={keyboard?.hide();focus.clearFocus()}),trailingIcon={if(query.isNotEmpty())TextButton(onClick={query=""}){Text("Clear")}})
                Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("All","You","Contact").forEach{who->FilterChip(selected=sender==who,onClick={sender=who},label={Text(who)})}
                }
                Text("${filtered.size} shown · ${items.size} items in loaded history",style=MaterialTheme.typography.labelSmall,color=muted,modifier=Modifier.padding(8.dp))
            }
            if(filtered.isEmpty()) item {
                Surface(color=soft,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
                    Text(if(loading)"Loading shared content…" else if(query.isNotBlank()||sender!="All")"No matching shared items." else "No ${category.lowercase()} in loaded messages.",color=muted,modifier=Modifier.padding(20.dp))
                }
            }
            items(filtered){entry->
                val file=entry.file
                Surface(color=soft,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(if(entry.senderId==userId)"You" else conversation.peerName,style=MaterialTheme.typography.labelMedium,color=accent)
                        if(file!=null){
                            when{
                                file.mime.startsWith("image/")->SeraPrivateImageCard(token,file,onOpen={onImage(file)},foreground=ink)
                                file.mime=="video/mp4"->SeraVideoAttachment(token,file,accent,onAudioStart={playback.pause()})
                                file.mime.startsWith("audio/")->SeraChatVoicePlayer(playback,file.path,file.durationMs,accent,onPlay={scope.launch{playback.toggle(file.path,file.mime,{readChatFile(token,file)},{error=it})}})
                                else->TextButton(enabled=opening==null,onClick={
                                    opening=file.path
                                    scope.launch{
                                        val result=withContext(Dispatchers.IO){runCatching{downloadChatFile(context,token,file)}}
                                        opening=null
                                        result.onSuccess{uri->runCatching{openChatFile(context,uri,file.mime)}.onFailure{error="No app can open this file."}}.onFailure{error="File unavailable or access denied."}
                                    }
                                }){Icon(Icons.Outlined.Description,null);Spacer(Modifier.width(8.dp));Text(if(opening==file.path)"Opening…" else file.name,maxLines=2,overflow=TextOverflow.Ellipsis)}
                            }
                        }else entry.link?.let{link->TextButton(onClick={selectedLink=link}){Icon(Icons.Outlined.Link,null);Spacer(Modifier.width(8.dp));Text(link,maxLines=3,overflow=TextOverflow.Ellipsis)}}
                        val date=runCatching{java.time.Instant.parse(entry.at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))}.getOrDefault("")
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            if(date.isNotBlank())Text(date,style=MaterialTheme.typography.labelSmall,color=muted,modifier=Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                            TextButton(onClick={keyboard?.hide();focus.clearFocus();onJumpToMessage(entry.messageId)}){Text("Go to message")}
                        }
                    }
                }
            }
            if(canLoadMore)item{OutlinedButton(onClick=onLoadEarlier,modifier=Modifier.fillMaxWidth()){Text("Load earlier shared content")}}
            if(historyLimit>=1000&&messages.size>=1000)item{Text("Showing up to 1,000 recent messages. Older shared items are not loaded.",style=MaterialTheme.typography.bodySmall,color=muted,modifier=Modifier.padding(8.dp))}
        }
    }
    selectedLink?.let{link->AlertDialog(onDismissRequest={selectedLink=null},title={Text("Open external link?")},text={Text(link)},confirmButton={TextButton(onClick={selectedLink=null;runCatching{uriHandler.openUri(link)}.onFailure{error="Could not open link."}}){Text("Open")}},dismissButton={TextButton(onClick={selectedLink=null}){Text("Cancel")}})}
    if(error.isNotBlank())AlertDialog(onDismissRequest={error=""},title={Text("Unable to open")},text={Text(error)},confirmButton={TextButton(onClick={error=""}){Text("OK")}})
}
