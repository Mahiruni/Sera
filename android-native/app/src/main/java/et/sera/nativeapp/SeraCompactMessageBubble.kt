package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared production layout. No descendant may fill the entire available row merely to align metadata. */
@Composable
internal fun SeraCompactMessageBubble(own:Boolean,color:Color,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Surface(
        shape=seraChatBubbleShape(own),
        color=color,modifier=modifier,
        border=BorderStroke(0.5.dp,if(own)seraChatColors().outgoingBorder else seraChatColors().border)
    ){
        Column(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
    }
}

@Composable
internal fun ColumnScope.SeraCompactReceipt(time:String,own:Boolean,delivered:Boolean,read:Boolean,foreground:Color,edited:Boolean=false){
    Row(Modifier.align(Alignment.End).padding(top=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        if(edited)Text("Edited ·",color=foreground,style=seraChatMeta(MaterialTheme.typography.labelSmall))
        Text(time,color=foreground,style=seraChatMeta(MaterialTheme.typography.labelSmall))
        if(own&&!read){
            val status=if(delivered)"Delivered" else "Sent"
            val tint by animateColorAsState(if(delivered)seraChatColors().read else foreground,tween(150),label="Receipt tint")
            Crossfade(targetState=status,animationSpec=tween(120),modifier=Modifier.size(16.dp),label="Receipt status"){state->
                Icon(if(state=="Sent")Icons.Outlined.Check else Icons.Outlined.DoneAll,contentDescription=state,tint=tint,modifier=Modifier.fillMaxSize())
            }
        }
    }
}
