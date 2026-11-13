package et.sera.nativeapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Short-lived account/thread-scoped snapshots. Never stores attachment bytes or tokens. */
internal class SeraMessageHistoryCache(context:Context){
    private val prefs=EncryptedSharedPreferences.create(context,"sera_message_history_v1",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private fun key(user:String,thread:String)="history:$user:$thread"
    fun read(user:String,thread:String):String?=synchronized(lock){
        if(!SeraMessageAlerts.validId(user)||!SeraMessageAlerts.validId(thread))return@synchronized null
        val k=key(user,thread)
        val row=runCatching{JSONObject(prefs.getString(k,null)?:return@synchronized null)}.getOrNull()?:return@synchronized null
        val age=System.currentTimeMillis()-row.optLong("at")
        if(age !in 0L..600000L){prefs.edit().remove(k).apply();return@synchronized null}
        row.optJSONArray("rows")?.toString()
    }
    fun write(user:String,thread:String,raw:String)=synchronized(lock){
        if(!SeraMessageAlerts.validId(user)||!SeraMessageAlerts.validId(thread))return@synchronized
        val source=JSONArray(raw);val rows=JSONArray()
        for(i in 0 until minOf(source.length(),60)){
            val item=source.getJSONObject(i)
            if(!item.isNull("unsent_at")){item.put("body","Message unsent");item.remove("professional_message_attachments");item.remove("professional_message_reactions")}
            rows.put(item)
        }
        val value=JSONObject().put("at",System.currentTimeMillis()).put("rows",rows).toString()
        if(value.length>600000)return@synchronized
        val editor=prefs.edit()
        val k=key(user,thread)
        val others=prefs.all.filterKeys{it.startsWith("history:$user:")&&it!=k}.entries.sortedByDescending{runCatching{JSONObject(it.value as String).optLong("at")}.getOrDefault(0L)}
        others.drop(7).forEach{editor.remove(it.key)}
        editor.putString(k,value).apply()
    }
    fun clear(user:String)=synchronized(lock){val editor=prefs.edit();prefs.all.keys.filter{it.startsWith("history:$user:")}.forEach{editor.remove(it)};editor.commit()}
    companion object{private val lock=Any()}
}
