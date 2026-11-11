package et.sera.nativeapp

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
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
    var repostTarget by remember { mutableStateOf<SeraPost?>(null) }
    var repostNote by remember { mutableStateOf("") }
    var repostMessage by remember { mutableStateOf("") }
    var reposting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val expandedPosts = remember { mutableStateMapOf<String, Boolean>() }
    PullToRefreshBox(
        isRefreshing = posts.loadState.refresh is LoadState.Loading,
        onRefresh = { posts.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                top = 73.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The app chrome already owns the brand/title. Keep the feed editorial.

            items(posts.itemCount, key = { index -> posts.peek(index)?.id ?: "placeholder-$index" }) { index ->
                val post = posts[index]
                if (post != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE4EEEC)),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            if (post.repostOf != null) Text(
                                "↻ Reposted from ${post.repostAuthor ?: "a professional"}",
                                color = feedTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                Box(
                                    Modifier.size(43.dp).clip(CircleShape).background(Color(0xFFE1F5F2)),
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
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(post.author, color = feedInk, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif, fontSize = 14.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    val details = listOfNotNull(post.headline.takeIf { it.isNotBlank() },
                                        postTimeLabel(post.publishedAt).takeIf { it.isNotBlank() }).joinToString(" · ")
                                    Text(details, color = feedMuted, fontSize = 11.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                Text(post.kind.replaceFirstChar { it.uppercase() }, color = feedTeal,
                                    fontWeight = FontWeight.Medium, fontSize = 10.sp)
                            }
                            // A composer may store the same copy in title and body.
                            // Never render identical content twice in a feed card.
                            val title = post.title.trim()
                            val body = post.body.trim()
                            if (title.isNotBlank() && title != body) {
                                Text(title, color = feedInk, fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp)
                            }
                            val readableBody = body.ifBlank { if (title.isNotBlank()) title else "" }
                            if (readableBody.isNotBlank()) {
                                Text(readableBody, color = feedInk, fontFamily = FontFamily.SansSerif,
                                    fontSize = 14.sp, lineHeight = 21.sp,
                                    maxLines = if (expandedPosts[post.id] == true) Int.MAX_VALUE else 7,
                                    overflow = TextOverflow.Ellipsis)
                                if (readableBody.length > 330) TextButton(
                                    onClick = { expandedPosts[post.id] = expandedPosts[post.id] != true },
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text(if (expandedPosts[post.id] == true) "Show less" else "Read more",
                                    color = feedTeal, fontSize = 12.sp) }
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
                            HorizontalDivider(color = Color(0xFFE9F0F0), thickness = .7.dp)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val likeTint = if (post.liked) feedTeal else feedMuted
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center) {
                                    IconButton(onClick = { onLike(post) }, modifier = Modifier.size(48.dp)) {
                                        SeraReactionGlyph(SeraReactionKind.LIKE, active = post.liked)
                                    }
                                    if (post.likes > 0) Text(post.likes.toString(), fontSize = 11.sp, color = likeTint)
                                }
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center) {
                                    IconButton(onClick = { commentsPostId = post.id }, modifier = Modifier.size(48.dp)) {
                                        SeraReactionGlyph(SeraReactionKind.COMMENT)
                                    }
                                    if (post.comments > 0) Text(post.comments.toString(), fontSize = 11.sp, color = feedMuted)
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    IconButton(onClick = {
                                        repostTarget = post
                                        repostNote = ""
                                        repostMessage = ""
                                    }, modifier = Modifier.size(48.dp)) {
                                        SeraReactionGlyph(SeraReactionKind.REPOST)
                                    }
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "https://sera.hisabtech.com/posts/" + post.id)
                                        }
                                        context.startActivity(Intent.createChooser(intent, shareTitle))
                                    }, modifier = Modifier.size(48.dp)) {
                                        SeraReactionGlyph(SeraReactionKind.SHARE)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (posts.loadState.refresh is LoadState.Loading && posts.itemCount == 0) item { SeraLoadingCards(count = 3) }
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
    repostTarget?.let { selected ->
        SeraSignatureSheet(onDismissRequest = { if (!reposting) repostTarget = null }) {
            Column(
                Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Repost to your network",
                            style = MaterialTheme.typography.titleLarge, color = feedInk,
                            fontWeight = FontWeight.Bold)
                        Text("Share a professional insight with proper attribution.",
                            style = MaterialTheme.typography.bodySmall, color = feedMuted)
                    }
                    TextButton(onClick = { repostTarget = null }, enabled = !reposting) {
                        Text("Close", color = feedTeal)
                    }
                }
                HorizontalDivider(color = Color(0xFFE0ECEA))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = Color(0xFFF0F8F7),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ORIGINAL UPDATE", fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, color = feedTeal)
                            Text(selected.author, color = feedInk,
                                fontWeight = FontWeight.SemiBold)
                            Text((selected.body.ifBlank { selected.title }).take(260),
                                maxLines = 4, overflow = TextOverflow.Ellipsis,
                                color = feedMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = repostNote,
                        onValueChange = { if (it.length <= 400) repostNote = it },
                        label = { Text("Your perspective (optional)") },
                        placeholder = { Text("Why is this useful to your network?") },
                        supportingText = { Text("${repostNote.length} / 400") },
                        shape = RoundedCornerShape(18.dp), maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !reposting
                    )
                    if (repostMessage.isNotBlank())
                        Text(repostMessage, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = Color(0xFFE0ECEA))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { repostTarget = null },
                        enabled = !reposting,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (reposting) return@Button
                            reposting = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    repostNativePost(token, selected.repostOf ?: selected.id, repostNote)
                                }
                                reposting = false
                                if (result.first) { repostTarget = null; posts.refresh() }
                                else repostMessage = result.second
                            }
                        },
                        enabled = !reposting,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = feedTeal),
                        modifier = Modifier.weight(1.5f).heightIn(min = 50.dp)
                    ) {
                        if (reposting) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White,
                                strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Reposting…")
                        } else {
                            SeraReactionGlyph(SeraReactionKind.REPOST, active = true)
                            Spacer(Modifier.width(8.dp))
                            Text("Repost")
                        }
                    }
                }
            }
        }
    }
    expandedImage?.let { url -> SeraMediaLightbox(url, stringResource(R.string.post_image), onDismiss = { expandedImage = null }) }
    commentsPostId?.let { id -> SeraCommentsSheet(token, id, onDismiss = { commentsPostId = null }) }
}

private fun postTimeLabel(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault("")
