package et.sera.nativeapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** A short-lived, encrypted, account-scoped public text preview. No media or credentials. */
internal data class SeraFeedPreview(val id:String,val author:String,val title:String,val body:String)
internal const val SERA_FEED_CACHE_TTL_MS=15*60*1000L
internal fun seraFeedPreviewRows(posts:List<SeraPost>):List<SeraFeedPreview> =
    posts.asSequence().filter{it.audience=="public"&&it.repostOf==null&&it.id.isNotBlank()}
        .take(20).map{SeraFeedPreview(it.id,it.author.take(120),it.title.take(300),it.body.take(1200))}.toList()

internal class SeraFeedPreviewCache(context:Context,private val user:String){
    private val prefs=EncryptedSharedPreferences.create(context.applicationContext,"sera_feed_preview_v1",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val key="public_feed:$user"
    fun read(now:Long=System.currentTimeMillis()):List<SeraFeedPreview> = synchronized(lock){
        if(!SeraMessageAlerts.validId(user))return@synchronized emptyList()
        val stored=runCatching{JSONObject(prefs.getString(key,null)?:return@synchronized emptyList())}.getOrNull()?:return@synchronized emptyList()
        if(now-stored.optLong("at") !in 0L..SERA_FEED_CACHE_TTL_MS){prefs.edit().remove(key).apply();return@synchronized emptyList()}
        val rows=stored.optJSONArray("rows")?:return@synchronized emptyList()
        (0 until minOf(rows.length(),20)).mapNotNull{i->runCatching{val r=rows.getJSONObject(i);SeraFeedPreview(r.getString("id"),r.getString("author"),r.optString("title"),r.optString("body"))}.getOrNull()}
    }
    fun write(posts:List<SeraPost>,now:Long=System.currentTimeMillis())=synchronized(lock){
        if(!SeraMessageAlerts.validId(user))return@synchronized
        val rows=JSONArray()
        seraFeedPreviewRows(posts).forEach{rows.put(JSONObject().put("id",it.id).put("author",it.author).put("title",it.title).put("body",it.body))}
        val editor=prefs.edit()
        prefs.all.keys.filter{it.startsWith("public_feed:")&&it!=key}.forEach{editor.remove(it)}
        if(rows.length()==0)editor.remove(key) else editor.putString(key,JSONObject().put("at",now).put("rows",rows).toString())
        editor.apply()
    }
    fun clear()=synchronized(lock){prefs.edit().remove(key).commit()}
    companion object{private val lock=Any()}
}
