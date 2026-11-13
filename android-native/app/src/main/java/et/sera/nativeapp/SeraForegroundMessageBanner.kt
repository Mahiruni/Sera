package et.sera.nativeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal data class SeraMessageBannerEvent(val owner:String,val thread:String,val id:String,val kind:String){
    fun destination()=if(kind=="message")"message:$thread:$owner" else "company_message"
}

@Composable internal fun SeraForegroundMessageBanner(modifier:Modifier=Modifier,onOpen:(String)->Unit){
    val event=SeraMessageAlerts.banner
    LaunchedEffect(event?.id){if(event!=null){delay(5000);SeraMessageAlerts.dismissBanner()}}
    AnimatedVisibility(visible=event!=null,modifier=modifier.statusBarsPadding().padding(horizontal=12.dp,vertical=8.dp),enter=slideInVertically{ -it }+fadeIn(),exit=fadeOut()){
        if(event!=null)Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=6.dp,tonalElevation=3.dp){
            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFF087E83)){Icon(Icons.Outlined.Message,null,Modifier.padding(12.dp),tint=Color.White)}
                TextButton(onClick={SeraMessageAlerts.dismissBanner();onOpen(event.destination())},modifier=Modifier.weight(1f)){
                    Column(Modifier.fillMaxWidth()){Text("New SERA message",color=MaterialTheme.colorScheme.onSurface);Text("Open your private conversation",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                IconButton(onClick={SeraMessageAlerts.dismissBanner()}){Icon(Icons.Outlined.Close,"Dismiss message notification")}
            }
        }
    }
}
