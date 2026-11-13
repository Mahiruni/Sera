package et.sera.nativeapp
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File

internal data class NativePending(val clientId:String,val body:String,val replyId:String?,val file:NativeChatSelection?,val album:List<NativeChatSelection> = emptyList(),val paused:Boolean=false)
internal class NativeChatOutbox(private val context:Context){
    private val master=MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs=EncryptedSharedPreferences.create(context,"sera_chat_outbox",master,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private fun key(user:String,thread:String)="$user:$thread"
    private fun path(user:String,id:String):File=File(File(context.filesDir,"sera_chat_pending").apply{mkdirs()},"$user-$id.bin")
    private fun encrypted(file:File)=EncryptedFile.Builder(context,file,master,EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    private fun erase(user:String,id:String){path(user,id).delete();for(i in 0..3)path(user,albumItemId(id,i)).delete()}
    fun save(user:String,thread:String,p:NativePending){
        require(p.album.isEmpty()||(p.album.size in 2..4&&p.file==null&&p.album.all{it.mime.startsWith("image/")&&it.bytes.size in 1..5*1024*1024}))
        val existing=prefs.getString(key(user,thread),null)?.let{runCatching{JSONObject(it).optString("id")}.getOrNull()}
        val written=mutableListOf<File>()
        fun store(id:String,f:NativeChatSelection){val target=path(user,id);if(!target.exists()){written.add(target);encrypted(target).openFileOutput().use{it.write(f.bytes)}}}
        try{
            val f=p.file
            val json=JSONObject().put("id",p.clientId).put("body",p.body).put("reply",p.replyId?:JSONObject.NULL).put("paused",p.paused)
            if(f!=null){store(p.clientId,f);json.put("name",f.name).put("mime",f.mime).put("duration",f.durationMs?:JSONObject.NULL)}
            if(p.album.isNotEmpty()){
                val photos=org.json.JSONArray()
                p.album.forEachIndexed{i,photo->store(albumItemId(p.clientId,i),photo);photos.put(JSONObject().put("name",photo.name).put("mime",photo.mime))}
                json.put("photos",photos)
            }
            check(prefs.edit().putString(key(user,thread),json.toString()).commit())
        }catch(e:Exception){written.forEach{it.delete()};throw e}
        if(existing!=null&&existing!=p.clientId)erase(user,existing)
    }
    fun load(user:String,thread:String):NativePending?{
        val raw=prefs.getString(key(user,thread),null)?:return null
        val j=JSONObject(raw);val id=j.getString("id");require(id.matches(Regex("[0-9a-f-]{36}")))
        fun loadFile(fileId:String,meta:JSONObject):NativeChatSelection{val bytes=encrypted(path(user,fileId)).openFileInput().use{it.readBytes()};require(bytes.size in 1..5*1024*1024);return NativeChatSelection(meta.getString("name"),meta.getString("mime"),bytes,meta.optInt("duration").takeIf{it>0})}
        val file=if(j.has("mime"))loadFile(id,j)else null
        val photos=j.optJSONArray("photos")
        val album=if(photos!=null){require(photos.length() in 2..4&&file==null);(0 until photos.length()).map{loadFile(albumItemId(id,it),photos.getJSONObject(it))}}else emptyList()
        return NativePending(id,j.optString("body"),j.optString("reply").takeUnless{it=="null"||it.isBlank()},file,album,j.optBoolean("paused"))
    }
    fun setPaused(user:String,thread:String,id:String,paused:Boolean){
        val raw=prefs.getString(key(user,thread),null)?:error("Pending message unavailable.")
        val j=JSONObject(raw);check(j.optString("id")==id);j.put("paused",paused);check(prefs.edit().putString(key(user,thread),j.toString()).commit())
    }
    fun remove(user:String,thread:String,id:String){
        val existing=prefs.getString(key(user,thread),null)?.let{runCatching{JSONObject(it).optString("id")}.getOrNull()}
        if(existing==id)prefs.edit().remove(key(user,thread)).commit()
        erase(user,id)
    }
}
