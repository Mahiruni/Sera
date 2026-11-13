package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun SeraNativeMessageDialogs(remove:NativeMessage?,reaction:NativeMessage?,forwarding:NativeMessage?,conversations:List<NativeConversation>,token:String,userId:String,forwardClient:String,onRemoveDismiss:()->Unit,onReactionDismiss:()->Unit,onForwardDismiss:()->Unit,onReact:(NativeMessage,String)->Unit,onChanged:()->Unit,onError:(String)->Unit){
    val scope=rememberCoroutineScope()
    if(remove!=null)AlertDialog(onDismissRequest=onRemoveDismiss,title={Text("Unsend message?")},text={Text("This message will be removed for everyone.")},confirmButton={TextButton(onClick={
        onRemoveDismiss()
        scope.launch{val r=withContext(Dispatchers.IO){unsendNativeMessage(token,remove.id)};if(r.first==null)onError(r.second)else onChanged()}
    }){Text("Unsend")}},dismissButton={TextButton(onClick=onRemoveDismiss){Text("Cancel")}})
    if(reaction!=null)AlertDialog(onDismissRequest=onReactionDismiss,title={Text("React to message")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        listOf("👍","❤️","😂","😮","😢","👏").chunked(3).forEach{group->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){group.forEach{emoji->IconButton(onClick={onReact(reaction,emoji)},modifier=Modifier.size(48.dp)){Text(emoji,fontSize=23.sp)}}}}
    }},confirmButton={TextButton(onClick=onReactionDismiss){Text("Close")}})
    if(forwarding!=null)key(forwardClient){SeraNativeForwardDialog(forwarding,conversations,token,userId,forwardClient,onForwardDismiss,onChanged,onError)}
}
@Composable private fun SeraNativeForwardDialog(source:NativeMessage,conversations:List<NativeConversation>,token:String,userId:String,client:String,onDismiss:()->Unit,onChanged:()->Unit,onError:(String)->Unit){
    val scope=rememberCoroutineScope()
    var query by remember{mutableStateOf("")}
    var target by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Forward message")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(source.body.take(120),maxLines=3,overflow=TextOverflow.Ellipsis,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query,{query=it},singleLine=true,placeholder={Text("Search conversations")},modifier=Modifier.fillMaxWidth())
        LazyColumn(Modifier.heightIn(max=220.dp)){
            items(conversations.filter{it.peerName.contains(query.trim(),true)},key={it.id}){conversation->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    RadioButton(selected=target==conversation.id,onClick={target=conversation.id},enabled=!busy)
                    TextButton(onClick={target=conversation.id},enabled=!busy,modifier=Modifier.weight(1f)){Text(conversation.peerName,maxLines=1,overflow=TextOverflow.Ellipsis)}
                }
            }
        }
    }},confirmButton={Button(enabled=target!=null&&!busy,onClick={
        val destination=target?:return@Button
        busy=true
        scope.launch{
            try{
                val r=withContext(Dispatchers.IO){
                    val file=source.files.firstOrNull()?.let{NativeChatSelection(it.name,it.mime,readChatFile(token,it),it.durationMs)}
                    sendNativeMessageV2(token,userId,destination,client,source.body,file,forward=source.id)
                }
                if(r.first!=null){onDismiss();onChanged()}else onError(r.second)
            }catch(e:CancellationException){throw e}catch(_:Exception){onError("Forward failed. Retry.")}
            finally{busy=false}
        }
    }){Text(if(busy)"Forwarding…" else "Forward")}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("Cancel")}})
}
