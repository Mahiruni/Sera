package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable internal fun SeraMultiSelectToolbar(count:Int,busy:Boolean,textOnly:Boolean,onClose:()->Unit,onCopy:()->Unit,onStar:()->Unit,onForward:()->Unit){
 Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){
  Row(Modifier.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose,enabled=!busy){Icon(Icons.Outlined.Close,"Clear selection")};Text("$count selected",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);IconButton(onClick=onCopy,enabled=count>0&&!busy){Icon(Icons.Outlined.ContentCopy,"Copy selected messages")};IconButton(onClick=onStar,enabled=count>0&&!busy){Icon(Icons.Outlined.StarBorder,"Star selected messages")};IconButton(onClick=onForward,enabled=count>0&&textOnly&&!busy){Icon(Icons.Outlined.Forward,"Forward selected text messages")}}
 }
}
@Composable internal fun SeraBulkForwardDialog(token:String,source:String,messages:List<NativeMessage>,conversations:List<NativeConversation>,onDismiss:()->Unit,onSuccess:()->Unit){
 var target by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 val ids=remember(messages){messages.map{it.id}};val clients=remember(ids,target){ids.map{UUID.randomUUID().toString()}}
 AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Forward ${ids.size} messages")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Select a conversation. Text messages are forwarded in their original order.",style=MaterialTheme.typography.bodySmall);LazyColumn(Modifier.heightIn(max=300.dp)){items(conversations,key={it.id}){c->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=target==c.id,onClick={target=c.id},enabled=!busy);TextButton(onClick={target=c.id},enabled=!busy){Text(c.peerName)}}}};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(enabled=target!=null&&!busy&&ids.isNotEmpty(),onClick={val destination=target?:return@Button;busy=true;scope.launch{try{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_bulk_forward_text",JSONObject().put("p_source",source).put("p_destination",destination).put("p_ids",JSONArray(ids)).put("p_clients",JSONArray(clients)))};if(r.first!=null)onSuccess()else error=r.second}finally{busy=false}}}){Text(if(busy)"Forwarding…" else "Forward")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}})
}
