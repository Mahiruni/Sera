package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal fun seraCallDuration(seconds:Long):String {
    val s=seconds.coerceAtLeast(0)
    return if(s>=3600) String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60)
    else String.format(Locale.ROOT,"%02d:%02d",s/60,s%60)
}
internal data class SeraVoiceSummary(val title:String,val connectedSeconds:Long?,val incoming:Boolean,val endedAt:Long=System.currentTimeMillis(),val detail:String="")

@Composable internal fun SeraVoiceAvatar(name:String,photo:String?,accent:Color,pulsing:Boolean,modifier:Modifier=Modifier){
    val colors=MaterialTheme.colorScheme
    val pulse=if(pulsing){
        val animation=rememberInfiniteTransition(label="voice halo")
        val alpha by animation.animateFloat(.16f,.48f,infiniteRepeatable(tween(1300,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="halo opacity")
        alpha
    }else .23f
    Box(modifier.size(156.dp).border(1.dp,accent.copy(alpha=pulse),CircleShape),contentAlignment=Alignment.Center){
        Box(Modifier.size(136.dp).background(accent.copy(alpha=.07f),CircleShape).border(1.dp,accent.copy(alpha=pulse),CircleShape),contentAlignment=Alignment.Center){
            Box(Modifier.size(112.dp).clip(CircleShape).background(colors.primaryContainer),contentAlignment=Alignment.Center){
                Text(name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()},fontSize=34.sp,color=colors.onPrimaryContainer,fontWeight=FontWeight.SemiBold)
                if(!photo.isNullOrBlank())SeraMediaImage(photo,"Profile photo",224,224,Modifier.fillMaxSize())
            }
        }
    }
}

@Composable internal fun SeraVoiceControl(icon:ImageVector,label:String,active:Boolean=false,destructive:Boolean=false,accept:Boolean=false,enabled:Boolean=true,onClick:()->Unit){
    val c=MaterialTheme.colorScheme
    val haptic=LocalHapticFeedback.current
    val background by animateColorAsState(when{destructive->Color(0xFFC5374B);accept->Color(0xFF137C5B);active->c.primary;else->c.surfaceVariant},label="voice control")
    val foreground=if(destructive||accept)Color.White else if(active)c.onPrimary else c.onSurfaceVariant
    Column(Modifier.widthIn(min=72.dp),horizontalAlignment=Alignment.CenterHorizontally){
        FilledIconButton(onClick={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);onClick()},enabled=enabled,modifier=Modifier.size(if(destructive||accept)68.dp else 56.dp).testTag("sera_voice_"+label),colors=IconButtonDefaults.filledIconButtonColors(containerColor=background,contentColor=foreground)){
            Icon(icon,label,Modifier.size(if(destructive||accept)28.dp else 24.dp))
        }
        Text(label,color=c.onSurfaceVariant,fontSize=12.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=6.dp),maxLines=2)
    }
}

@Composable internal fun SeraVoiceSummaryContent(name:String,photo:String?,summary:SeraVoiceSummary,onAgain:()->Unit,onDone:()->Unit){
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(24.dp).testTag("sera_voice_summary"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
        SeraVoiceAvatar(name,photo,c.primary,false,Modifier)
        Text(summary.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=c.onSurface)
        Text(name,style=MaterialTheme.typography.titleMedium,color=c.onSurface)
        if(summary.connectedSeconds!=null)Text(seraCallDuration(summary.connectedSeconds),fontFamily=FontFamily.Monospace,fontSize=28.sp,color=c.primary,modifier=Modifier.testTag("sera_voice_summary_duration"))
        Text((if(summary.incoming)"Incoming" else "Outgoing")+" · "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(java.util.Date(summary.endedAt)),fontSize=12.sp,color=c.onSurfaceVariant,textAlign=TextAlign.Center)
        if(summary.detail.isNotBlank())Text(summary.detail,color=c.onSurfaceVariant,fontSize=13.sp,textAlign=TextAlign.Center)
        Button(onClick=onAgain,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Outlined.Call,null);Spacer(Modifier.width(8.dp));Text("Call again")}
        OutlinedButton(onClick=onDone,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(18.dp)){Text("Back to messages")}
    }
}
