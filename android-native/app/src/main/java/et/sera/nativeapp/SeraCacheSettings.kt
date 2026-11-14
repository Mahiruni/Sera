package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Clear only reconstructable caches; never touch drafts, uploads, sessions or user files. */
@Composable internal fun SeraCacheSettings(user:String,onClear:()->Unit){
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var confirm by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var budget by remember{mutableIntStateOf(30)}
    var bytes by remember(user){mutableLongStateOf(0L)}
    var imageBytes by remember(user){mutableLongStateOf(0L)}
    val imageBudget=when(budget){15->30;50->200;else->100}
    LaunchedEffect(user){withContext(Dispatchers.IO){runCatching{val store=SeraLocalCache.get(context);Triple(store.budgetMb(),store.textBytes(user),store.imageBytes(user))}.getOrNull()}?.let{budget=it.first;bytes=it.second;imageBytes=it.third}}
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)){
        Text("Storage and performance",style=MaterialTheme.typography.titleMedium)
        Text("Recent posts, conversations and profile previews are saved to help Sera open faster.",style=MaterialTheme.typography.bodySmall)
        Text("Saved text: "+String.format(java.util.Locale.ROOT,"%.1f",bytes/1048576.0)+" MB · limit "+budget+" MB",style=MaterialTheme.typography.bodySmall)
        Text("Saved images: "+String.format(java.util.Locale.ROOT,"%.1f",imageBytes/1048576.0)+" MB · limit "+imageBudget+" MB",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(15 to "Saver",30 to "Balanced",50 to "Performance").forEach{(mb,label)->FilterChip(selected=budget==mb,onClick={scope.launch{busy=true;val result=withContext(Dispatchers.IO){runCatching{val store=SeraLocalCache.get(context);store.setBudgetMb(mb);store.textBytes(user) to store.imageBytes(user)}};result.onSuccess{bytes=it.first;imageBytes=it.second;budget=mb};busy=false;if(result.isFailure)message="Could not change cache limit."}},label={Text(label)},enabled=!busy)}}
        TextButton(onClick={confirm=true},enabled=!busy&&user.isNotBlank()){Text(if(busy)"Clearing…" else "Clear saved previews")}
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Clear saved previews?")},text={Text("Sera will download fresh content when needed. Your posts, messages, drafts, uploads and account stay safe.")},confirmButton={TextButton(onClick={
        confirm=false;busy=true;message=""
        scope.launch{
            val result=withContext(Dispatchers.IO){runCatching{
                SeraFeedPreviewCache(context,user).clear()
                SeraMessageHistoryCache(context).clear(user)
                SeraLocalCache.get(context).clear(user)
                SeraNetworkMemory.clear();SeraAvatarMemory.clear()
            }}
            busy=false
            if(result.isSuccess){bytes=0;imageBytes=0;onClear();message="Saved previews cleared."}else message="Could not clear previews. Please retry."
        }
    }){Text("Clear")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}
