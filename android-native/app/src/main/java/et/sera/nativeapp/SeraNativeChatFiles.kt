package et.sera.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class NativeChatFile(val name: String, val mime: String, val path: String, val bytes: Int, val durationMs: Int? = null)
internal data class NativeChatSelection(val name: String, val mime: String, val bytes: ByteArray, val durationMs: Int? = null)

private const val CHAT_LIMIT = 5 * 1024 * 1024
private fun java.io.InputStream.readChatBounded(): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (out.size() <= CHAT_LIMIT) { val n=read(buffer,0,minOf(buffer.size,CHAT_LIMIT+1-out.size())); if(n<=0) break; out.write(buffer,0,n) }
    return out.toByteArray()
}
private val chatMimes = setOf("image/jpeg","image/png","image/webp","application/pdf","audio/mp4","audio/webm","audio/ogg","audio/mpeg")
internal fun selectChatFile(context: Context, uri: Uri): NativeChatSelection {
    val resolver = context.contentResolver
    val mime = (resolver.getType(uri) ?: error("Choose a supported file.")).let { if(it=="audio/x-m4a") "audio/mp4" else it }
    require(mime in chatMimes) { "Choose a supported image, PDF or audio file." }
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.replace(Regex("[\\p{Cntrl}/\\\\]"),"_")?.take(180)?.takeIf { it.isNotBlank() } ?: "Attachment"
    val bytes = resolver.openInputStream(uri)?.use { it.readChatBounded() } ?: error("Could not read file.")
    require(bytes.size in 1..CHAT_LIMIT) { "File must be 5 MB or smaller." }
    val valid = when(mime) {
        "application/pdf" -> bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())
        "image/jpeg" -> bytes.size >= 3 && (bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 255) == 216
        "image/png" -> bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))
        "image/webp" -> bytes.size >= 12 && String(bytes,0,4) == "RIFF" && String(bytes,8,4) == "WEBP"
        "audio/mp4" -> bytes.size>=12 && String(bytes,4,4)=="ftyp"
        "audio/webm" -> bytes.size>=4 && bytes.take(4).toByteArray().contentEquals(byteArrayOf(26,69,-33,-93))
        "audio/ogg" -> bytes.size>=4 && String(bytes,0,4)=="OggS"
        "audio/mpeg" -> bytes.size>=3 && (String(bytes,0,3)=="ID3" || (bytes[0].toInt() and 255)==255)
        else -> false
    }
    require(valid) { "The file does not match its declared format." }
    return NativeChatSelection(name,mime,bytes)
}
private fun chatConnection(token: String, path: String, method: String): HttpURLConnection =
    (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout=15000; readTimeout=30000
        instanceFollowRedirects=false
        setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
        setRequestProperty("Authorization","Bearer $token")
    }

internal fun chatRpc(token:String,name:String,payload:JSONObject):Pair<String?,String> {
    val c=chatConnection(token,"/rest/v1/rpc/"+name,"POST")
    return try {
        c.doOutput=true; c.setRequestProperty("Content-Type","application/json")
        c.outputStream.use{it.write(payload.toString().toByteArray())}
        if(c.responseCode !in 200..299) null to "Request failed. Please retry." else (c.inputStream?.bufferedReader()?.use{it.readText()} ?: "") to ""
    } catch(_:Exception){null to "Connection interrupted. Please retry."} finally{c.disconnect()}
}
internal fun readChatFile(token:String,file:NativeChatFile):ByteArray {
    require(file.mime in chatMimes && file.bytes in 1..CHAT_LIMIT)
    require(file.path.matches(Regex("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp|pdf|m4a|mp4|webm|ogg|mp3)")))
    val c=chatConnection(token,"/storage/v1/object/authenticated/professional-message-files/"+file.path,"GET")
    try { check(c.responseCode in 200..299){"File unavailable."}; return c.inputStream.use{it.readChatBounded()}.also{require(it.size==file.bytes)} } finally {c.disconnect()}
}
internal fun sendNativeMessageV2(token:String,userId:String,conversation:String,clientId:String,body:String,file:NativeChatSelection?,reply:String?=null,forward:String?=null,onProgress:(Int)->Unit={}):Pair<String?,String> {
    return try {
        val ext=when(file?.mime){"application/pdf"->"pdf";"image/png"->"png";"image/webp"->"webp";"audio/mp4"->"m4a";"audio/webm"->"webm";"audio/ogg"->"ogg";"audio/mpeg"->"mp3";else->"jpg"}
        val path=if(file==null)null else "$userId/$conversation/$clientId.$ext"
        if(file!=null && path!=null){
            require(file.mime in chatMimes && file.bytes.size in 1..CHAT_LIMIT)
            val c=chatConnection(token,"/storage/v1/object/professional-message-files/"+path,"POST")
            try{c.doOutput=true;c.setRequestProperty("Content-Type",file.mime);c.setRequestProperty("x-upsert","false");c.setFixedLengthStreamingMode(file.bytes.size);c.outputStream.use{out->var offset=0;while(offset<file.bytes.size){val count=minOf(32768,file.bytes.size-offset);out.write(file.bytes,offset,count);offset+=count;onProgress((offset*100L/file.bytes.size).toInt().coerceAtMost(99))}}
                if(c.responseCode !in 200..299){
                    val previous=readChatFile(token,NativeChatFile(file.name,file.mime,path,file.bytes.size))
                    if(!previous.contentEquals(file.bytes))return null to "Attachment upload could not be verified."
                }
            }finally{c.disconnect()}
            onProgress(100)
        }
        val payload=JSONObject().put("p_conversation",conversation).put("p_client_id",clientId).put("p_body",body)
            .put("p_reply_to",reply?:JSONObject.NULL).put("p_forward_source",forward?:JSONObject.NULL)
            .put("p_path",path?:JSONObject.NULL).put("p_name",file?.name?:JSONObject.NULL)
            .put("p_mime",file?.mime?:JSONObject.NULL).put("p_size",file?.bytes?.size?:JSONObject.NULL)
            .put("p_duration_ms",file?.durationMs?:JSONObject.NULL)
        val r=chatRpc(token,"sera_send_message_v2",payload)
        val id=r.first?.trim()?.trim('"')
        if(id!=null && id.matches(Regex("[0-9a-f-]{36}"))){runCatching{SeraPush.dispatch(token,"message",id)};id to ""}else null to r.second.ifBlank{"Message not sent. Retry."}
    }catch(_:Exception){null to "Transfer interrupted. Check your connection and retry."}
}
internal fun sendChatAttachment(token:String,userId:String,conversation:String,body:String,file:NativeChatSelection):String =
    sendNativeMessageV2(token,userId,conversation,UUID.randomUUID().toString(),body,file).second
internal fun unsendNativeMessage(token:String,id:String):Pair<String?,String>{
    val r=chatRpc(token,"sera_unsend_message",JSONObject().put("p_message",id))
    if(r.first==null)return r
    val paths=runCatching{org.json.JSONArray(r.first)}.getOrDefault(org.json.JSONArray())
    if(paths.length()>0)runCatching{
        val c=chatConnection(token,"/storage/v1/object/professional-message-files","DELETE")
        try{c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("prefixes",paths).toString().toByteArray())};c.responseCode}finally{c.disconnect()}
    }
    return r
}
internal fun downloadChatFile(context: Context,token: String,file: NativeChatFile): Uri {
    require(file.mime in chatMimes && file.bytes in 1..CHAT_LIMIT)
    require(file.path.matches(Regex("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp|pdf|m4a|mp4|webm|ogg|mp3)")))
    val connection=chatConnection(token,"/storage/v1/object/authenticated/professional-message-files/${file.path}","GET")
    val dir=File(context.cacheDir,"chat_files").apply { mkdirs() }
    val ext=file.path.substringAfterLast('.')
    val destination=File(dir,"${UUID.randomUUID()}.$ext")
    try {
        check(connection.responseCode in 200..299) { "File is unavailable or access was denied." }
        val bytes=connection.inputStream.use { it.readChatBounded() }
        require(bytes.size in 1..CHAT_LIMIT) { "Invalid attachment size." }
        destination.writeBytes(bytes)
        return FileProvider.getUriForFile(context,context.packageName+".chatfiles",destination)
    } catch(e: Exception) { destination.delete(); throw e }
    finally { connection.disconnect() }
}
internal fun openChatFile(context: Context,uri: Uri,mime: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Open attachment"))
}

/** Re-encode camera output before sending: bounded dimensions/bytes, no EXIF metadata. */
internal fun prepareCapturedChatPhoto(file:File):NativeChatSelection{
    val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
    android.graphics.BitmapFactory.decodeFile(file.absolutePath,bounds)
    require(bounds.outWidth>0&&bounds.outHeight>0){"Camera did not return a readable photo."}
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2000)sample*=2
    val bitmap=android.graphics.BitmapFactory.decodeFile(file.absolutePath,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})?:error("Could not decode photo.")
    try{val stream=java.io.ByteArrayOutputStream();check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,stream));val bytes=stream.toByteArray();require(bytes.size in 1..CHAT_LIMIT){"Photo exceeds 5 MB."};return NativeChatSelection("Photo-${System.currentTimeMillis()}.jpg","image/jpeg",bytes)}finally{bitmap.recycle()}
}
