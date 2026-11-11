package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraPagedFeed(token: String, refreshKey: Int, onLike: (SeraPost) -> Unit, modifier: Modifier = Modifier) {
    val stream = remember(token, refreshKey) { pagedSeraPosts(token) }
    val posts = stream.collectAsLazyPagingItems()
    var commentsPostId by remember { mutableStateOf<String?>(null) }
    PullToRefreshBox(
        isRefreshing = posts.loadState.refresh is LoadState.Loading,
        onRefresh = { posts.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(stringResource(R.string.feed_title), style = MaterialTheme.typography.headlineSmall) }
            items(posts.itemCount) { index ->
                val post = posts[index]
                if (post != null) {
                    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                post.avatarUrl?.let { AsyncImage(model = it, contentDescription = stringResource(R.string.profile_photo), modifier = Modifier.size(42.dp)) }
                                Text(post.author, style = MaterialTheme.typography.titleMedium)
                            }
                            if (post.title.isNotBlank()) Text(post.title, style = MaterialTheme.typography.titleMedium)
                            if (post.body.isNotBlank()) Text(post.body, style = MaterialTheme.typography.bodyLarge)
                            post.imageUrl?.let {
                                AsyncImage(model = it, contentDescription = stringResource(R.string.post_image),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                                    contentScale = ContentScale.Crop)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { onLike(post) }) { Text("♡ " + post.likes) }
                                TextButton(onClick = { commentsPostId = post.id }) { Text(stringResource(R.string.comments) + " " + post.comments) }
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
    commentsPostId?.let { id -> SeraCommentsSheet(token, id, onDismiss = { commentsPostId = null }) }
}
