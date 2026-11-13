package et.sera.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val controlTeal=Color(0xFF087E83)
private val controlInk=Color(0xFF183B40)
private val controlRed=Color(0xFFB42334)
private val audiences=listOf("public" to "Public","connections" to "Connections only","followers" to "Followers only","only_me" to "Only me")
private fun audienceDescription(key:String)=when(key){"connections"->"Your accepted professional connections";"followers"->"Members who follow you";"only_me"->"Visible only to you";else->"Anyone signed in to SERA"}
private fun audienceIcon(key:String)=when(key){"connections","followers"->Icons.Outlined.PeopleOutline;"only_me"->Icons.Outlined.Lock;else->Icons.Outlined.Public}
internal val seraMultilineKeyboard=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences,imeAction=ImeAction.Default)

private fun managePost(token:String,id:String,action:String,audience:String?=null):Pair<Boolean,String>{
 val r=spaceCall(token,"sera_manage_post",JSONObject().put("p_id",id).put("p_action",action).put("p_audience",audience?:JSONObject.NULL))
 return (r.first!=null) to if(r.first!=null)"" else "Unable to complete this action. Check your connection and try again."
}

private fun editPostText(token:String,post:SeraPost,title:String,body:String):Pair<Boolean,String>{
 // Use the authenticated database write path. The existing publication gate
 // rejects unmoderated edits when enforcement is enabled.
 val r=spaceCall(token,"sera_edit_post_text",JSONObject().put("p_id",post.id).put("p_title",title).put("p_body",body).put("p_note",if(post.repostOf!=null)body else JSONObject.NULL))
 return (r.first!=null) to if(r.first!=null)"" else "Changes were not published. Please retry."
}
@Composable internal fun SeraConfirmAction(title:String,message:String,primary:String,secondary:String="Cancel",destructive:Boolean=false,busy:Boolean=false,onCancel:()->Unit,onConfirm:()->Unit){
 Dialog(onDismissRequest={if(!busy)onCancel()}) {
  Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface){
   Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(if(destructive)Color(0xFFFCE8EA)else Color(0xFFE5F5F2),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(if(destructive)Icons.Outlined.DeleteOutline else Icons.Outlined.Edit,null,tint=if(destructive)controlRed else controlTeal)};Spacer(Modifier.weight(1f));IconButton(onClick=onCancel,enabled=!busy){Icon(Icons.Outlined.Close,"Close")}}
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
    Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick=onCancel,enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(secondary)}
    Button(onClick=onConfirm,enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=if(destructive)controlRed else controlTeal)){Text(if(busy)"Please wait…" else primary)}
   }
  }
 }
}
@Composable private fun PostOption(icon:ImageVector,label:String,color:Color=controlInk,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
  Icon(icon,null,tint=color,modifier=Modifier.size(22.dp));Text(label,color=color,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null,tint=color,modifier=Modifier.size(18.dp))
 }
}
@Composable internal fun SeraPostOptions(token:String,post:SeraPost,owner:Boolean,onChanged:(String)->Unit){
 var panel by remember(post.id){mutableStateOf("")}
 var privacy by remember(post.id,post.audience){mutableStateOf(post.audience)}
 var title by rememberSaveable(post.id){mutableStateOf(post.title)}
 var body by rememberSaveable(post.id){mutableStateOf(if(post.repostOf!=null)post.repostNote else post.body)}
 var confirm by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var copied by remember{mutableStateOf(false)}
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 val link="https://sera.hisabtech.com/posts/"+post.id
 fun close(){if(!busy){panel="";error="";confirm=""}}
 fun requestClose(){if(busy)return;if(panel=="edit"&&(title!=post.title||body!=(if(post.repostOf!=null)post.repostNote else post.body)))confirm="discard" else close()}
 fun runAction(action:String){
  if(busy)return;busy=true;error=""
  val chosen=privacy;val savedTitle=title;val savedBody=body
  scope.launch{
   val result=withContext(Dispatchers.IO){if(action=="edit")editPostText(token,post,savedTitle,savedBody)else managePost(token,post.id,action,chosen)}
   busy=false
   if(result.first){confirm="";panel="";onChanged(action)}else{error=result.second;confirm=""}
  }
 }
 IconButton(onClick={panel="menu";error="";copied=false},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.MoreHoriz,"Post options",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
 if(panel.isNotBlank())SeraSignatureSheet(onDismissRequest={requestClose()},allowPartial=panel=="menu"||panel=="privacy",canDismiss=!busy&&(panel!="edit"||(title==post.title&&body==(if(post.repostOf!=null)post.repostNote else post.body)))){
  val keyboard=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
  Column(Modifier.fillMaxWidth().heightIn(max=680.dp).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){Text(when(panel){"edit"->"Edit post";"privacy"->"Who can see your post?";"link"->"Share this post";else->"Post options"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick={requestClose()},enabled=!busy){Icon(Icons.Outlined.Close,"Close")}}
   if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=18.dp))
   when(panel){
    "menu"->{
     if(owner){PostOption(Icons.Outlined.Edit,"Edit post"){title=post.title;body=if(post.repostOf!=null)post.repostNote else post.body;panel="edit"};PostOption(Icons.Outlined.Lock,"Edit privacy"){privacy=post.audience;panel="privacy"}}
     PostOption(Icons.Outlined.ContentCopy,"Copy link"){panel="link"}
     if(owner){HorizontalDivider(Modifier.padding(horizontal=18.dp));PostOption(Icons.Outlined.DeleteOutline,"Move to bin",controlRed){confirm="trash"}}
    }
    "link"->{
     Column(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFF0F7F6)){Column(Modifier.fillMaxWidth().padding(14.dp)){Text(post.author,fontWeight=FontWeight.SemiBold);Text((if(post.repostOf!=null)post.originalBody.orEmpty() else post.body).take(160),style=MaterialTheme.typography.bodySmall)}}
      OutlinedTextField(link,{},readOnly=true,label={Text("Post link")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
      Text(if(post.audience=="public")"Share a direct link to this post." else "Only people with permission can open this post.",style=MaterialTheme.typography.bodySmall)
      Button(onClick={val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;clipboard.setPrimaryClip(ClipData.newPlainText("SERA post",link));copied=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=controlTeal)){Icon(Icons.Outlined.ContentCopy,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(copied)"Link copied" else "Copy link")}
      TextButton(onClick={close()},modifier=Modifier.fillMaxWidth()){Text("Close")}
     }
    }
    "privacy"->{
     Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      audiences.forEach{(key,label)->Surface(shape=RoundedCornerShape(16.dp),color=if(privacy==key)Color(0xFFE5F5F2)else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().clickable(enabled=!busy){privacy=key}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(audienceIcon(key),null,tint=controlTeal);Column(Modifier.weight(1f)){Text(label,fontWeight=FontWeight.SemiBold);Text(audienceDescription(key),style=MaterialTheme.typography.bodySmall)};RadioButton(selected=privacy==key,onClick={privacy=key},enabled=!busy)}}}
     }
     Button(onClick={runAction("privacy")},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=controlTeal)){Text(if(busy)"Saving…" else "Save privacy")}
    }
    "edit"->{
     Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      if(post.repostOf==null)OutlinedTextField(title,{title=it.take(140)},label={Text("Headline")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!busy,shape=RoundedCornerShape(14.dp))
      val limit=if(post.repostOf!=null)400 else if(post.kind=="quote")600 else 10000
      OutlinedTextField(body,{body=it.take(limit)},label={Text(if(post.repostOf!=null)"Your thoughts" else "Your post")},modifier=Modifier.fillMaxWidth().heightIn(min=if(keyboard)86.dp else 200.dp),singleLine=false,minLines=if(keyboard)2 else 7,maxLines=if(keyboard)5 else 14,keyboardOptions=seraMultilineKeyboard,enabled=!busy,shape=RoundedCornerShape(18.dp),supportingText={Text("${body.length} / $limit")})
      if(post.repostOf==null)post.imageUrl?.let{SeraMediaImage(it,"Existing attachment",640,480,Modifier.fillMaxWidth().height(140.dp),accessToken=token)}
     }
     Button(onClick={confirm="edit"},enabled=!busy&&(post.repostOf!=null||(post.kind=="quote"&&body.trim().length>=3)||(post.kind=="photos"&&title.trim().length>=3)||(title.trim().length>=3&&body.isNotBlank())),modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=controlTeal)){Text(if(busy)"Saving…" else "Save changes")}
    }
   }
  }
 }
 when(confirm){
  "trash"->SeraConfirmAction("Move this post to your bin?","This post will be removed from your feed and profile. You can restore it within 30 days.","Move to bin",destructive=true,busy=busy,onCancel={confirm=""},onConfirm={runAction("trash")})
  "edit"->SeraConfirmAction("Publish your changes?","Your updated post will replace the current version. Existing likes and comments will remain.","Publish changes","Keep editing",busy=busy,onCancel={confirm=""},onConfirm={runAction("edit")})
  "discard"->SeraConfirmAction("Keep your changes?","You have unsaved edits. Continue editing or discard them.","Discard changes","Keep editing",destructive=true,onCancel={confirm=""},onConfirm={title=post.title;body=if(post.repostOf!=null)post.repostNote else post.body;close()})
 }
}

@Composable internal fun SeraPostBin(token:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var rows by remember(token){mutableStateOf<List<JSONObject>>(emptyList())}
 var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)}
 var refresh by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf<JSONObject?>(null)}
 var action by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 var preview by remember{mutableStateOf<JSONObject?>(null)}
 val scope=rememberCoroutineScope()
 androidx.activity.compose.BackHandler(enabled=!busy,onBack=onBack)
 LaunchedEffect(token,refresh){loading=true;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_list_post_bin")};if(r.first!=null){rows=spaceRows(r.first);error=""}else error="Unable to load your bin. Please retry.";loading=false}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack,enabled=!busy){Text("Back")};Text("Bin",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={refresh++},enabled=!loading){Text("Refresh")}}
  Text("Deleted posts stay here for 30 days. Only you can access them.",style=MaterialTheme.typography.bodySmall)
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){
   items(rows,key={it.optString("id")}){r->
    ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Text(r.optString("title").ifBlank{"Post"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
     Text(r.optString("body").take(220),maxLines=3,style=MaterialTheme.typography.bodyMedium)
     val days=runCatching{java.time.Duration.between(java.time.Instant.now(),java.time.OffsetDateTime.parse(r.optString("expires_at")).toInstant()).toDays().coerceAtLeast(0)+1}.getOrDefault(0)
     Text("Deleted "+r.optString("deleted_at").take(10)+" · $days days remaining",style=MaterialTheme.typography.labelSmall,color=SeraPurple)
     TextButton(onClick={preview=r}){Text("Preview")}
     Button(onClick={selected=r;action="restore"},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=controlTeal)){Text("Restore")}
     TextButton(onClick={selected=r;action="purge"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Delete permanently",color=controlRed)}
    }}
   }
   if(rows.isEmpty()&&!loading&&error.isBlank())item{Column(Modifier.fillMaxWidth().padding(36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Outlined.DeleteOutline,null,tint=controlTeal,modifier=Modifier.size(40.dp));Text("Your bin is empty",style=MaterialTheme.typography.titleMedium);Text("Deleted posts will appear here.",style=MaterialTheme.typography.bodySmall)}}
  }
 }
 preview?.let{r->SeraSignatureSheet(onDismissRequest={preview=null}){Column(Modifier.fillMaxWidth().heightIn(max=600.dp).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(r.optString("title"),style=MaterialTheme.typography.titleLarge);Text(r.optString("body"),Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()));TextButton(onClick={preview=null},modifier=Modifier.fillMaxWidth()){Text("Close")}}}}
 selected?.let{r->SeraConfirmAction(if(action=="restore")"Restore this post?" else "Delete this post permanently?",if(action=="restore")"Your post will return to your profile and become available to its permitted audience." else "This cannot be undone. The post and its associated content will be permanently removed.",if(action=="restore")"Restore post" else "Delete permanently",if(action=="restore")"Cancel" else "Keep post",destructive=action=="purge",busy=busy,onCancel={selected=null},onConfirm={
  if(!busy){busy=true;val operation=action;scope.launch{val result=withContext(Dispatchers.IO){managePost(token,r.optString("id"),operation)};busy=false;selected=null;if(result.first){refresh++}else error=result.second}}
 })}
}

@Composable internal fun SeraContentSettings(token:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var bin by remember{mutableStateOf(false)}
 var sounds by remember{mutableStateOf(false)}
 var identity by remember{mutableStateOf(false)}
 if(identity){SeraIdentityVerification(token,modifier){identity=false};return}
 if(sounds){SeraSoundSettings(modifier){sounds=false};return}
 if(bin){SeraPostBin(token,modifier){bin=false};return}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Back")};Text("Settings",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
  ElevatedCard(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(8.dp)){TextButton(onClick={identity=true},modifier=Modifier.fillMaxWidth()){Text("Identity verification")};Text("Verify your professional identity",Modifier.padding(horizontal=18.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)}}
  ElevatedCard(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(8.dp)){PostOption(Icons.Outlined.DeleteOutline,"Bin",controlTeal){bin=true};Text("Manage and restore deleted posts",Modifier.padding(horizontal=18.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)}}
  ElevatedCard(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(8.dp)){TextButton(onClick={sounds=true},modifier=Modifier.fillMaxWidth()){Text("Notification sounds")};Text("Customize SERA alerts or use Android defaults",Modifier.padding(horizontal=18.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)}}
 }
}
