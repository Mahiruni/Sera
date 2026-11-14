package et.sera.nativeapp

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network-backed paging foundation. A Room RemoteMediator will replace direct
 * network paging once the per-account offline database is migrated and tested.
 */
class SeraPostPagingSource(private val token: String, private val personalized:Boolean=false,private val savedOnly:Boolean=false,private val onFirstPage:((List<SeraPost>)->Unit)?=null,private val onBasePage:((List<SeraPost>)->Unit)?=null) : PagingSource<Int, SeraPost>() {
    override fun getRefreshKey(state: PagingState<Int, SeraPost>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SeraPost> {
        val page = params.key ?: 0
        return try {
            val firstPublicPage=page==0&&!personalized&&!savedOnly
            // Do not let a slow network generation hold Paging in Loading forever.
            val result = withTimeoutOrNull(40_000L) {
                runInterruptible(Dispatchers.IO) {
                    loadSeraFeed(token,page,personalized=personalized,savedOnly=savedOnly,onBasePosts=if(firstPublicPage)onBasePage else null)
                }
            } ?: return LoadResult.Error(IllegalStateException("Feed request timed out. Please retry."))
            val posts = result.first ?: return LoadResult.Error(IllegalStateException(result.second))
            if(firstPublicPage)onFirstPage?.invoke(posts)
            LoadResult.Page(
                data = posts,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (posts.size < 20) null else page + 1
            )
        } catch (cancelled:kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }
}

fun pagedSeraPosts(token: String, personalized:Boolean=false,savedOnly:Boolean=false,onFirstPage:((List<SeraPost>)->Unit)?=null,onBasePage:((List<SeraPost>)->Unit)?=null) = Pager(
    config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5, maxSize = 120, enablePlaceholders = false),
    pagingSourceFactory = { SeraPostPagingSource(token,personalized,savedOnly,onFirstPage,onBasePage) }
).flow

/** In-memory account/token-scoped feed generations; cancellation drops all cached pages. */
internal class SeraFeedSession(private val token:String,context:android.content.Context,private val user:String){
    private val cacheLock=Any()
    private var closed=false
    private val cache by lazy{runCatching{SeraFeedPreviewCache(context.applicationContext,user)}.getOrNull()}
    fun readPreview():List<SeraFeedPreview> = synchronized(cacheLock){if(closed)emptyList() else runCatching{cache?.read().orEmpty()}.getOrDefault(emptyList())}
    private val mutablePreview=MutableStateFlow<List<SeraFeedPreview>?>(null)
    val preview=mutablePreview.asStateFlow()
    private fun publishPreview(rows:List<SeraPost>) { mutablePreview.value=seraFeedPreviewRows(rows) }
    private fun storePreview(rows:List<SeraPost>) { scope.launch(Dispatchers.IO){synchronized(cacheLock){if(!closed)runCatching{cache?.write(rows)};Unit}} }
    fun clearOnSignOut()=synchronized(cacheLock){closed=true;runCatching{cache?.clear()};Unit}
    private val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.Main.immediate)
    private val streams=mutableMapOf<Pair<Boolean,Boolean>,kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<SeraPost>>>()
    private val revisions=mutableMapOf<Pair<Boolean,Boolean>,Int>()
    fun stream(personalized:Boolean,saved:Boolean)=streams.getOrPut(personalized to saved){pagedSeraPosts(token,personalized,saved,::storePreview,::publishPreview).cachedIn(scope)}
    fun changed(personalized:Boolean,saved:Boolean,revision:Int):Boolean {
        val previous=revisions.put(personalized to saved,revision)
        return previous!=null&&previous!=revision
    }
    fun close(){synchronized(cacheLock){closed=true};scope.cancel();streams.clear();revisions.clear()}
}
