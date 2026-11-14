package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 val context=androidx.compose.ui.platform.LocalContext.current
 var loaded by remember{mutableStateOf(false)}
 var enabled by remember{mutableStateOf(true)}
 var quiet by remember{mutableStateOf(false)}
 var typing by remember{mutableStateOf(true)}
 var receipts by remember{mutableStateOf(true)}
 var previewMode by remember{mutableStateOf("full")}
 var start by remember{mutableStateOf("22:00")}
 var end by remember{mutableStateOf("07:00")}
 var zone by remember{mutableStateOf("Africa/Addis_Ababa")}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 LaunchedEffect(token,userId){val p=withContext(Dispatchers.IO){loadControlPreferences(token,userId)};if(p==null)error="Could not load preferences." else{enabled=p.optBoolean("notifications_enabled",true);quiet=p.optBoolean("quiet_enabled",false);typing=p.optBoolean("typing_visible",true);receipts=p.optBoolean("read_receipts_visible",true);previewMode=p.optString("notification_preview_mode",if(p.has("notification_previews"))if(p.optBoolean("notification_previews"))"full" else "hidden" else "full");start=clockValue(p.optInt("quiet_start",1320));end=clockValue(p.optInt("quiet_end",420));zone=p.optString("quiet_timezone","Africa/Addis_Ababa");loaded=true}}
 Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)){
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
   Column(Modifier.fillMaxSize().safeDrawingPadding()){
    Surface(tonalElevation=2.dp){
     Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){
       Text("Messaging control center",style=MaterialTheme.typography.titleLarge)
       Text("Your conversations. Your rules.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(enabled=!busy,onClick=onDismiss){Icon(Icons.Outlined.Close,contentDescription="Close messaging control center")}
     }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
     Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(24.dp)){
      Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       Text("YOUR MESSAGE EXPERIENCE",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
       Text("Stay connected. Stay in control.",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
       Text("Manage your alerts and privacy across devices. Messages keep arriving in your inbox.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
      }
     }
     if(!loaded&&error.isBlank())LinearProgressIndicator(Modifier.fillMaxWidth())
     ControlSection("Notifications","Choose when you receive message alerts."){
      ControlToggle("Message notifications","Receive new message push alerts.",enabled,loaded&&!busy){enabled=it}
      HorizontalDivider()
      ControlToggle("Quiet hours","Pause alerts on your schedule.",quiet,loaded&&!busy){quiet=it}
      if(quiet){
       Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        OutlinedTextField(start,{start=it},Modifier.weight(1f),label={Text("Start (HH:mm)")},singleLine=true,enabled=loaded&&!busy)
        OutlinedTextField(end,{end=it},Modifier.weight(1f),label={Text("End (HH:mm)")},singleLine=true,enabled=loaded&&!busy)
       }
       OutlinedTextField(zone,{zone=it},Modifier.fillMaxWidth(),label={Text("Time zone")},singleLine=true,enabled=loaded&&!busy)
       Text("Overnight hours are supported.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
     }
     ControlSection("Privacy & activity","Choose what other people can see."){
      ControlToggle("Show typing activity","Let others see when you type.",typing,loaded&&!busy){typing=it}
      HorizontalDivider()
      ControlToggle("Share read receipts","Keep new reads private when off.",receipts,loaded&&!busy){receipts=it}
      HorizontalDivider()
      SeraPresenceSettings(token,userId)
     }
     ControlSection(androidx.compose.ui.res.stringResource(R.string.sera_notification_privacy),"Choose what appears in private notifications."){
      listOf("full" to R.string.sera_notification_full,"sender" to R.string.sera_notification_sender,"hidden" to R.string.sera_notification_hidden).forEach{(mode,label)->
       Surface(shape=RoundedCornerShape(14.dp),color=if(previewMode==mode)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)){
        Row(Modifier.fillMaxWidth().padding(end=12.dp),verticalAlignment=Alignment.CenterVertically){
         RadioButton(selected=previewMode==mode,onClick={previewMode=mode},enabled=loaded&&!busy)
         Text(androidx.compose.ui.res.stringResource(label),style=MaterialTheme.typography.bodyMedium)
        }
       }
      }
      ControlNotificationPreview(previewMode)
      Text(androidx.compose.ui.res.stringResource(R.string.sera_notification_lock),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     if(error.isNotBlank())Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)){Text(error,Modifier.fillMaxWidth().padding(14.dp),color=MaterialTheme.colorScheme.onErrorContainer)}
    }
    Surface(tonalElevation=3.dp){
     Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
      OutlinedButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}
      Button(modifier=Modifier.weight(1f).heightIn(min=48.dp),enabled=loaded&&!busy,onClick={
 val a=parseClock(start);val z=parseClock(end)
 if(a==null||z==null||a==z||zone.isBlank()){error="Enter valid quiet hours.";return@Button}
 busy=true;scope.launch{val result=withContext(Dispatchers.IO){chatRpc(token,"sera_save_message_preferences",JSONObject().put("p_enabled",enabled).put("p_quiet",quiet).put("p_start",a).put("p_end",z).put("p_timezone",zone))};if(result.first!=null){val privacy=withContext(Dispatchers.IO){chatRpc(token,"sera_set_typing_visibility",JSONObject().put("p_visible",typing))};if(privacy.first!=null){val read=withContext(Dispatchers.IO){chatRpc(token,"sera_set_read_receipt_visibility",JSONObject().put("p_visible",receipts))};if(read.first!=null){val previewResult=withContext(Dispatchers.IO){chatRpc(token,"sera_set_notification_preview_mode",JSONObject().put("p_mode",previewMode))};busy=false;if(previewResult.first!=null){SeraMessageAlerts.clearPrivateAlerts(context,userId);onDismiss()}else error="Other preferences saved. Preview preference could not be saved."}else{busy=false;error="Other preferences saved. Read privacy could not be saved."}}else{busy=false;error="Notification preferences saved. Typing preference could not be saved."}}else{busy=false;error=result.second}}
      }){Text(if(busy)"Saving…" else "Save preferences")}
     }
    }
   }
  }
 }
}
@Composable private fun ControlNotificationPreview(mode:String){
 val scheme=MaterialTheme.colorScheme
 val hidden=mode=="hidden"
 val body=when(mode){"full"->androidx.compose.ui.res.stringResource(R.string.sera_control_sample_message);"sender"->androidx.compose.ui.res.stringResource(R.string.sera_notification_sent);else->androidx.compose.ui.res.stringResource(R.string.sera_notification_private)}
 Surface(shape=RoundedCornerShape(18.dp),color=scheme.tertiaryContainer,modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(androidx.compose.ui.res.stringResource(R.string.sera_control_live_preview),style=MaterialTheme.typography.labelLarge,color=scheme.onTertiaryContainer)
   Text(androidx.compose.ui.res.stringResource(R.string.sera_control_example),style=MaterialTheme.typography.labelSmall,color=scheme.onTertiaryContainer)
   Surface(shape=RoundedCornerShape(14.dp),color=scheme.surface){
    Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
     Surface(shape=CircleShape,color=Color(0xFF7C3AED),modifier=Modifier.size(42.dp)){Box(contentAlignment=Alignment.Center){Text(if(hidden)"S" else "H",color=Color.White,style=MaterialTheme.typography.titleMedium)}}
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
      Text(if(hidden)"Sera" else androidx.compose.ui.res.stringResource(R.string.sera_control_sample_sender),style=MaterialTheme.typography.titleSmall,color=scheme.onSurface)
      Text(body,style=MaterialTheme.typography.bodySmall,color=scheme.onSurface)
      Text("Sera",style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant)
     }
     Surface(shape=CircleShape,color=Color(0xFF7C3AED),modifier=Modifier.size(9.dp)){}
    }
   }
  }
 }
}
@Composable private fun ControlSection(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){
 OutlinedCard(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(title,style=MaterialTheme.typography.titleMedium)
   Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   content()
  }
 }
}
@Composable private fun ControlToggle(title:String,subtitle:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=56.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
   Text(title,style=MaterialTheme.typography.bodyLarge)
   Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Switch(checked=checked,onCheckedChange=onChange,enabled=enabled)
 }
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
