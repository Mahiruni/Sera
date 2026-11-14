package et.sera.nativeapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** A short-lived, encrypted, account-scoped public text preview. No media or credentials. */
internal data class SeraFeedPreview(val id:String,val author:String,val title:String,val body:String,val headline:String="",val publishedAt:String="",val kind:String="",val likes:Int=0,val comments:Int=0,val shares:Int=0)
internal const val SERA_FEED_CACHE_TTL_MS=15*60*1000L
internal fun seraFeedPreviewRows(posts:List<SeraPost>):List<SeraFeedPreview> =
    posts.asSequence().filter{it.audience=="public"&&it.repostOf==null&&it.id.isNotBlank()}
        .take(40).map{SeraFeedPreview(it.id,it.author.take(120),it.title.take(300),it.body.take(12000),it.headline.take(300),it.publishedAt,it.kind,it.likes,it.comments,it.shares)}.toList()

internal class SeraFeedPreviewCache(context:Context,private val user:String){
    private val store=SeraLocalCache.get(context)
    private val prefs by lazy{EncryptedSharedPreferences.create(context.applicationContext,"sera_feed_preview_v1",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)}
    private val key="public_feed:$user"
    fun read(now:Long=System.currentTimeMillis()):List<SeraFeedPreview> = synchronized(lock){
        if(!SeraMessageAlerts.validId(user))return@synchronized emptyList()
        val raw=store.read(user,"feed","latest",now)?:run{
            val old=runCatching{prefs.getString(key,null)}.getOrNull()?:return@synchronized emptyList()
            val at=runCatching{JSONObject(old).optLong("at")}.getOrDefault(0L)
            if(now-at !in 0L..SERA_FEED_CACHE_TTL_MS){prefs.edit().remove(key).apply();return@synchronized emptyList()}
            if(store.write(user,"feed","latest",old,at,SERA_FEED_CACHE_TTL_MS,1))prefs.edit().remove(key).apply()
            old
        }
        val stored=runCatching{JSONObject(raw)}.getOrNull()?:return@synchronized emptyList()
        if(now-stored.optLong("at") !in 0L..SERA_FEED_CACHE_TTL_MS){store.remove(user,"feed","latest");return@synchronized emptyList()}
        val rows=stored.optJSONArray("rows")?:return@synchronized emptyList()
        (0 until minOf(rows.length(),40)).mapNotNull{i->runCatching{val r=rows.getJSONObject(i);SeraFeedPreview(r.getString("id"),r.getString("author"),r.optString("title"),r.optString("body"),r.optString("headline"),r.optString("publishedAt"),r.optString("kind"),r.optInt("likes"),r.optInt("comments"),r.optInt("shares"))}.getOrNull()}
    }
    fun write(posts:List<SeraPost>,now:Long=System.currentTimeMillis())=synchronized(lock){
        if(!SeraMessageAlerts.validId(user))return@synchronized
        val rows=JSONArray()
        seraFeedPreviewRows(posts).forEach{rows.put(JSONObject().put("id",it.id).put("author",it.author).put("title",it.title).put("body",it.body).put("headline",it.headline).put("publishedAt",it.publishedAt).put("kind",it.kind).put("likes",it.likes).put("comments",it.comments).put("shares",it.shares))}
        if(rows.length()==0)store.remove(user,"feed","latest") else store.write(user,"feed","latest",JSONObject().put("at",now).put("rows",rows).toString(),now,SERA_FEED_CACHE_TTL_MS,1)
        runCatching{prefs.edit().remove(key).apply()}
    }
    fun clear()=synchronized(lock){store.clear(user,"feed");runCatching{prefs.edit().remove(key).commit()};Unit}
    companion object{private val lock=Any()}
}
