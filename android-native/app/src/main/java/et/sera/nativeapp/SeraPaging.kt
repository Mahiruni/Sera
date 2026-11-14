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

internal const val SERA_ANDROID_FEED_PAGE_SIZE=5

/** A separate account/mode-scoped stream of optional details. Invalidated generations cannot overwrite current rows. */
internal class SeraFeedEnrichment {
    private val mutable=MutableStateFlow<Map<String,SeraPost>>(emptyMap())
    val rows=mutable.asStateFlow()
    private var generation=0L
    @Synchronized fun begin():Long { generation++;mutable.value=emptyMap();return generation }
    @Synchronized fun publish(expected:Long,posts:List<SeraPost>){
        if(expected!=generation)return
        mutable.value=(mutable.value+posts.associateBy{it.id}).entries.toList().takeLast(120).associate{it.key to it.value}
    }
    @Synchronized fun clear(){generation++;mutable.value=emptyMap()}
}

internal class SeraPostPagingSource(
    private val token:String,private val personalized:Boolean=false,private val savedOnly:Boolean=false,
    private val onFirstPage:((List<SeraPost>)->Unit)?=null,
    private val enrichment:SeraFeedEnrichment=SeraFeedEnrichment()
):PagingSource<Int,SeraPost>(){
    private val generation=enrichment.begin()
    private val worker=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.IO)
    init{registerInvalidatedCallback{worker.cancel()}}
    override fun getRefreshKey(state:PagingState<Int,SeraPost>):Int? {
        val anchor=state.anchorPosition?:return null
        val page=state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1)?:page?.nextKey?.minus(1)
    }
    override suspend fun load(params:LoadParams<Int>):LoadResult<Int,SeraPost>{
        val page=params.key?:0
        return try{
            val result=withTimeoutOrNull(24_000L){
                runInterruptible(Dispatchers.IO){loadSeraFeedBase(token,page,personalized=personalized,savedOnly=savedOnly,pageSize=SERA_ANDROID_FEED_PAGE_SIZE)}
            }?:return LoadResult.Error(IllegalStateException("Feed request timed out. Please retry."))
            val posts=result.first?:return LoadResult.Error(IllegalStateException(result.second))
            android.util.Log.d("SeraFeed","Returning page to UI: ${posts.size} rows")
            if(page==0&&!personalized&&!savedOnly)onFirstPage?.invoke(posts)
            if(posts.isNotEmpty())worker.launch{
                val details=withTimeoutOrNull(35_000L){runInterruptible(Dispatchers.IO){enrichSeraFeed(token,posts)}}?.first
                if(details!=null&&!invalid){
                    enrichment.publish(generation,details)
                    android.util.Log.d("SeraFeed","Optional details ready: ${details.size} rows")
                    if(page==0&&!personalized&&!savedOnly)onFirstPage?.invoke(details)
                }else android.util.Log.w("SeraFeed","Optional details unavailable; base posts retained")
            }
            LoadResult.Page(posts,if(page==0)null else page-1,if(posts.size<SERA_ANDROID_FEED_PAGE_SIZE)null else page+1)
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(error:Exception){LoadResult.Error(error)}
    }
}

internal fun pagedSeraPosts(token:String,personalized:Boolean=false,savedOnly:Boolean=false,onFirstPage:((List<SeraPost>)->Unit)?=null,enrichment:SeraFeedEnrichment=SeraFeedEnrichment())=Pager(
    config=PagingConfig(pageSize=SERA_ANDROID_FEED_PAGE_SIZE,initialLoadSize=SERA_ANDROID_FEED_PAGE_SIZE,prefetchDistance=2,maxSize=120,enablePlaceholders=false),
    pagingSourceFactory={SeraPostPagingSource(token,personalized,savedOnly,onFirstPage,enrichment)}
).flow

/** Account-scoped feed generations; cancellation drops all cached pages and details. */
internal class SeraFeedSession(private val token:String,context:android.content.Context,private val user:String){
    private val cacheLock=Any()
    private var closed=false
    private val cache by lazy{runCatching{SeraFeedPreviewCache(context.applicationContext,user)}.getOrNull()}
    fun readPreview():List<SeraFeedPreview> = synchronized(cacheLock){if(closed)emptyList() else runCatching{cache?.read().orEmpty()}.getOrDefault(emptyList())}
    private val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.Main.immediate)
    private val streams=mutableMapOf<Pair<Boolean,Boolean>,kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<SeraPost>>>()
    private val details=mutableMapOf<Pair<Boolean,Boolean>,SeraFeedEnrichment>()
    private val revisions=mutableMapOf<Pair<Boolean,Boolean>,Int>()
    fun enrichment(personalized:Boolean,saved:Boolean)=details.getOrPut(personalized to saved){SeraFeedEnrichment()}
    private fun storePreview(rows:List<SeraPost>){scope.launch(Dispatchers.IO){synchronized(cacheLock){if(!closed)runCatching{cache?.write(rows)};Unit}}}
    fun stream(personalized:Boolean,saved:Boolean)=streams.getOrPut(personalized to saved){pagedSeraPosts(token,personalized,saved,::storePreview,enrichment(personalized,saved)).cachedIn(scope)}
    fun changed(personalized:Boolean,saved:Boolean,revision:Int):Boolean{val previous=revisions.put(personalized to saved,revision);return previous!=null&&previous!=revision}
    fun clearOnSignOut()=synchronized(cacheLock){closed=true;runCatching{cache?.clear()};Unit}
    fun close(){synchronized(cacheLock){closed=true};scope.cancel();streams.clear();details.values.forEach{it.clear()};details.clear();revisions.clear()}
}
