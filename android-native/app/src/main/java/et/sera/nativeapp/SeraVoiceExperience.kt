package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.Icon as AndroidMaterialIcon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import android.animation.ValueAnimator
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

/** Only an unanswered call animates; a connected or reconnecting call stays still. */
internal fun seraCallShouldAnimate(started:Long,cancelling:Boolean,active:Boolean)=started==0L&&!cancelling&&active

/** The contact photo stays fixed; a single soft orbit signals dialing. */
@Composable internal fun SeraVoiceAvatar(name:String,photo:String?,accent:Color,pulsing:Boolean,modifier:Modifier=Modifier){
    val colors=MaterialTheme.colorScheme
    val animate=pulsing&&ValueAnimator.areAnimatorsEnabled()
    val angle=if(animate){
        val transition=rememberInfiniteTransition(label="call orbit")
        transition.animateFloat(0f,360f,infiniteRepeatable(tween(2300,easing=LinearEasing),RepeatMode.Restart),label="ring angle")
    }else rememberUpdatedState(0f)
    Box(modifier.size(176.dp).testTag("sera_voice_avatar_footprint"),contentAlignment=Alignment.Center){
        Box(Modifier.size(156.dp).border(1.dp,accent.copy(alpha=.15f),CircleShape))
        if(animate)Canvas(Modifier.size(156.dp).testTag("sera_call_orbit")){
            drawArc(color=accent.copy(alpha=.85f),startAngle=angle.value-90f,sweepAngle=96f,useCenter=false,style=Stroke(width=2.5.dp.toPx(),cap=StrokeCap.Round))
        }
        Box(Modifier.size(136.dp).border(1.dp,accent.copy(alpha=.24f),CircleShape))
        Box(Modifier.size(112.dp).clip(CircleShape).background(colors.primaryContainer),contentAlignment=Alignment.Center){
            Text(name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()},fontSize=34.sp,color=colors.onPrimaryContainer,fontWeight=FontWeight.SemiBold)
            if(!photo.isNullOrBlank())SeraMediaImage(photo,voiceText("Profile photo"),224,224,Modifier.fillMaxSize())
        }
    }
}

/** Bounded, privacy-conscious photo wash; no full-resolution or unblurred fallback. */
@Composable internal fun SeraCallBackdrop(photo:String?,bg:Color,halo:Color,modifier:Modifier=Modifier){
    Box(modifier.clip(RoundedCornerShape(0.dp)).background(Brush.verticalGradient(listOf(bg,halo,bg))).testTag("sera_call_backdrop")){
        if(!photo.isNullOrBlank()&&android.os.Build.VERSION.SDK_INT>=31){
            SeraMediaImage(photo,"",384,640,Modifier.fillMaxSize().blur(14.dp).alpha(.58f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha=.62f),bg.copy(alpha=.46f),bg.copy(alpha=.70f)))))
        }
    }
}

@Composable internal fun SeraVoiceControl(icon:ImageVector,label:String,active:Boolean=false,destructive:Boolean=false,accept:Boolean=false,enabled:Boolean=true,onClick:()->Unit){
    val c=MaterialTheme.colorScheme
    val haptic=LocalHapticFeedback.current
    val background by animateColorAsState(when{destructive->Color(0xFFCE3348);accept->Color(0xFF137C5B);active->c.primary;else->c.surfaceVariant},label="voice control")
    val foreground=if(destructive||accept)Color.White else if(active)c.onPrimary else c.onSurfaceVariant
    Column(Modifier.width(if(destructive||accept)72.dp else 64.dp),horizontalAlignment=Alignment.CenterHorizontally){
        FilledIconButton(onClick={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);onClick()},enabled=enabled,modifier=Modifier.size(if(destructive||accept)64.dp else 56.dp).testTag("sera_voice_"+label),colors=IconButtonDefaults.filledIconButtonColors(containerColor=background,contentColor=foreground)){
            AndroidMaterialIcon(icon,voiceText(label),Modifier.size(if(destructive||accept)28.dp else 24.dp))
        }
        Text(voiceText(label),color=c.onSurface,fontSize=11.sp,lineHeight=14.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=7.dp),maxLines=2)
    }
}

@Composable internal fun SeraVoiceSummaryContent(name:String,photo:String?,summary:SeraVoiceSummary,onAgain:()->Unit,onDone:()->Unit){
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(24.dp).testTag("sera_voice_summary"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
            SeraVoiceAvatar(name,photo,c.primary,false,Modifier)
            IconButton(onClick=onDone,modifier=Modifier.align(Alignment.TopEnd).size(44.dp).testTag("sera_voice_summary_close")){
                Box(Modifier.size(36.dp).background(c.surfaceVariant.copy(alpha=.75f),CircleShape),contentAlignment=Alignment.Center){
                    Icon(Icons.Outlined.Close,androidx.compose.ui.res.stringResource(R.string.close),tint=c.onSurfaceVariant,modifier=Modifier.size(21.dp))
                }
            }
        }
        Text(voiceText(summary.title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=c.onSurface)
        Text(name,style=MaterialTheme.typography.titleMedium,color=c.onSurface)
        if(summary.connectedSeconds!=null)Text(seraCallDuration(summary.connectedSeconds),fontFamily=FontFamily.Monospace,fontSize=28.sp,color=c.primary,modifier=Modifier.testTag("sera_voice_summary_duration"))
        Text(voiceText(if(summary.incoming)"Incoming" else "Outgoing")+" · "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(java.util.Date(summary.endedAt)),fontSize=12.sp,color=c.onSurfaceVariant,textAlign=TextAlign.Center)
        if(summary.detail.isNotBlank())Text(voiceText(summary.detail),color=c.onSurfaceVariant,fontSize=13.sp,textAlign=TextAlign.Center)
        Button(onClick=onAgain,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Outlined.Call,null);Spacer(Modifier.width(8.dp));Text(voiceText("Call again"))}
        OutlinedButton(onClick=onDone,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(18.dp)){Text(voiceText("Back to messages"))}
    }
}

/** In-flow strip, not an overlay: all page and keyboard layouts retain their own space. */
@Composable internal fun SeraOngoingCallStrip(name:String,photo:String?,status:String,muted:Boolean,onRestore:()->Unit,onMute:()->Unit,onEnd:()->Unit,canMute:Boolean){
    val c=MaterialTheme.colorScheme
    Surface(color=c.primaryContainer,shape=RoundedCornerShape(24.dp),shadowElevation=3.dp,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp).testTag("sera_ongoing_call")){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onRestore,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=2.dp)){
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface),contentAlignment=Alignment.Center){
                    Text(name.take(1).uppercase(),color=c.onSurface)
                    if(!photo.isNullOrBlank())SeraMediaImage(photo,voiceText("Profile photo"),72,72,Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)){Text(name,color=c.onPrimaryContainer,maxLines=1,fontWeight=FontWeight.SemiBold);Text(voiceText(status),color=c.onPrimaryContainer,fontSize=12.sp,maxLines=1)}
                Icon(Icons.Outlined.KeyboardArrowUp,voiceText("Return to call"),tint=c.onPrimaryContainer)
            }
            IconButton(onClick=onMute,enabled=canMute,modifier=Modifier.size(48.dp)){Icon(if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic,voiceText(if(muted)"Unmute" else "Mute"),tint=c.onPrimaryContainer)}
            IconButton(onClick=onEnd,modifier=Modifier.size(48.dp)){Box(Modifier.size(36.dp).background(Color(0xFFC5374B),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.CallEnd,voiceText("End call"),tint=Color.White,modifier=Modifier.size(19.dp))}}
        }
    }
}

/** Derived only from received audio RTP counters; no guessed quality claims. */
internal data class SeraAudioSample(val received:Long,val lost:Long,val jitterSeconds:Double?=null,val rttSeconds:Double?=null)
internal fun seraAudioQuality(previous:SeraAudioSample?,current:SeraAudioSample):String? {
    if(current.jitterSeconds?.let{it.isFinite()&&it>=.06}==true||current.rttSeconds?.let{it.isFinite()&&it>=.8}==true)return "Unstable connection"
    if(previous==null)return null
    val received=current.received-previous.received
    val lost=current.lost-previous.lost
    if(received<0||lost<0||received+lost<12)return null
    val ratio=lost.toDouble()/(received+lost)
    return if(ratio>=.08)"Unstable connection" else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SeraVoiceHistoryContent(name:String,photo:String?,events:List<SeraCallEvent>,userId:String,loading:Boolean,onCall:()->Unit,onClose:()->Unit){
    val c=MaterialTheme.colorScheme
    var filter by remember{mutableStateOf("All")}
    val filtered=events.filter{when(filter){"Missed"->it.state=="missed"&&it.incoming(userId);"Incoming"->it.incoming(userId);"Outgoing"->!it.incoming(userId);else->true}}
    Column(Modifier.fillMaxWidth().padding(20.dp).testTag("sera_voice_history"),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(voiceText("Recent calls"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(name,color=c.onSurfaceVariant,fontSize=13.sp,maxLines=1)}
            IconButton(onClick=onClose){Icon(Icons.Outlined.Close,voiceText("Close call history"))}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("All","Missed","Incoming","Outgoing").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(voiceText(label),fontSize=12.sp,maxLines=1)})}
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        else if(filtered.isEmpty())Text(voiceText("No calls in this view"),color=c.onSurfaceVariant,modifier=Modifier.padding(vertical=24.dp))
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            items(filtered,key={it.id}){event->
                val incoming=event.incoming(userId)
                val missed=incoming&&event.state=="missed"
                Surface(color=c.surfaceVariant.copy(alpha=.5f),shape=RoundedCornerShape(16.dp)){
                    Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.primaryContainer),contentAlignment=Alignment.Center){Icon(if(missed)Icons.Outlined.CallMissed else if(incoming)Icons.Outlined.CallReceived else Icons.Outlined.CallMade,null,tint=if(missed)c.error else c.onPrimaryContainer)}
                        Column(Modifier.weight(1f)){Text(voiceText(event.outcome(userId)),fontWeight=FontWeight.Medium,color=c.onSurface,fontSize=13.sp);Text(voiceText(if(incoming)"Incoming" else "Outgoing")+" · "+event.dateTime()+(event.connectedSeconds?.let{" · "+seraCallDuration(it)}?:""),fontSize=11.sp,color=c.onSurfaceVariant)}
                        IconButton(onClick=onCall){Icon(Icons.Outlined.Call,voiceText("Call again"),tint=c.primary)}
                    }
                }
            }
        }
        Button(onClick=onCall,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(18.dp)){Text(voiceText("Call again"),maxLines=1)}
    }
}
