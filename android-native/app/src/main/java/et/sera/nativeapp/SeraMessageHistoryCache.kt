package et.sera.nativeapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Short-lived account/thread-scoped snapshots. Never stores attachment bytes or tokens. */
internal const val SERA_MESSAGE_CACHE_RETENTION_MS=15*60*1000L
internal const val SERA_MESSAGE_CACHE_THREADS=20
internal class SeraMessageHistoryCache(context:Context){
    private val store=SeraLocalCache.get(context)
    private val prefs by lazy{EncryptedSharedPreferences.create(context.applicationContext,"sera_message_history_v1",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)}
    private fun key(user:String,thread:String)="history:$user:$thread"
    private fun snapshot(user:String,bucket:String,item:String,legacyKey:String):JSONObject?{
        val now=System.currentTimeMillis()
        val raw=store.read(user,bucket,item,now)?:run{
            val old=runCatching{prefs.getString(legacyKey,null)}.getOrNull()?:return null
            val at=runCatching{JSONObject(old).optLong("at")}.getOrDefault(0L)
            if(now-at !in 0L..SERA_MESSAGE_CACHE_RETENTION_MS){prefs.edit().remove(legacyKey).apply();return null}
            if(store.write(user,bucket,item,old,at,SERA_MESSAGE_CACHE_RETENTION_MS,if(bucket=="chat")SERA_MESSAGE_CACHE_THREADS else 1))prefs.edit().remove(legacyKey).apply()
            old
        }
        return runCatching{JSONObject(raw)}.getOrNull()
    }
    fun read(user:String,thread:String):String?=synchronized(lock){
        if(!SeraMessageAlerts.validId(user)||!SeraMessageAlerts.validId(thread))return@synchronized null
        val row=snapshot(user,"chat",thread,key(user,thread))?:return@synchronized null
        val age=System.currentTimeMillis()-row.optLong("at")
        if(age !in 0L..SERA_MESSAGE_CACHE_RETENTION_MS){store.remove(user,"chat",thread);return@synchronized null}
        row.optJSONArray("rows")?.toString()
    }
    fun generation(user:String):Long=synchronized(lock){generations[user]?:0L}
    fun write(user:String,thread:String,raw:String,expectedGeneration:Long=generation(user))=synchronized(lock){
        if(expectedGeneration!=(generations[user]?:0L))return@synchronized
        if(!SeraMessageAlerts.validId(user)||!SeraMessageAlerts.validId(thread))return@synchronized
        val source=JSONArray(raw);val rows=JSONArray()
        for(i in 0 until minOf(source.length(),SERA_INITIAL_MESSAGE_WINDOW)){
            val item=source.getJSONObject(i)
            if(!item.isNull("unsent_at")){item.put("body","Message unsent");item.remove("professional_message_attachments");item.remove("professional_message_reactions")}
            rows.put(item)
        }
        val value=JSONObject().put("at",System.currentTimeMillis()).put("rows",rows).toString()
        if(value.length>600000)return@synchronized
        store.write(user,"chat",thread,value,System.currentTimeMillis(),SERA_MESSAGE_CACHE_RETENTION_MS,SERA_MESSAGE_CACHE_THREADS)
        runCatching{prefs.edit().remove(key(user,thread)).apply()}
        Unit
    }
    fun writeInbox(user:String,items:List<NativeConversation>,expectedGeneration:Long=generation(user))=synchronized(lock){
        if(expectedGeneration!=(generations[user]?:0L))return@synchronized
        if(!SeraMessageAlerts.validId(user))return@synchronized
        val rows=JSONArray();items.take(100).forEach{c->rows.put(JSONObject().put("id",c.id).put("peer",c.peerId).put("name",c.peerName).put("preview",c.preview.take(400)).put("unread",c.unread).put("at",c.lastAt).put("pinned",c.pinned).put("archived",c.archived).put("marked",c.markedUnread).put("muted",c.mutedUntil?:JSONObject.NULL))}
        val now=System.currentTimeMillis()
        store.write(user,"inbox","latest",JSONObject().put("at",now).put("rows",rows).toString(),now,SERA_MESSAGE_CACHE_RETENTION_MS,1)
        runCatching{prefs.edit().remove("history:$user:inbox").apply()}
        Unit
    }
    fun readInbox(user:String):List<NativeConversation> = synchronized(lock){
        if(!SeraMessageAlerts.validId(user))return@synchronized emptyList()
        val row=snapshot(user,"inbox","latest","history:$user:inbox")?:return@synchronized emptyList()
        if(System.currentTimeMillis()-row.optLong("at") !in 0L..SERA_MESSAGE_CACHE_RETENTION_MS){store.remove(user,"inbox","latest");return@synchronized emptyList()}
        val rows=row.optJSONArray("rows")?:return@synchronized emptyList()
        (0 until rows.length()).mapNotNull{i->runCatching{val c=rows.getJSONObject(i);NativeConversation(c.getString("id"),c.getString("peer"),c.getString("name"),c.optString("preview"),c.optInt("unread"),null,c.optString("at"),c.optBoolean("pinned"),c.optBoolean("archived"),c.optBoolean("marked"),c.optString("muted").takeUnless{it=="null"||it.isBlank()})}.getOrNull()}
    }
    fun invalidateThread(user:String,thread:String)=synchronized(lock){generations[user]=(generations[user]?:0L)+1;store.remove(user,"chat",thread);runCatching{prefs.edit().remove(key(user,thread)).apply()};Unit}
    fun clear(user:String)=synchronized(lock){generations[user]=(generations[user]?:0L)+1;store.clear(user,"chat");store.clear(user,"inbox");runCatching{val editor=prefs.edit();prefs.all.keys.filter{it.startsWith("history:$user:")}.forEach{editor.remove(it)};editor.commit()};Unit}
    companion object{private val lock=Any();private val generations=mutableMapOf<String,Long>()}
}
