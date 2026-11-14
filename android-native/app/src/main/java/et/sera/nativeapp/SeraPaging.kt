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
    private val directFeeds=mutableMapOf<Pair<Boolean,Boolean>,SeraDirectFeed>()
    fun direct(personalized:Boolean,saved:Boolean)=directFeeds.getOrPut(personalized to saved){SeraDirectFeed(token,personalized,saved,::storePreview)}
    fun enrichment(personalized:Boolean,saved:Boolean)=details.getOrPut(personalized to saved){SeraFeedEnrichment()}
    private fun storePreview(rows:List<SeraPost>){scope.launch(Dispatchers.IO){synchronized(cacheLock){if(!closed)runCatching{cache?.write(rows)};Unit}}}
    fun stream(personalized:Boolean,saved:Boolean)=streams.getOrPut(personalized to saved){pagedSeraPosts(token,personalized,saved,::storePreview,enrichment(personalized,saved)).cachedIn(scope)}
    fun changed(personalized:Boolean,saved:Boolean,revision:Int):Boolean{val previous=revisions.put(personalized to saved,revision);return previous!=null&&previous!=revision}
    fun clearOnSignOut()=synchronized(cacheLock){closed=true;runCatching{cache?.clear()};Unit}
    fun close(){synchronized(cacheLock){closed=true};directFeeds.values.forEach{it.close()};directFeeds.clear();scope.cancel();streams.clear();details.values.forEach{it.clear()};details.clear();revisions.clear()}
}

// Direct publication from the IO worker avoids a suspended PagingSource handoff.
// Each mode and authenticated session has independent state and generation.
internal data class SeraFeedState(
    val rows:List<SeraPost> = emptyList(),
    val details:Map<String,SeraPost> = emptyMap(),
    val refreshing:Boolean=true,
    val appending:Boolean=false,
    val error:String="",
    val nextPage:Int=0,
    val hasMore:Boolean=true
)
internal class SeraDirectFeed(
    private val token:String,
    private val personalized:Boolean,
    private val savedOnly:Boolean,
    private val onFirstPage:((List<SeraPost>)->Unit)?=null,
    private val fetch: (Int) -> Pair<List<SeraPost>?, String> = {page->loadSeraFeedBase(token,page,personalized=personalized,savedOnly=savedOnly,pageSize=SERA_ANDROID_FEED_PAGE_SIZE)},
    private val enrich: (List<SeraPost>) -> List<SeraPost>? = {rows->enrichSeraFeed(token,rows).first}
){
    private val mutable=MutableStateFlow(SeraFeedState())
    val state=mutable.asStateFlow()
    private val worker=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.IO)
    private var generation=0L
    private var closed=false
    private var started=false
    private var baseJob:kotlinx.coroutines.Job?=null
    private val detailJobs=mutableListOf<kotlinx.coroutines.Job>()
    @Synchronized fun start(){if(!closed&&!started)refresh()}
    @Synchronized fun refresh(){
        if(closed)return
        started=true
        generation++
        baseJob?.cancel()
        detailJobs.forEach{it.cancel()};detailJobs.clear()
        mutable.value=mutable.value.copy(refreshing=true,appending=false,error="",nextPage=0,hasMore=true)
        load(generation,0)
    }
    @Synchronized fun append(){
        val s=mutable.value
        if(closed||!started||s.refreshing||s.appending||!s.hasMore||s.error.isNotBlank())return
        mutable.value=s.copy(appending=true)
        load(generation,s.nextPage)
    }
    @Synchronized fun retry(){
        val s=mutable.value
        if(s.nextPage==0||s.refreshing||s.rows.isEmpty())refresh()
        else {mutable.value=s.copy(error="");append()}
    }
    private fun load(expected:Long,page:Int){
        baseJob=worker.launch{
            try{
                val response=fetch(page)
                val rows=response.first
                if(rows==null){fail(expected,response.second.ifBlank{"Unable to load posts."});return@launch}
                if(!publish(expected,page,rows))return@launch
                if(page==0&&!personalized&&!savedOnly)onFirstPage?.invoke(rows)
                scheduleDetails(expected,page,rows)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){android.util.Log.w("SeraFeed","Direct feed failed: "+e.javaClass.simpleName);fail(expected,"Unable to load posts. Please retry.")}
        }
    }
    @Synchronized private fun publish(expected:Long,page:Int,rows:List<SeraPost>):Boolean{
        if(closed||expected!=generation)return false
        val prior=mutable.value
        val combined=if(page==0)rows.distinctBy{it.id} else (prior.rows+rows).distinctBy{it.id}
        mutable.value=prior.copy(rows=combined,details=if(page==0)emptyMap() else prior.details,refreshing=false,appending=false,error="",nextPage=page+1,hasMore=rows.size==SERA_ANDROID_FEED_PAGE_SIZE)
        android.util.Log.d("SeraFeed","Direct feed published: ${combined.size} rows")
        return true
    }
    @Synchronized private fun fail(expected:Long,message:String){
        if(!closed&&expected==generation)mutable.value=mutable.value.copy(refreshing=false,appending=false,error=message)
    }
    @Synchronized private fun scheduleDetails(expected:Long,page:Int,rows:List<SeraPost>){
        if(closed||expected!=generation||rows.isEmpty())return
        detailJobs.removeAll{it.isCompleted}
        detailJobs.add(worker.launch{
            try{
                val details=enrich(rows)?:return@launch
                if(publishDetails(expected,details)&&page==0&&!personalized&&!savedOnly)onFirstPage?.invoke(details)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){android.util.Log.w("SeraFeed","Optional details failed: "+e.javaClass.simpleName)}
        })
    }
    @Synchronized private fun publishDetails(expected:Long,rows:List<SeraPost>):Boolean{
        if(closed||expected!=generation)return false
        mutable.value=mutable.value.copy(details=mutable.value.details+rows.associateBy{it.id})
        android.util.Log.d("SeraFeed","Direct feed details ready: ${rows.size} rows")
        return true
    }
    @Synchronized fun close(){closed=true;generation++;worker.cancel();mutable.value=SeraFeedState(refreshing=false,hasMore=false)}
}
