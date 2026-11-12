package et.sera.nativeapp

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network-backed paging foundation. A Room RemoteMediator will replace direct
 * network paging once the per-account offline database is migrated and tested.
 */
class SeraPostPagingSource(private val token: String, private val personalized:Boolean=false) : PagingSource<Int, SeraPost>() {
    override fun getRefreshKey(state: PagingState<Int, SeraPost>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SeraPost> {
        val page = params.key ?: 0
        return try {
            val result = withContext(Dispatchers.IO) { loadSeraFeed(token, page, personalized=personalized) }
            val posts = result.first ?: return LoadResult.Error(IllegalStateException(result.second))
            LoadResult.Page(
                data = posts,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (posts.size < 20) null else page + 1
            )
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }
}

fun pagedSeraPosts(token: String, personalized:Boolean=false) = Pager(
    config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5, enablePlaceholders = false),
    pagingSourceFactory = { SeraPostPagingSource(token,personalized) }
).flow
