package et.sera.nativeapp

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val deepTeal = Color(0xFF087E83)
private val darkInk = Color(0xFF183B40)

@Composable
fun SeraFeed(posts: List<SeraPost>, loading: Boolean, error: String, onRefresh: () -> Unit, onLike: (SeraPost) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Your professional world", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = darkInk)
            Text("Ideas, talent and opportunities", color = Color(0xFF63787C))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh feed") }
            if (loading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (!loading && error.isBlank() && posts.isEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("Your feed is quiet right now. Published updates will appear here.", color = darkInk)
            }
        }
        items(posts, key = { it.id }) { post ->
            ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE1F5F2)), contentAlignment = Alignment.Center) {
                            Text(post.author.take(1).uppercase(), color = deepTeal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(post.author, color = darkInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(post.headline.ifBlank { "SERA professional" }, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(post.kind.uppercase(), color = deepTeal, fontSize = 10.sp)
                    }
                    if (post.title.isNotBlank()) Text(post.title, color = darkInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    if (post.body.isNotBlank()) Text(post.body, color = darkInk, maxLines = 12, overflow = TextOverflow.Ellipsis)
                    post.imageUrl?.let { url ->
                        AsyncImage(model = url, contentDescription = "Post image", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    HorizontalDivider(color = Color(0xFFE6EEEE))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onLike(post) }) { Text((if (post.liked) "♥ " else "♡ ") + post.likes, color = deepTeal) }
                        Text("Comments " + post.comments, color = Color.Gray, fontSize = 12.sp)
                        TextButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://sera.hisabtech.com/posts/" + post.id)
                            }
                            context.startActivity(Intent.createChooser(share, "Share SERA post"))
                        }) { Text("Share") }
                    }
                }
            }
        }
    }
}
