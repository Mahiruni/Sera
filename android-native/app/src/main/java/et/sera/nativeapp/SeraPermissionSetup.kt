package et.sera.nativeapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class SeraPermissionStep(val permission:String,val title:Int,val description:Int,val icon:ImageVector)

/** Only permissions used by the native app. No broad storage or location access. */
internal fun seraPermissionSteps(sdk:Int):List<SeraPermissionStep> = buildList {
    if(sdk>=33)add(SeraPermissionStep(Manifest.permission.POST_NOTIFICATIONS,R.string.setup_notifications,R.string.setup_notifications_desc,Icons.Outlined.Notifications))
    add(SeraPermissionStep(Manifest.permission.RECORD_AUDIO,R.string.setup_microphone,R.string.setup_microphone_desc,Icons.Outlined.Mic))
    add(SeraPermissionStep(Manifest.permission.CAMERA,R.string.setup_camera,R.string.setup_camera_desc,Icons.Outlined.Videocam))
}

@Composable internal fun SeraPermissionSetup(onComplete:()->Unit,onLanguage:()->Unit={}){
    val context=LocalContext.current
    val steps=remember{seraPermissionSteps(Build.VERSION.SDK_INT)}
    var index by rememberSaveable{mutableIntStateOf(0)}
    var pending by rememberSaveable{mutableStateOf(false)}
    val complete by rememberUpdatedState(onComplete)
    val current=steps.getOrNull(index)
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){_ ->
        pending=false
        index++
    }
    fun next(){if(!pending)index++}
    LaunchedEffect(index){
        val step=steps.getOrNull(index)
        if(step==null)complete()
        else if(ContextCompat.checkSelfPermission(context,step.permission)==PackageManager.PERMISSION_GRANTED)index++
    }
    BackHandler(enabled=!pending){complete()}
    if(current==null)return
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=24.dp,vertical=16.dp).testTag("sera_permission_setup"),
        horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text("Sera",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=colors.primary)
            IconButton(onClick=onLanguage,enabled=!pending){Icon(Icons.Outlined.Language,contentDescription=stringResource(R.string.language))}
        }
        LinearProgressIndicator(progress={ (index+1).toFloat()/steps.size },modifier=Modifier.fillMaxWidth().padding(top=12.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=24.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Text(stringResource(R.string.setup_step,index+1,steps.size),style=MaterialTheme.typography.labelMedium,color=colors.primary)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(88.dp).background(colors.primaryContainer,RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){
                Icon(current.icon,null,Modifier.size(38.dp),tint=colors.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.setup_title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(current.title),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(current.description),style=MaterialTheme.typography.bodyLarge,color=colors.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.setup_optional),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        Button(onClick={
            if(!pending){
                if(ContextCompat.checkSelfPermission(context,current.permission)==PackageManager.PERMISSION_GRANTED)next()
                else{
                    if(current.permission==Manifest.permission.RECORD_AUDIO)context.getSharedPreferences("sera_call_audio_permissions",Context.MODE_PRIVATE).edit().putBoolean("asked",true).apply()
                    pending=true
                    launcher.launch(current.permission)
                }
            }
        },enabled=!pending,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("sera_permission_allow")){Text(stringResource(R.string.setup_allow))}
        TextButton(onClick={next()},enabled=!pending,modifier=Modifier.fillMaxWidth().testTag("sera_permission_skip")){Text(stringResource(R.string.setup_not_now))}
        TextButton(onClick={complete()},enabled=!pending,modifier=Modifier.fillMaxWidth().testTag("sera_permission_skip_all")){Text(stringResource(R.string.setup_skip_all))}
    }
}
