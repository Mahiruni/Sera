package et.sera.nativeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal object SeraNotificationSounds {
    data class Tone(val key:String,val label:String,val description:String,val resource:Int)
    val tones=listOf(
        Tone("pulse","SERA Pulse","Messages",R.raw.sera_incoming),
        Tone("opportunity","SERA Opportunity","Jobs and opportunities",R.raw.sera_opportunity),
        Tone("connect","SERA Connect","Professional connections",R.raw.sera_connect),
        Tone("social","SERA Social","Likes, comments and shares",R.raw.sera_social),
        Tone("attention","SERA Attention","Important account activity",R.raw.sera_attention)
    )
    private fun prefs(context:Context)=context.getSharedPreferences("sera_notification_sounds",Context.MODE_PRIVATE)
    fun mode(context:Context)=prefs(context).getString("mode","message")?:"message"
    fun setMode(context:Context,mode:String){if(mode in listOf("message","system","signature","silent"))prefs(context).edit().putString("mode",mode).apply()}
    fun socialSound(context:Context)=prefs(context).getBoolean("social_sound",false)
    fun setSocialSound(context:Context,enabled:Boolean){prefs(context).edit().putBoolean("social_sound",enabled).apply()}
    private fun key(kind:String)=when(kind){
        "message","company_message"->"pulse"
        "job_match","application_update","opportunity_match"->"opportunity"
        "connection_request","connection_accepted","follow","space_invitation","community_invitation","project_invitation"->"connect"
        "like","comment","share","repost","reply","social"->"social"
        else->"attention"
    }
    fun id(tone:Tone)=if(tone.key=="pulse")"sera_incoming_messages_v2" else "sera_signature_"+tone.key+"_v1"
    const val SILENT="sera_silent_v1"
    fun createChannels(context:Context){
        val manager=context.getSystemService(NotificationManager::class.java)
        val audio=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        tones.forEach{tone->
            val uri=Uri.parse("android.resource://"+context.packageName+"/raw/"+context.resources.getResourceEntryName(tone.resource))
            manager.createNotificationChannel(NotificationChannel(id(tone),tone.label+" · "+tone.description,if(tone.key=="pulse")NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT).apply{
                description=tone.description;setSound(uri,audio);enableVibration(true)
            })
        }
        manager.createNotificationChannel(NotificationChannel(SILENT,"SERA silent activity",NotificationManager.IMPORTANCE_LOW).apply{description="Quiet notifications";setSound(null,null);enableVibration(false)})
    }
    fun channelFor(context:Context,kind:String):String{
        val k=key(kind)
        return when(mode(context)){
            "message"->if(k=="pulse")id(tones.first{it.key==k}) else SeraPush.CHANNEL
            "signature"->if(k=="social"&&!socialSound(context))SILENT else id(tones.first{it.key==k})
            "silent"->SILENT
            else->SeraPush.CHANNEL
        }
    }
    fun playIncoming(context:Context){
        if(mode(context)=="silent")return
        val audioManager=context.getSystemService(android.media.AudioManager::class.java)
        if(audioManager.ringerMode!=android.media.AudioManager.RINGER_MODE_NORMAL||audioManager.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION)==0)return
        val manager=context.getSystemService(NotificationManager::class.java)
        if(manager.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_NONE||manager.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_ALARMS)return
        val channel=manager.getNotificationChannel(channelFor(context,"message"))?:return
        if(channel.importance<NotificationManager.IMPORTANCE_DEFAULT||channel.sound==null)return
        if(manager.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_PRIORITY&&!channel.canBypassDnd())return
        runCatching{val player=MediaPlayer().apply{setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());setDataSource(context,channel.sound);setOnCompletionListener{it.release()};setOnErrorListener{p,_,_->p.release();true};setOnPreparedListener{it.start()};prepareAsync()}}
    }
    fun openChannel(context:Context,channel:String){
        runCatching{context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channel))}
    }
}

@Composable internal fun SeraSoundSettings(modifier:Modifier=Modifier,userId:String="",onBack:()->Unit){
    val context=LocalContext.current
    var messagesEnabled by remember(userId){mutableStateOf(SeraMessageAlerts.enabled(context,userId))}
    var mode by remember{mutableStateOf(SeraNotificationSounds.mode(context))}
    var social by remember{mutableStateOf(SeraNotificationSounds.socialSound(context))}
    var player by remember{mutableStateOf<MediaPlayer?>(null)}
    var playing by remember{mutableStateOf<String?>(null)}
    fun stop(){player?.release();player=null;playing=null}
    DisposableEffect(Unit){SeraNotificationSounds.createChannels(context);onDispose{player?.release()}}
    BackHandler{stop();onBack()}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick={stop();onBack()}){Text("Back")}
        Text("Notification sounds",style=MaterialTheme.typography.headlineSmall)
        if(userId.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Message notifications");Text("Private alerts on this device",style=MaterialTheme.typography.bodySmall)};Switch(checked=messagesEnabled,onCheckedChange={messagesEnabled=it;SeraMessageAlerts.setEnabled(context,userId,it)})}
        Text("Choose how SERA sounds on this device. Android notification permissions and channel settings remain in control.")
        listOf("message" to "SERA message tone","system" to "Android default","signature" to "SERA Signature","silent" to "Silent").forEach{(value,label)->
            ElevatedCard(onClick={mode=value;SeraNotificationSounds.setMode(context,value)},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){RadioButton(selected=mode==value,onClick={mode=value;SeraNotificationSounds.setMode(context,value)});Column{Text(label,style=MaterialTheme.typography.titleMedium);Text(when(value){"message"->"Custom incoming message sound; other activity uses Android default.";"system"->"Keep your existing Android notification channel.";"signature"->"Original SERA sounds for each activity type.";else->"Quiet notifications without sound."},style=MaterialTheme.typography.bodySmall)}}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Social activity sounds");Text("Off by default in Signature mode.",style=MaterialTheme.typography.bodySmall)};Switch(checked=social,onCheckedChange={social=it;SeraNotificationSounds.setSocialSound(context,it)},enabled=mode=="signature")}
        Text("Preview the collection",style=MaterialTheme.typography.titleLarge)
        SeraNotificationSounds.tones.forEach{tone->
            ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(tone.label,style=MaterialTheme.typography.titleMedium);Text(tone.description,style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={if(playing==tone.key)stop() else{stop();runCatching{MediaPlayer.create(context,tone.resource)?.let{p->player=p;playing=tone.key;p.setOnCompletionListener{if(player===p)stop()};p.start()}}}}){Text(if(playing==tone.key)"Stop" else "Preview")};TextButton(onClick={SeraNotificationSounds.openChannel(context,SeraNotificationSounds.id(tone))}){Text("Android settings")}}}}
        }
        TextButton(onClick={SeraNotificationSounds.openChannel(context,if(mode=="silent")SeraNotificationSounds.SILENT else SeraNotificationSounds.channelFor(context,"message"))}){Text("System notification settings")}
        Text("Sound and vibration can also be changed in Android settings. Existing channels are never reset by SERA.",style=MaterialTheme.typography.bodySmall)
    }
}
