package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONObject
@Composable internal fun SeraSpaceChat(token:String,userId:String,spaceId:String,name:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var messages by remember(token,spaceId){mutableStateOf<List<JSONObject>>(emptyList())};var draft by remember(spaceId){mutableStateOf("")};var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var sending by remember{mutableStateOf(false)};var refresh by remember{mutableIntStateOf(0)};var older by remember(spaceId){mutableStateOf<List<JSONObject>>(emptyList())};var hasOlder by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();val listState=rememberLazyListState()
 androidx.activity.compose.BackHandler(onBack=onBack)
 LaunchedEffect(token,spaceId,refresh){while(true){val r=withContext(Dispatchers.IO){spaceCall(token,"sera_list_space_messages",JSONObject().put("p_space",spaceId))};if(r.first!=null){messages=spaceRows(r.first).reversed();hasOlder=messages.size==100;error=""}else{messages=emptyList();older=emptyList();error=r.second};loading=false;delay(5000)}}
 val all=(older+messages).distinctBy{it.optString("id")}
 LaunchedEffect(all.lastOrNull()?.optString("id")){if(all.isNotEmpty())listState.scrollToItem(all.lastIndex)}
 Column(modifier.fillMaxSize().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
 TextButton(onClick=onBack){Text("Back to space")};Text(name,style=MaterialTheme.typography.headlineSmall);Text("Private team conversation",style=MaterialTheme.typography.bodySmall)
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++}){Text("Retry")}}
 if(hasOlder&&all.isNotEmpty())TextButton(onClick={scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,"sera_list_space_messages",JSONObject().put("p_space",spaceId).put("p_before",all.first().optString("created_at")))};if(r.first!=null){val batch=spaceRows(r.first);older=batch.reversed()+older;hasOlder=batch.size==100}else error=r.second}}){Text("Load earlier messages")}
 LazyColumn(Modifier.weight(1f),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)){items(all,key={it.getString("id")}){m->val mine=m.optString("sender_id")==userId;Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Surface(shape=RoundedCornerShape(18.dp),color=if(mine)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.widthIn(max=300.dp)){Column(Modifier.padding(12.dp)){Text(if(mine)"You" else m.optString("full_name"),style=MaterialTheme.typography.labelMedium);Text(m.optString("body"));Text(m.optString("created_at").take(16).replace('T',' '),style=MaterialTheme.typography.labelSmall)}}}}}
 if(!loading&&all.isEmpty()&&error.isBlank())Text("Start a conversation with your team.")
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(draft,{draft=it.take(4000)},label={Text("Message")},modifier=Modifier.weight(1f),maxLines=4);Button(onClick={val body=draft.trim();sending=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,"sera_send_space_message",JSONObject().put("p_space",spaceId).put("p_body",body))};sending=false;if(r.first!=null){if(draft.trim()==body)draft="";refresh++}else error=r.second}},enabled=!sending&&draft.isNotBlank()){Text("Send")}}
 }
}
