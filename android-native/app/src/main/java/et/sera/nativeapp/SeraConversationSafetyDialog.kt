package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

@Composable internal fun SeraConversationSafetyDialog(name:String,onDismiss:()->Unit,onBlock:()->Unit,onReport:(String,String)->Unit){
    var mode by remember{mutableStateOf("")}
    var reason by remember{mutableStateOf("spam")}
    var details by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(when(mode){"block"->"Block $name?";"report"->"Report $name";else->"Safety and privacy"})},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            when(mode){
                "block"->Text("You will no longer be able to message each other or access the conversation. You can manage blocks from your profile.")
                "report"->{Text("Choose a reason. Reports go to the moderation team.");listOf("spam","harassment","impersonation","inappropriate","other").forEach{r->Row{RadioButton(selected=reason==r,onClick={reason=r});Text(r,Modifier.padding(top=14.dp))}};OutlinedTextField(details,{details=it.take(1000)},label={Text("Details (optional)")})}
                else->{Text("SERA conversations require accepted connections. You can block or report unwanted behavior.");TextButton(onClick={mode="block"}){Text("Block member",color=MaterialTheme.colorScheme.error)};TextButton(onClick={mode="report"}){Text("Report spam or abuse")}}
            }
        }
    },confirmButton={when(mode){"block"->TextButton(onClick=onBlock){Text("Block")};"report"->TextButton(onClick={onReport(reason,details)}){Text("Submit report")};else->TextButton(onClick=onDismiss){Text("Done")}}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
