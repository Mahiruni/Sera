package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Native, authenticated post detail. Never launches a browser. */
@Composable internal fun SeraNativePostDetail(token:String,postId:String,onClose:()->Unit){
 var post by remember(token,postId){mutableStateOf<SeraPost?>(null)}
 var loading by remember(token,postId){mutableStateOf(true)}
 var error by remember(token,postId){mutableStateOf("")}
 var reload by remember(postId){mutableIntStateOf(0)}
 var comments by remember(postId){mutableStateOf(false)}
 var photo by remember(postId){mutableStateOf<String?>(null)}
 var pending by remember(postId){mutableStateOf(false)}
 var currentUser by remember(token){mutableStateOf("")}
 LaunchedEffect(token){currentUser=withContext(Dispatchers.IO){loadSeraUserId(token).first.orEmpty()}}
 val scope=rememberCoroutineScope()
 val teal=Color(0xFF087E83)
 LaunchedEffect(token,postId,reload){
  loading=true
  val r=withContext(Dispatchers.IO){loadSeraFeed(token,postId=postId)}
  post=r.first?.firstOrNull()
  error=r.second.ifBlank{if(post==null)"This post is no longer available." else ""}
  loading=false
 }
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=Color(0xFFF5FAF9)){
   Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick=onClose){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",tint=teal)}
     Text("Post",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Color(0xFF183B40),modifier=Modifier.weight(1f));post?.let{p->SeraPostOptions(token,p,currentUser.isNotBlank()&&p.authorId==currentUser){action->if(action=="trash")onClose() else reload++}}
    }
    HorizontalDivider(color=Color(0xFFDCEAE7))
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(error.isNotBlank()){Text(error,modifier=Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error);TextButton(onClick={reload++}){Text("Retry")}}
    post?.let{p->
     Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
       if(p.avatarUrl!=null)SeraMediaImage(p.avatarUrl,"Profile photo",128,128,Modifier.size(48.dp).clip(CircleShape)) else Box(Modifier.size(48.dp).background(Color(0xFFD0EBE7),CircleShape),contentAlignment=Alignment.Center){Text(p.author.take(1),color=teal,fontWeight=FontWeight.Bold)}
       Column(Modifier.weight(1f)){Text(p.author,fontWeight=FontWeight.Bold,color=Color(0xFF183B40));if(p.headline.isNotBlank())Text(p.headline,color=teal,style=MaterialTheme.typography.labelSmall);Text(runCatching{DateTimeFormatter.ofPattern("d MMM · HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.parse(p.publishedAt))}.getOrDefault(""),style=MaterialTheme.typography.labelSmall)}
      }
      val title=if(p.repostOf!=null)p.originalTitle.orEmpty() else p.title
      val body=if(p.repostOf!=null)p.originalBody.orEmpty() else p.body
      if(p.repostOf!=null){Text("Reposted from "+(p.repostAuthor?:"original author"),color=teal);if(p.repostNote.isNotBlank())Text(p.repostNote)}
      if(title.isNotBlank()&&!body.replace(Regex("\\s+")," ").startsWith(title.replace(Regex("\\s+")," "),ignoreCase=true))Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
      Text(body.ifBlank{if(p.repostOf!=null&&p.originalBody==null)"Original post unavailable" else title},style=MaterialTheme.typography.bodyLarge)
      p.imageUrl?.let{url->SeraMediaImage(url,"Post image",1080,720,Modifier.fillMaxWidth().heightIn(min=180.dp,max=420.dp).clip(RoundedCornerShape(18.dp)).clickable{photo=url})}
      HorizontalDivider()
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround,verticalAlignment=Alignment.CenterVertically){
       TextButton(enabled=!pending,onClick={pending=true;scope.launch{val r=withContext(Dispatchers.IO){setSeraLike(token,p.id,p.liked)};pending=false;if(r.first)post=p.copy(liked=!p.liked,likes=(p.likes+if(p.liked)-1 else 1).coerceAtLeast(0))else error=r.second}}){SeraReactionGlyph(SeraReactionKind.LIKE,active=p.liked);Spacer(Modifier.width(6.dp));Text(p.likes.toString())}
       TextButton(onClick={comments=true}){SeraReactionGlyph(SeraReactionKind.COMMENT);Spacer(Modifier.width(6.dp));Text(p.comments.toString()+" comments")}
      }
     }
    }
   }
  }
 }
 if(comments)SeraCommentsSheet(token,postId,onDismiss={comments=false},onChanged={reload++})
 photo?.let{SeraMediaLightbox(it,"Post image",onDismiss={photo=null})}
}
