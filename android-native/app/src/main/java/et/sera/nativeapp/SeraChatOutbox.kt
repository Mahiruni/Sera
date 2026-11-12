package et.sera.nativeapp
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File

internal data class NativePending(val clientId:String,val body:String,val replyId:String?,val file:NativeChatSelection?)
internal class NativeChatOutbox(private val context:Context){
    private val master=MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs=EncryptedSharedPreferences.create(context,"sera_chat_outbox",master,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private fun key(user:String,thread:String)="$user:$thread"
    private fun path(user:String,id:String):File=File(File(context.filesDir,"sera_chat_pending").apply{mkdirs()},"$user-$id.bin")
    private fun encrypted(file:File)=EncryptedFile.Builder(context,file,master,EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    fun save(user:String,thread:String,p:NativePending){
        val existing=prefs.getString(key(user,thread),null)?.let{runCatching{JSONObject(it).optString("id")}.getOrNull()}
        val f=p.file
        if(f!=null){val target=path(user,p.clientId);if(!target.exists())encrypted(target).openFileOutput().use{it.write(f.bytes)}}
        val json=JSONObject().put("id",p.clientId).put("body",p.body).put("reply",p.replyId?:JSONObject.NULL)
        if(f!=null)json.put("name",f.name).put("mime",f.mime).put("duration",f.durationMs?:JSONObject.NULL)
        check(prefs.edit().putString(key(user,thread),json.toString()).commit())
        if(existing!=null&&existing!=p.clientId)path(user,existing).delete()
    }
    fun load(user:String,thread:String):NativePending?{
        val raw=prefs.getString(key(user,thread),null)?:return null
        val j=JSONObject(raw);val id=j.getString("id")
        require(id.matches(Regex("[0-9a-f-]{36}")))
        val file=if(j.has("mime")){val f=path(user,id);val bytes=encrypted(f).openFileInput().use{it.readBytes()};require(bytes.size in 1..5*1024*1024);NativeChatSelection(j.getString("name"),j.getString("mime"),bytes,j.optInt("duration").takeIf{it>0})}else null
        return NativePending(id,j.optString("body"),j.optString("reply").takeUnless{it=="null"||it.isBlank()},file)
    }
    fun remove(user:String,thread:String,id:String){
        val existing=prefs.getString(key(user,thread),null)?.let{runCatching{JSONObject(it).optString("id")}.getOrNull()}
        if(existing==id)prefs.edit().remove(key(user,thread)).commit()
        path(user,id).delete()
    }
}
