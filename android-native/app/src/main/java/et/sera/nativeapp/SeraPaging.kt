package et.sera.nativeapp

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network-backed paging foundation. A Room RemoteMediator will replace direct
 * network paging once the per-account offline database is migrated and tested.
 */
class SeraPostPagingSource(private val token: String, private val personalized:Boolean=false,private val savedOnly:Boolean=false,private val onFirstPage:((List<SeraPost>)->Unit)?=null) : PagingSource<Int, SeraPost>() {
    override fun getRefreshKey(state: PagingState<Int, SeraPost>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SeraPost> {
        val page = params.key ?: 0
        return try {
            val result = withContext(Dispatchers.IO) { loadSeraFeed(token, page, personalized=personalized,savedOnly=savedOnly).also{if(page==0&&!personalized&&!savedOnly)it.first?.let{rows->onFirstPage?.invoke(rows)}} }
            val posts = result.first ?: return LoadResult.Error(IllegalStateException(result.second))
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

fun pagedSeraPosts(token: String, personalized:Boolean=false,savedOnly:Boolean=false,onFirstPage:((List<SeraPost>)->Unit)?=null) = Pager(
    config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5, maxSize = 120, enablePlaceholders = false),
    pagingSourceFactory = { SeraPostPagingSource(token,personalized,savedOnly,onFirstPage) }
).flow

/** In-memory account/token-scoped feed generations; cancellation drops all cached pages. */
internal class SeraFeedSession(private val token:String,context:android.content.Context,private val user:String){
    private val cacheLock=Any()
    private var closed=false
    private val cache by lazy{runCatching{SeraFeedPreviewCache(context.applicationContext,user)}.getOrNull()}
    fun readPreview():List<SeraFeedPreview> = synchronized(cacheLock){if(closed)emptyList() else runCatching{cache?.read().orEmpty()}.getOrDefault(emptyList())}
    private fun storePreview(rows:List<SeraPost>)=synchronized(cacheLock){if(!closed)runCatching{cache?.write(rows)};Unit}
    fun clearOnSignOut()=synchronized(cacheLock){closed=true;runCatching{cache?.clear()};Unit}
    private val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.Main.immediate)
    private val streams=mutableMapOf<Pair<Boolean,Boolean>,kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<SeraPost>>>()
    private val revisions=mutableMapOf<Pair<Boolean,Boolean>,Int>()
    fun stream(personalized:Boolean,saved:Boolean)=streams.getOrPut(personalized to saved){pagedSeraPosts(token,personalized,saved,::storePreview).cachedIn(scope)}
    fun changed(personalized:Boolean,saved:Boolean,revision:Int):Boolean {
        val previous=revisions.put(personalized to saved,revision)
        return previous!=null&&previous!=revision
    }
    fun close(){synchronized(cacheLock){closed=true};scope.cancel();streams.clear();revisions.clear()}
}
