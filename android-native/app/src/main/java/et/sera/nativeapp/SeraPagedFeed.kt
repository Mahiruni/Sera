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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.FormatQuote
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems

private val feedTeal = Color(0xFF0D7C86)
private val feedInk = Color(0xFF16363A)
private val feedMuted = Color(0xFF667A7E)

@OptIn(ExperimentalMaterial3Api::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun SeraPagedFeed(token: String, refreshKey: Int, onLike: (SeraPost) -> Unit, modifier: Modifier = Modifier, onAtTop:()->Unit = {},savedOnly:Boolean=false,composerName:String="",composerAvatar:String?=null,canCompose:Boolean=true,onCompose:(String?)->Unit={},session:SeraFeedSession?=null,retainedListState:androidx.compose.foundation.lazy.LazyListState?=null,homeReselect:Int=0,activeCall:Boolean=false,retainedPersonalized:Boolean=false,onPersonalized:(Boolean)->Unit={}) {
    val context = LocalContext.current
    val link=LocalSeraLink.current
    val feedInk=MaterialTheme.colorScheme.onSurface
    val feedMuted=MaterialTheme.colorScheme.onSurfaceVariant
    val feedTeal=MaterialTheme.colorScheme.primary
    val localListState=rememberLazyListState()
    val listState=retainedListState?:localListState
    val topCallback by rememberUpdatedState(onAtTop)
    LaunchedEffect(listState){snapshotFlow{listState.firstVisibleItemIndex==0&&listState.firstVisibleItemScrollOffset==0}.collect{if(it)topCallback()}}
    val shareTitle = stringResource(R.string.share_post)
    var personalized by remember(token){mutableStateOf(retainedPersonalized)}
    val enrichment=remember(token,session,personalized,savedOnly){session?.enrichment(personalized,savedOnly)?:SeraFeedEnrichment()}
    val enrichedRows by enrichment.rows.collectAsState()
    val stream = remember(token,session,personalized,savedOnly) { session?.stream(personalized,savedOnly)?:pagedSeraPosts(token,personalized,savedOnly,enrichment=enrichment) }
    val posts = stream.collectAsLazyPagingItems()
    LaunchedEffect(posts.itemCount){android.util.Log.d("SeraFeed","UI paging items: ${posts.itemCount}")}
    var cachedPreview by remember(session,personalized,savedOnly){mutableStateOf<List<SeraFeedPreview>>(emptyList())}
    LaunchedEffect(session,personalized,savedOnly){if(!personalized&&!savedOnly)cachedPreview=withContext(Dispatchers.IO){session?.readPreview().orEmpty()}}
    val previews=cachedPreview
    val showPreview=!personalized&&!savedOnly&&posts.itemCount==0&&posts.loadState.refresh !is LoadState.NotLoading&&previews.isNotEmpty()
    val refreshing=posts.loadState.refresh is LoadState.Loading
    var retryAttempt by remember(stream){mutableIntStateOf(0)}
    var slowLoad by remember(stream){mutableStateOf(false)}
    LaunchedEffect(stream,refreshing,retryAttempt){
        slowLoad=false
        if(refreshing){kotlinx.coroutines.delay(8_000L);slowLoad=true}
    }
    fun retryFeed(){retryAttempt++;slowLoad=false;if(posts.loadState.refresh is LoadState.Error)posts.retry() else posts.refresh()}
    var previousRevision by remember(token,personalized,savedOnly){mutableIntStateOf(refreshKey)}
    LaunchedEffect(refreshKey,session,personalized,savedOnly){val changed=session?.changed(personalized,savedOnly,refreshKey)?: (previousRevision!=refreshKey);previousRevision=refreshKey;if(changed)posts.refresh()}
    var lastReselect by remember{mutableIntStateOf(homeReselect)}
    LaunchedEffect(homeReselect){if(homeReselect!=lastReselect){lastReselect=homeReselect;if(!savedOnly){if(listState.firstVisibleItemIndex==0&&listState.firstVisibleItemScrollOffset==0)posts.refresh() else listState.animateScrollToItem(0)}}}
    var newHead by remember(token,personalized,savedOnly){mutableStateOf<String?>(null)}
    val displayedHead=posts.itemSnapshotList.items.firstOrNull()?.let{SeraFeedHead(it.id,it.publishedAt)}
    val currentHead by rememberUpdatedState(displayedHead)
    LaunchedEffect(displayedHead){if(displayedHead?.id==newHead)newHead=null}
    val freshnessLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(token,personalized,savedOnly,activeCall,freshnessLifecycle,link){if(link.available&&!personalized&&!savedOnly&&token.isNotBlank())freshnessLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){if(!activeCall){val baseline=currentHead;if(baseline!=null){val result=withContext(Dispatchers.IO){seraLatestFeedHead(token)};val remote=result.first;val current=currentHead;if(result.second.isBlank()&&remote!=null&&current!=null){newHead=remote.id.takeIf{seraFeedHeadIsNewer(remote,current)}}}};kotlinx.coroutines.delay(seraSecondarySyncDelay(link,activeCall,60000L))}}}
    val authorIds = posts.itemSnapshotList.items.map { it.authorId }.distinct().take(100)
    var activeAuthors by remember(token) { mutableStateOf<Set<String>>(emptySet()) }
    val presenceLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(token,authorIds,presenceLifecycle,link,activeCall) { if(!link.available||activeCall){activeAuthors=emptySet();return@LaunchedEffect};presenceLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { while(true) { activeAuthors=withContext(Dispatchers.IO){feedPresence(token,authorIds)} ?: emptySet(); kotlinx.coroutines.delay(seraSecondarySyncDelay(link,false,30000L)) } } }

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
        isRefreshing = refreshing&&!slowLoad,
        onRefresh = { if(!refreshing||slowLoad)retryFeed() },
        modifier = modifier.fillMaxSize().semantics{testTagsAsResourceId=true}
    ) {
        Box(Modifier.fillMaxSize()){
        LazyColumn(
            state=listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                top = 73.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(if(savedOnly)10.dp else 0.dp)
        ) {
            item(key="feed-mode") { if(savedOnly)Text(stringResource(R.string.saved_posts),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp)) else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!personalized,onClick={personalized=false;onPersonalized(false)},label={Text(stringResource(R.string.latest))},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer));FilterChip(selected=personalized,onClick={personalized=true;onPersonalized(true)},label={Text(stringResource(R.string.for_you))},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer))} }
            if(!savedOnly) item(key="home-compose") { Box(Modifier.padding(top=6.dp,bottom=10.dp)){SeraHomeComposeCard(composerName,composerAvatar,canCompose,onCompose)} }
            // The app chrome already owns the brand/title. Keep the feed editorial.

            items(posts.itemCount, key = { index -> posts.peek(index)?.id ?: "placeholder-$index" }) { index ->
                val basePost = posts[index]
                val post=basePost?.let{enrichedRows[it.id]?:it}
                val detailsReady=post!=null&&enrichedRows.containsKey(post.id)
                if (post != null) {
                    Surface(
                        shape = RoundedCornerShape(if(savedOnly)20.dp else 0.dp),
                        border = if(savedOnly)androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant) else null,
                        color = if(savedOnly)MaterialTheme.colorScheme.surface else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(horizontal = if(savedOnly)12.dp else 2.dp).testTag("sera_feed_post_"+post.id),
                            verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            if(!savedOnly && index>0) SeraFeedDivider()
                            Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp).testTag("sera_feed_author"),verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
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
                                Column(Modifier.weight(1f).padding(top=2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                                    Text(post.author,color=feedInk,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,lineHeight=18.sp,modifier=Modifier.weight(1f,fill=false))
                                    if(post.identityVerified)SeraIdentityBadge()
                                    }
                                    if(post.headline.isNotBlank()) Text(post.headline,color=feedMuted,fontSize=11.sp,lineHeight=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    val details = (if(post.repostOf!=null)"Reposted · " else "")+postTimeLabel(post.publishedAt)
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                        if(post.authorId in activeAuthors) { SeraOnlineDot() }
                                        Text(details+(if(post.editedAt!=null)" · Edited" else ""), color = feedMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                SeraPostOptions(token,post,currentUserId.isNotBlank()&&post.authorId==currentUserId){ posts.refresh() }
                            }
                            val title = (if(post.repostOf!=null)post.originalTitle.orEmpty() else post.title).trim()
                            val body = (if(post.repostOf!=null)post.originalBody.orEmpty() else post.body).trim()
                            val postBody: @Composable () -> Unit = {
                                SeraFeedCopy(title,body,expandedPosts[post.id]==true,{expandedPosts[post.id]=it},postId=post.id)
                                post.imageUrl?.let { url ->
                                    SeraMediaImage(url=url,description=stringResource(R.string.post_image),widthPx=1080,heightPx=720,accessToken=token,previewUrl=post.imagePreviewUrl,
                                        modifier=Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=160.dp,max=340.dp).clip(RoundedCornerShape(14.dp)).clickable{expandedImage=url})
                                }
                            }
                            if(post.repostOf!=null){
                                if(post.repostNote.isNotBlank()) Text(post.repostNote,color=feedInk,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.padding(bottom=8.dp))
                                Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=.65f),shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(.7.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f)),modifier=Modifier.fillMaxWidth().testTag("sera_feed_repost")){
                                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled=post.originalBody!=null){nativePostId=post.repostOf},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                            SeraAccountAvatar(post.repostAuthor?:"Original post",post.originalAvatar,32)
                                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                                                Text(post.repostAuthor?:"Original post unavailable",color=feedInk,fontWeight=FontWeight.SemiBold,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                                Text("Original · "+postTimeLabel(post.originalTime.orEmpty()),color=feedMuted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                            }
                                            if(post.originalBody!=null)Text("›",fontSize=20.sp,color=feedMuted)
                                        }
                                        postBody()
                                    }
                                }
                            }else postBody()
                            Row(
                                Modifier.fillMaxWidth().padding(top=3.dp,bottom=5.dp).testTag("sera_feed_actions"),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val reaction=reactionOverrides[post.id]?: (post.liked to post.likes)
                                val repostState=repostOverrides[post.repostOf?:post.id]?: (post.reposted to post.repostCount)
                                val saved=saveOverrides[post.id]?:post.saved
                                FeedReactionControl(SeraReactionKind.LIKE,reaction.second,reaction.first,Modifier.weight(1f),enabled=detailsReady&&reactionPending[post.id]!=true){
                                    val previous=reactionOverrides[post.id]?: (post.liked to post.likes)
                                    reactionOverrides[post.id]=(!previous.first to (previous.second+if(previous.first)-1 else 1).coerceAtLeast(0))
                                    reactionPending[post.id]=true
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    scope.launch{val result=withContext(Dispatchers.IO){setSeraLike(token,post.id,previous.first)};if(!result.first){reactionOverrides[post.id]=previous;reactionError=result.second};reactionPending.remove(post.id)}
                                }
                                FeedReactionControl(SeraReactionKind.COMMENT,post.comments,false,Modifier.weight(1f)){commentsPostId=post.id}
                                FeedReactionControl(SeraReactionKind.REPOST,repostState.second,repostState.first,Modifier.weight(1f),enabled=detailsReady){repostTarget=post;withThoughts=false;repostNote="";repostMessage=""}
                                FeedReactionControl(SeraReactionKind.SHARE,post.shares,false,Modifier.weight(1f)){
                                    val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"https://sera.hisabtech.com/posts/"+post.id)}
                                    context.startActivity(Intent.createChooser(intent,shareTitle))
                                }
                                FeedReactionControl(SeraReactionKind.SAVE,0,saved,Modifier.weight(1f),enabled=detailsReady&&savePending[post.id]!=true){
                                    val previous=saveOverrides[post.id]?:post.saved
                                    saveOverrides[post.id]=!previous;savePending[post.id]=true
                                    scope.launch{val result=withContext(Dispatchers.IO){setSeraSaved(token,post.id,previous)};if(!result.first){saveOverrides[post.id]=previous;reactionError=result.second}else if(savedOnly)posts.refresh();savePending.remove(post.id)}
                                }
                            }
                        }
                    }
                }
            }
            if(showPreview){
                item(key="cached_feed_notice"){
                    Text(stringResource(R.string.feed_cached_preview),color=feedMuted,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp).testTag("sera_cached_feed_notice"))
                }
                items(previews.size,key={index->"cached-"+previews[index].id}){index->
                    val preview=previews[index]
                    Column(Modifier.fillMaxWidth().padding(vertical=10.dp,horizontal=2.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(preview.author,color=feedInk,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        if(preview.title.isNotBlank())Text(preview.title,color=feedInk,fontWeight=FontWeight.Medium)
                        if(preview.body.isNotBlank())Text(preview.body,color=feedInk,fontSize=14.sp,lineHeight=20.sp,maxLines=8,overflow=TextOverflow.Ellipsis)
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            if(refreshing&&posts.itemCount==0&&slowLoad) item(key="feed-slow") { SeraFeedLoadingNotice(onRetry=::retryFeed) }
            if (refreshing && posts.itemCount == 0 && !showPreview && !slowLoad) item { SeraLoadingCards(count = 3) }
            if (posts.loadState.append is LoadState.Loading) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val error = (posts.loadState.refresh as? LoadState.Error)?.error
                ?: (posts.loadState.append as? LoadState.Error)?.error
            if (error != null) item {
                Column {
                    Text(error.message ?: "Unable to load feed.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { retryFeed() }) { Text(stringResource(R.string.retry)) }
                }
            }
            if (posts.loadState.refresh is LoadState.NotLoading && posts.itemCount == 0) item {
                Text(stringResource(R.string.feed_empty))
            }
        }
        if(newHead!=null&&!savedOnly&&!personalized&&posts.loadState.refresh !is LoadState.Loading){
            FilledTonalButton(onClick={scope.launch{listState.scrollToItem(0);posts.refresh()}},modifier=Modifier.align(Alignment.TopCenter).padding(top=72.dp).testTag("sera_new_posts")){Text(stringResource(R.string.new_feed_posts))}
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { repostTarget = null },
                        enabled = !reposting,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                    ) { Text(stringResource(R.string.cancel)) }
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
        SeraReactionGlyph(kind,active,Modifier.size(21.dp).graphicsLayer{scaleX=scale;scaleY=scale},tint=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if(count>0){Spacer(Modifier.width(3.dp));Text(if(count>=1000)"${count/1000}k" else count.toString(),fontSize=11.sp,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
    }
}

@Composable
private fun SeraHomeComposeCard(name:String,avatar:String?,enabled:Boolean,onCompose:(String?)->Unit){
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(20.dp),color=colors.surface,border=androidx.compose.foundation.BorderStroke(1.dp,colors.outlineVariant),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled=enabled){onCompose(null)}.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                SeraAccountAvatar(name.ifBlank{"Sera member"},avatar,38)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(stringResource(R.string.share_something),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=colors.onSurface)
                    Text(stringResource(R.string.ideas_projects),fontSize=12.sp,color=colors.onSurfaceVariant)
                }
                Surface(shape=RoundedCornerShape(12.dp),color=colors.primaryContainer){Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){Icon(Icons.Outlined.EditNote,"Create post",tint=colors.onPrimaryContainer)}}
            }
            HorizontalDivider(thickness=.5.dp,color=colors.outlineVariant.copy(alpha=.6f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                listOf(Triple("Photo","photos",Icons.Outlined.Image),Triple("Project","project",Icons.Outlined.WorkOutline),Triple("Thought","quote",Icons.Outlined.FormatQuote)).forEach{(label,kind,icon)->
                    TextButton(onClick={onCompose(kind)},enabled=enabled,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=3.dp,vertical=8.dp)){
                        Icon(icon,null,Modifier.size(19.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(label,fontSize=12.sp,maxLines=1)
                    }
                }
            }
        }
    }
}

/** Keep each post on one quiet editorial baseline. */
@Composable internal fun SeraFeedDivider(){
    HorizontalDivider(Modifier.fillMaxWidth().testTag("sera_feed_separator"),thickness=1.dp,color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
}

/** Separate a stored title from the body without duplicating it or deleting prose. */
internal fun seraFeedCopyParts(title:String,body:String):Pair<String,String>{
    val t=title.trim();val b=body.trim()
    if(t.isBlank())return "" to b
    if(b.isBlank()||t.equals(b,ignoreCase=true))return t to ""
    if(b.startsWith(t,ignoreCase=true)&&b.length>t.length&&b[t.length].isWhitespace())return t to b.substring(t.length).trimStart()
    val compactTitle=t.replace(Regex("\\s+")," ");val compactBody=b.replace(Regex("\\s+")," ")
    if(compactBody.equals(compactTitle,ignoreCase=true)||compactBody.startsWith(compactTitle+" ",ignoreCase=true))return "" to b
    return t to b
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun SeraFeedCopy(title:String,body:String,expanded:Boolean,onExpanded:(Boolean)->Unit,postId:String?=null){
    val textTag=if(postId==null)Modifier else Modifier.testTag("sera_feed_text_"+postId).semantics{testTagsAsResourceId=true}
    val (heading,copy)=remember(title,body){seraFeedCopyParts(title,body)}
    var overflowed by remember(title,body){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().testTag("sera_feed_copy"),verticalArrangement=Arrangement.spacedBy(3.dp)){
        if(heading.isNotBlank())Text(heading,modifier=textTag,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp,maxLines=if(copy.isBlank()&&!expanded)5 else Int.MAX_VALUE,overflow=TextOverflow.Ellipsis,onTextLayout={if(copy.isBlank()&&!expanded)overflowed=it.hasVisualOverflow})
        if(copy.isNotBlank())Text(copy,modifier=if(heading.isBlank())textTag else Modifier,color=MaterialTheme.colorScheme.onSurface,fontSize=14.sp,lineHeight=20.sp,maxLines=if(expanded)Int.MAX_VALUE else 5,overflow=TextOverflow.Ellipsis,onTextLayout={if(!expanded)overflowed=it.hasVisualOverflow})
        if(overflowed||expanded)Box(Modifier.heightIn(min=36.dp).clip(RoundedCornerShape(8.dp)).clickable(role=Role.Button){onExpanded(!expanded)}.padding(end=16.dp).testTag("sera_feed_expand"),contentAlignment=Alignment.CenterStart){Text(if(expanded)"Show less" else "Read more",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold,fontSize=12.sp)}
    }
}

/** A recoverable state, not a permanent loading skeleton. */
@Composable
internal fun SeraFeedLoadingNotice(onRetry:()->Unit){
    Column(Modifier.fillMaxWidth().padding(vertical=18.dp,horizontal=4.dp).testTag("sera_feed_slow"),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(stringResource(R.string.feed_loading_slow),color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=onRetry,modifier=Modifier.testTag("sera_feed_retry")){Text(stringResource(R.string.retry))}
    }
}
