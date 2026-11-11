package et.sera.nativeapp

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems

private val feedTeal = Color(0xFF087E83)
private val feedInk = Color(0xFF183B40)
private val feedMuted = Color(0xFF667A7E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraPagedFeed(token: String, refreshKey: Int, onLike: (SeraPost) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_post)
    val stream = remember(token, refreshKey) { pagedSeraPosts(token) }
    val posts = stream.collectAsLazyPagingItems()
    var commentsPostId by remember { mutableStateOf<String?>(null) }
    var expandedImage by remember { mutableStateOf<String?>(null) }
    PullToRefreshBox(
        isRefreshing = posts.loadState.refresh is LoadState.Loading,
        onRefresh = { posts.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.feed_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = feedInk)
                    Text(stringResource(R.string.feed_subtitle), style = MaterialTheme.typography.bodySmall, color = feedMuted)
                }
            }
            items(posts.itemCount, key = { index -> posts.peek(index)?.id ?: "placeholder-$index" }) { index ->
                val post = posts[index]
                if (post != null) {
                    ElevatedCard(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                Box(
                                    Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE1F5F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (post.avatarUrl != null) {
                                        SeraMediaImage(
                                            url = post.avatarUrl,
                                            description = stringResource(R.string.profile_photo),
                                            widthPx = 128, heightPx = 128,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(post.author.take(1).uppercase(), color = feedTeal, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(post.author, color = feedInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(post.headline.ifBlank { stringResource(R.string.professional) }, color = feedMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Surface(color = Color(0xFFE7F5F3), shape = RoundedCornerShape(10.dp)) {
                                    Text(post.kind.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 11.sp, color = feedTeal)
                                }
                            }
                            // A composer may store the same copy in title and body.
                            // Never render identical content twice in a feed card.
                            val title = post.title.trim()
                            val body = post.body.trim()
                            if (title.isNotBlank() && title != body) {
                                Text(title, color = feedInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            }
                            if (body.isNotBlank()) {
                                Text(body, color = feedInk, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
                            } else if (title.isNotBlank()) {
                                Text(title, color = feedInk, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
                            }
                            post.imageUrl?.let { url ->
                                SeraMediaImage(
                                    url = url,
                                    description = stringResource(R.string.post_image),
                                    widthPx = 1080, heightPx = 720,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { expandedImage = url }
                                )
                            }
                            HorizontalDivider(color = Color(0xFFE9F0F0))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { onLike(post) }) {
                                    Text((if (post.liked) "♥ " else "♡ ") + post.likes, color = if (post.liked) feedTeal else feedMuted)
                                }
                                TextButton(onClick = { commentsPostId = post.id }) {
                                    Text("◌ " + post.comments, color = feedMuted)
                                }
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://sera.hisabtech.com/posts/" + post.id)
                                    }
                                    context.startActivity(Intent.createChooser(intent, shareTitle))
                                }) { Text("↗ " + stringResource(R.string.share), color = feedMuted) }
                            }
                        }
                    }
                }
            }
            if (posts.loadState.append is LoadState.Loading) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val error = (posts.loadState.refresh as? LoadState.Error)?.error
                ?: (posts.loadState.append as? LoadState.Error)?.error
            if (error != null) item {
                Column {
                    Text(error.message ?: "Unable to load feed.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { posts.retry() }) { Text(stringResource(R.string.retry)) }
                }
            }
            if (posts.loadState.refresh is LoadState.NotLoading && posts.itemCount == 0) item {
                Text(stringResource(R.string.feed_empty))
            }
        }
    }
    expandedImage?.let { url -> SeraMediaLightbox(url, stringResource(R.string.post_image), onDismiss = { expandedImage = null }) }
    commentsPostId?.let { id -> SeraCommentsSheet(token, id, onDismiss = { commentsPostId = null }) }
}
