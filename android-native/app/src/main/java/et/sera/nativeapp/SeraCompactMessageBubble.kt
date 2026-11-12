package et.sera.nativeapp

import androidx.compose.foundation.layout.*
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
        shape=if(own)RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=20.dp,bottomEnd=6.dp)
        else RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=6.dp,bottomEnd=20.dp),
        color=color,modifier=modifier
    ){
        Column(Modifier.padding(horizontal=12.dp,vertical=7.dp),content=content)
    }
}

@Composable
internal fun ColumnScope.SeraCompactReceipt(time:String,own:Boolean,delivered:Boolean,read:Boolean,foreground:Color){
    Row(Modifier.align(Alignment.End).padding(top=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
        Text(time,color=foreground,fontSize=10.sp)
        if(own){
            val status=if(read)"Read" else if(delivered)"Delivered" else "Sent"
            Icon(if(delivered||read)Icons.Outlined.DoneAll else Icons.Outlined.Check,contentDescription=status,
                tint=if(read)Color(0xFFB5FFF4)else foreground,modifier=Modifier.size(15.dp))
        }
    }
}
