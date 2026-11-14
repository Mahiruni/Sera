package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class SignatureTone { Primary, Secondary, Activity }
@Composable internal fun SignatureAction(label:String,colors:SignatureColors,onClick:()->Unit,modifier:Modifier=Modifier,tone:SignatureTone=SignatureTone.Primary,enabled:Boolean=true){
 val shape=RoundedCornerShape(SignatureMetrics.radius)
 if(tone==SignatureTone.Secondary){
  OutlinedButton(onClick,modifier.heightIn(min=SignatureMetrics.target),enabled=enabled,shape=shape,colors=ButtonDefaults.outlinedButtonColors(contentColor=colors.text)){Text(label)}
 }else{
  Button(onClick,modifier.heightIn(min=SignatureMetrics.target),enabled=enabled,shape=shape,colors=ButtonDefaults.buttonColors(containerColor=if(tone==SignatureTone.Activity)colors.activity else colors.primary,contentColor=if(tone==SignatureTone.Activity)colors.onActivity else colors.onPrimary)){Text(label)}
 }
}
@Composable internal fun SignatureSurface(colors:SignatureColors,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
 Surface(modifier=modifier,shape=RoundedCornerShape(SignatureMetrics.largeRadius),color=colors.surface,contentColor=colors.text,border=androidx.compose.foundation.BorderStroke(1.dp,colors.border)){
  Column(Modifier.padding(SignatureMetrics.padding),verticalArrangement=Arrangement.spacedBy(SignatureMetrics.gap),content=content)
 }
}
@Composable internal fun SignatureActivityCount(count:Int,colors:SignatureColors){
 if(count>0)Box(Modifier.defaultMinSize(minWidth=24.dp,minHeight=24.dp).background(colors.activity,CircleShape).padding(horizontal=6.dp),contentAlignment=Alignment.Center){Text(if(count>99)"99+" else count.toString(),color=colors.onActivity,fontWeight=FontWeight.Bold,fontSize=SignatureMetrics.metadata)}
}
@Composable internal fun SignatureCallAction(label:String,colors:SignatureColors,onClick:()->Unit){
 IconButton(onClick,modifier=Modifier.size(SignatureMetrics.target)){Icon(Icons.Outlined.Call,contentDescription=label,tint=colors.call)}
}
@Composable internal fun SignaturePreference(label:String,checked:Boolean,colors:SignatureColors,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=SignatureMetrics.target),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SignatureMetrics.gap)){
  Text(label,Modifier.weight(1f),color=colors.text,fontSize=SignatureMetrics.body)
  Switch(checked,onChange,colors=SwitchDefaults.colors(checkedTrackColor=colors.primary,checkedThumbColor=colors.onPrimary))
 }
}
@Composable internal fun SignatureCallConfirmation(title:String,message:String,no:String,yes:String,colors:SignatureColors,onDismiss:()->Unit,onConfirm:()->Unit){
 AlertDialog(onDismissRequest=onDismiss,shape=RoundedCornerShape(SignatureMetrics.largeRadius),containerColor=colors.surface,titleContentColor=colors.text,textContentColor=colors.text,
 icon={Icon(Icons.Outlined.Call,null,tint=colors.call)},title={Text(title)},text={Text(message)},
 dismissButton={SignatureAction(no,colors,onDismiss,tone=SignatureTone.Secondary)},confirmButton={SignatureAction(yes,colors,onConfirm)})
}
