package et.sera.nativeapp

import android.content.Intent
import org.json.JSONObject
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.collect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems

private val feedTeal = Color(0xFF087E83)
private val feedInk = Color(0xFF183B40)
private val feedMuted = Color(0xFF667A7E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraPagedFeed(token: String, refreshKey: Int, onLike: (SeraPost) -> Unit, modifier: Modifier = Modifier, onAtTop:()->Unit = {}) {
    val context = LocalContext.current
    val listState=rememberLazyListState()
    val topCallback by rememberUpdatedState(onAtTop)
    LaunchedEffect(listState){snapshotFlow{listState.firstVisibleItemIndex==0&&listState.firstVisibleItemScrollOffset==0}.collect{if(it)topCallback()}}
    val shareTitle = stringResource(R.string.share_post)
    var personalized by remember(token){mutableStateOf(true)}
    val stream = remember(token, refreshKey,personalized) { pagedSeraPosts(token,personalized) }
    val posts = stream.collectAsLazyPagingItems()
    val authorIds = posts.itemSnapshotList.items.map { it.authorId }.distinct().take(100)
    var activeAuthors by remember(token) { mutableStateOf<Set<String>>(emptySet()) }
    val presenceLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(token,authorIds,presenceLifecycle) { presenceLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { while(true) { activeAuthors=withContext(Dispatchers.IO){feedPresence(token,authorIds)} ?: emptySet(); kotlinx.coroutines.delay(30000) } } }

    var nativePostId by remember { mutableStateOf<String?>(null) }
    var commentsPostId by remember { mutableStateOf<String?>(null) }
    var expandedImage by remember { mutableStateOf<String?>(null) }
    var repostTarget by remember { mutableStateOf<SeraPost?>(null) }
    var repostNote by remember { mutableStateOf("") }
    var repostMessage by remember { mutableStateOf("") }
    var reposting by remember { mutableStateOf(false) }
    var discardRepost by remember { mutableStateOf(false) }
    fun closeRepost(){if(!reposting){if(repostNote.isNotBlank())discardRepost=true else repostTarget=null}}
    var withThoughts by remember{mutableStateOf(false)}
    val repostOverrides=remember(token){mutableStateMapOf<String,Pair<Boolean,Int>>()}
    var currentUserId by remember(token){mutableStateOf("")}
    LaunchedEffect(token){currentUserId=withContext(Dispatchers.IO){loadSeraUserId(token).first.orEmpty()}}
    val scope = rememberCoroutineScope()
    val expandedPosts = remember { mutableStateMapOf<String, Boolean>() }
    val reactionOverrides = remember(token, refreshKey) { mutableStateMapOf<String, Pair<Boolean, Int>>() }
    val reactionPending = remember(token) { mutableStateMapOf<String, Boolean>() }
    val saveOverrides=remember(token,refreshKey){mutableStateMapOf<String,Boolean>()}
    val savePending=remember(token){mutableStateMapOf<String,Boolean>()}
    var reactionError by remember { mutableStateOf("") }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    PullToRefreshBox(
        isRefreshing = posts.loadState.refresh is LoadState.Loading,
        onRefresh = { reactionOverrides.clear(); repostOverrides.clear(); saveOverrides.clear(); posts.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state=listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                top = 73.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key="feed-mode") { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=personalized,onClick={personalized=true},label={Text("For you")});FilterChip(selected=!personalized,onClick={personalized=false},label={Text("Latest")})} }
            // The app chrome already owns the brand/title. Keep the feed editorial.

            items(posts.itemCount, key = { index -> posts.peek(index)?.id ?: "placeholder-$index" }) { index ->
                val post = posts[index]
                if (post != null) {
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                                    Text(buildAnnotatedString {
                                        withStyle(SpanStyle(color=feedInk,fontWeight=FontWeight.Bold,fontSize=14.sp)){append(post.author)}
                                        if(post.repostOf!=null) withStyle(SpanStyle(color=SeraPurple,fontWeight=FontWeight.Medium,fontSize=11.sp)){append(" | Reposted from "+(post.repostAuthor?:"original author"))}
                                    },maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=17.sp,modifier=Modifier.weight(1f,fill=false))
                                    if(post.identityVerified)SeraIdentityBadge()
                                    }
                                    if(post.headline.isNotBlank()) Text(post.headline,color=feedTeal,fontSize=11.sp,lineHeight=13.sp)
                                    val details = (if(post.repostOf!=null)"Reposted " else "")+postTimeLabel(post.publishedAt)
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                        if(post.authorId in activeAuthors) { SeraOnlineDot(); Text("Active now",color=feedTeal,fontSize=10.sp) }
                                        Text(details+(if(post.editedAt!=null)" · Edited" else ""), color = feedMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                SeraPostOptions(token,post,currentUserId.isNotBlank()&&post.authorId==currentUserId){ posts.refresh() }
                            }
                            // A composer may store the same copy in title and body.
                            // Never render identical content twice in a feed card.
                            val title = (if(post.repostOf!=null)post.originalTitle.orEmpty() else post.title).trim()
                            val body = (if(post.repostOf!=null)post.originalBody.orEmpty() else post.body).trim()
                            if(post.repostOf!=null){
                                if(post.repostNote.isNotBlank()) Text(post.repostNote,color=feedInk)
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F8F7)).clickable(enabled=post.originalBody!=null){nativePostId=post.repostOf}.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                    if(post.originalAvatar!=null)SeraMediaImage(post.originalAvatar,"Original author",96,96,modifier=Modifier.size(30.dp).clip(CircleShape))
                                    Column(Modifier.weight(1f)){Text(post.repostAuthor?:"Original post unavailable",color=feedInk,fontWeight=FontWeight.SemiBold,fontSize=12.sp);Text("Original · "+postTimeLabel(post.originalTime.orEmpty()),color=feedMuted,fontSize=11.sp)}
                                    if(post.originalBody!=null)TextButton(onClick={nativePostId=post.repostOf}){Text("›",fontSize=22.sp,color=SeraPurple)}
                                }
                            }
                            if (title.isNotBlank() && !body.replace(Regex("\\s+")," ").startsWith(title.replace(Regex("\\s+")," "),ignoreCase=true)) {
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
                                    widthPx = 1080, heightPx = 720, accessToken=token,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { expandedImage = url }
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val reaction=reactionOverrides[post.id]?: (post.liked to post.likes)
                                val repostState=repostOverrides[post.repostOf?:post.id]?: (post.reposted to post.repostCount)
                                val saved=saveOverrides[post.id]?:post.saved
                                FeedReactionControl(SeraReactionKind.LIKE,reaction.second,reaction.first,Modifier.weight(1f),enabled=reactionPending[post.id]!=true){
                                    val previous=reactionOverrides[post.id]?: (post.liked to post.likes)
                                    reactionOverrides[post.id]=(!previous.first to (previous.second+if(previous.first)-1 else 1).coerceAtLeast(0))
                                    reactionPending[post.id]=true
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    scope.launch{val result=withContext(Dispatchers.IO){setSeraLike(token,post.id,previous.first)};if(!result.first){reactionOverrides[post.id]=previous;reactionError=result.second};reactionPending.remove(post.id)}
                                }
                                FeedReactionControl(SeraReactionKind.COMMENT,post.comments,false,Modifier.weight(1f)){commentsPostId=post.id}
                                FeedReactionControl(SeraReactionKind.REPOST,repostState.second,repostState.first,Modifier.weight(1f)){repostTarget=post;withThoughts=false;repostNote="";repostMessage=""}
                                FeedReactionControl(SeraReactionKind.SHARE,post.shares,false,Modifier.weight(1f)){
                                    val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"https://sera.hisabtech.com/posts/"+post.id)}
                                    context.startActivity(Intent.createChooser(intent,shareTitle))
                                }
                                FeedReactionControl(SeraReactionKind.SAVE,0,saved,Modifier.weight(1f),enabled=savePending[post.id]!=true){
                                    val previous=saveOverrides[post.id]?:post.saved
                                    saveOverrides[post.id]=!previous;savePending[post.id]=true
                                    scope.launch{val result=withContext(Dispatchers.IO){setSeraSaved(token,post.id,previous)};if(!result.first){saveOverrides[post.id]=previous;reactionError=result.second};savePending.remove(post.id)}
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.7.dp)
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
    if(reactionError.isNotBlank()) {
        LaunchedEffect(reactionError){ android.widget.Toast.makeText(context,reactionError,android.widget.Toast.LENGTH_SHORT).show(); reactionError="" }
    }
    repostTarget?.let { selected ->
        val originalId=selected.repostOf?:selected.id
        val repostState=repostOverrides[originalId]?: (selected.reposted to selected.repostCount)
        val ownOriginal=currentUserId.isNotBlank()&&(selected.originalAuthorId?:selected.authorId)==currentUserId
        val sourceAvailable=selected.repostOf==null||selected.originalBody!=null
        SeraSignatureSheet(onDismissRequest = { closeRepost() }, allowPartial=true,canDismiss=!reposting&&repostNote.isBlank()) {
            Column(
                Modifier.fillMaxWidth(),
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
                        
                    }
                    TextButton(onClick = { closeRepost() }, enabled = !reposting) {
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
                            Text(selected.repostAuthor?:selected.author, color = feedInk,
                                fontWeight = FontWeight.SemiBold)
                            Text((if(selected.repostOf!=null)selected.originalBody.orEmpty().ifBlank{selected.originalTitle?:"Original post unavailable"} else selected.body.ifBlank{selected.title}).take(260),
                                maxLines = 4, overflow = TextOverflow.Ellipsis,
                                color = feedMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if(!repostState.first&&!ownOriginal) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!withThoughts,onClick={withThoughts=false},label={Text("Repost")});FilterChip(selected=withThoughts,onClick={withThoughts=true},label={Text("Add thoughts")})}
                    if(withThoughts&&!repostState.first) OutlinedTextField(
                        value = repostNote,
                        onValueChange = { if (it.length <= 400) repostNote = it },
                        label = { Text("Your perspective (optional)") },
                        placeholder = { Text("Why is this useful to your network?") },
                        supportingText = { Text("${repostNote.length} / 400") },
                        shape = RoundedCornerShape(18.dp), maxLines = 5, singleLine=false, keyboardOptions=seraMultilineKeyboard,
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
                                    if(repostState.first){val r=spaceCall(token,"sera_undo_repost",JSONObject().put("p_original",originalId));(r.first!=null) to r.second}else repostNativePost(token,originalId,if(withThoughts)repostNote else "")
                                }
                                reposting = false
                                if (result.first) { repostOverrides[originalId]=(!repostState.first to (repostState.second+if(repostState.first)-1 else 1).coerceAtLeast(0)); repostTarget = null; posts.refresh() }
                                else repostMessage = result.second
                            }
                        },
                        enabled = !reposting && currentUserId.isNotBlank() && (repostState.first||(!ownOriginal&&sourceAvailable)) && (!withThoughts||repostNote.isNotBlank()||repostState.first),
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
                            SeraReactionGlyph(SeraReactionKind.REPOST, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if(repostState.first)"Undo repost" else "Repost")
                        }
                    }
                }
            }
        }
    }
    if(discardRepost)SeraConfirmAction("Discard repost draft?","Your thoughts have not been published.","Discard","Keep writing",onCancel={discardRepost=false},onConfirm={discardRepost=false;repostTarget=null;repostNote=""})
    expandedImage?.let { url -> SeraMediaLightbox(url, stringResource(R.string.post_image), accessToken=token, onDismiss = { expandedImage = null }) }
    nativePostId?.let { id -> SeraNativePostDetail(token,id,onClose={nativePostId=null;posts.refresh()}) }
    commentsPostId?.let { id -> SeraCommentsSheet(token, id, onDismiss = { commentsPostId = null }, onChanged={posts.refresh()}) }
}

private fun postTimeLabel(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault("")

@Composable private fun FeedReactionControl(kind:SeraReactionKind,count:Int,active:Boolean,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit){
    val label=when(kind){SeraReactionKind.LIKE->if(active)"Unlike post" else "Like post";SeraReactionKind.COMMENT->"Comments";SeraReactionKind.REPOST->"Repost";SeraReactionKind.SHARE->"Share post";SeraReactionKind.SAVE->if(active)"Remove saved post" else "Save post"}
    Row(modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled=enabled,onClick=onClick).semantics{contentDescription=label+if(count>0)", $count" else "";selected=active},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
        val scale by animateFloatAsState(if(active&&kind==SeraReactionKind.LIKE)1.06f else 1f,label="Reaction scale")
        SeraReactionGlyph(kind,active,Modifier.graphicsLayer{scaleX=scale;scaleY=scale})
        if(count>0){Spacer(Modifier.width(3.dp));Text(if(count>=1000)"${count/1000}k" else count.toString(),fontSize=11.sp,color=if(active)feedTeal else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
    }
}
