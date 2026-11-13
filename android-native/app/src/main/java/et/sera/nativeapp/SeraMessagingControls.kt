package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection

private fun loadControlPreferences(token:String,user:String):JSONObject?{
 var c:HttpURLConnection?=null
 return try{
 c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/sera_message_preferences?user_id=eq."+user+"&limit=1").openConnection() as HttpURLConnection
 c.connectTimeout=8000;c.readTimeout=8000;c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer "+token)
 if(c.responseCode in 200..299)JSONArray(c.inputStream.bufferedReader().use{it.readText()}).optJSONObject(0)?:JSONObject() else null
 }catch(_:Exception){null}finally{c?.disconnect()}
}
private fun clockValue(m:Int)="%02d:%02d".format(java.util.Locale.ROOT,m/60,m%60)
private fun parseClock(s:String):Int?=if(s.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")))s.substring(0,2).toInt()*60+s.substring(3).toInt() else null

@Composable internal fun SeraMessagingControlDialog(token:String,userId:String,onDismiss:()->Unit){
 val scope=rememberCoroutineScope()
 var loaded by remember{mutableStateOf(false)}
 var enabled by remember{mutableStateOf(true)}
 var quiet by remember{mutableStateOf(false)}
 var start by remember{mutableStateOf("22:00")}
 var end by remember{mutableStateOf("07:00")}
 var zone by remember{mutableStateOf("Africa/Addis_Ababa")}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 LaunchedEffect(token,userId){val p=withContext(Dispatchers.IO){loadControlPreferences(token,userId)};if(p==null)error="Could not load preferences." else{enabled=p.optBoolean("notifications_enabled",true);quiet=p.optBoolean("quiet_enabled",false);start=clockValue(p.optInt("quiet_start",1320));end=clockValue(p.optInt("quiet_end",420));zone=p.optString("quiet_timezone","Africa/Addis_Ababa");loaded=true}}
 AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Messaging control center")},text={
 Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Text("Account-wide push preferences. Messages continue arriving in your inbox.",fontSize=12.sp)
 Row(verticalAlignment=Alignment.CenterVertically){Text("Message notifications",Modifier.weight(1f));Switch(enabled=loaded&&!busy,checked=enabled,onCheckedChange={enabled=it})}
 Row(verticalAlignment=Alignment.CenterVertically){Text("Quiet hours",Modifier.weight(1f));Switch(enabled=loaded&&!busy,checked=quiet,onCheckedChange={quiet=it})}
 OutlinedTextField(start,{start=it},label={Text("Start (HH:mm)")},singleLine=true,enabled=loaded&&!busy)
 OutlinedTextField(end,{end=it},label={Text("End (HH:mm)")},singleLine=true,enabled=loaded&&!busy)
 OutlinedTextField(zone,{zone=it},label={Text("Time zone")},singleLine=true,enabled=loaded&&!busy)
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
 }
 },confirmButton={TextButton(enabled=loaded&&!busy,onClick={
 val a=parseClock(start);val z=parseClock(end)
 if(a==null||z==null||a==z||zone.isBlank()){error="Enter valid quiet hours.";return@TextButton}
 busy=true;scope.launch{val result=withContext(Dispatchers.IO){chatRpc(token,"sera_save_message_preferences",JSONObject().put("p_enabled",enabled).put("p_quiet",quiet).put("p_start",a).put("p_end",z).put("p_timezone",zone))};busy=false;if(result.first!=null)onDismiss()else error=result.second}
 }){Text(if(busy)"Saving…" else "Save")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}})
}
@Composable internal fun SeraConversationControlMenu(token:String,conversation:NativeConversation,onChanged:()->Unit){
 val scope=rememberCoroutineScope();var open by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 fun change(action:String,value:Boolean,minutes:Int=0){open=false;busy=true;scope.launch{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_set_conversation_preference",JSONObject().put("p_conversation",conversation.id).put("p_action",action).put("p_value",value).put("p_mute_minutes",minutes))};busy=false;if(r.first!=null)onChanged()else error=r.second}}
 Box{IconButton(enabled=!busy,onClick={open=true},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.MoreVert,"Conversation controls")}
 DropdownMenu(expanded=open,onDismissRequest={open=false}){
 DropdownMenuItem(text={Text(if(conversation.pinned)"Unpin" else "Pin")},onClick={change("pin",!conversation.pinned)})
 DropdownMenuItem(text={Text(if(conversation.archived)"Restore to inbox" else "Archive")},onClick={change("archive",!conversation.archived)})
 DropdownMenuItem(text={Text(if(conversation.markedUnread)"Clear follow-up" else "Mark unread for follow-up")},onClick={change("unread",!conversation.markedUnread)})
 listOf("Mute 1 hour" to 60,"Mute 8 hours" to 480,"Mute 24 hours" to 1440,"Mute indefinitely" to -1,"Unmute" to 0).forEach{(label,n)->DropdownMenuItem(text={Text(label)},onClick={change("mute",true,n)})}
 }}
 if(error.isNotBlank())AlertDialog(onDismissRequest={error=""},title={Text("Unable to save")},text={Text(error)},confirmButton={TextButton(onClick={error=""}){Text("OK")}})
}
