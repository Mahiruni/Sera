package et.sera.nativeapp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable internal fun SeraCachedPostCard(post:SeraFeedPreview){
 var expanded by remember(post.id){mutableStateOf(false)}
 val colors=MaterialTheme.colorScheme
 Column(Modifier.fillMaxWidth().padding(vertical=10.dp,horizontal=2.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer),contentAlignment=Alignment.Center){Text(post.author.take(1).uppercase(),color=colors.primary,fontWeight=FontWeight.Bold,fontSize=19.sp)}
   Column(Modifier.weight(1f)){
    Text(post.author,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=1)
    if(post.headline.isNotBlank())Text(post.headline,color=colors.onSurfaceVariant,fontSize=11.sp,maxLines=1)
    Text(post.publishedAt.take(10),color=colors.onSurfaceVariant,fontSize=11.sp)
   }
  }
  SeraFeedCopy(post.title,post.body,expanded,{expanded=it})
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
   Text("${post.likes} likes",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
   Text("${post.comments} comments",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
   Text("${post.shares} shares",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
  }
 }
}
