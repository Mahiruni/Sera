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

internal data class NativeChatFile(val name: String, val mime: String, val path: String, val bytes: Int)
internal data class NativeChatSelection(val name: String, val mime: String, val bytes: ByteArray)

private const val CHAT_LIMIT = 5 * 1024 * 1024
private fun java.io.InputStream.readChatBounded(): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (out.size() <= CHAT_LIMIT) { val n=read(buffer,0,minOf(buffer.size,CHAT_LIMIT+1-out.size())); if(n<=0) break; out.write(buffer,0,n) }
    return out.toByteArray()
}
private val chatMimes = setOf("image/jpeg","image/png","image/webp","application/pdf")
internal fun selectChatFile(context: Context, uri: Uri): NativeChatSelection {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: error("Choose an image or PDF.")
    require(mime in chatMimes) { "Only JPEG, PNG, WebP and PDF files are supported." }
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.take(180)?.takeIf { it.isNotBlank() } ?: "Attachment"
    val bytes = resolver.openInputStream(uri)?.use { it.readChatBounded() } ?: error("Could not read file.")
    require(bytes.size in 1..CHAT_LIMIT) { "File must be 5 MB or smaller." }
    val valid = when(mime) {
        "application/pdf" -> bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())
        "image/jpeg" -> bytes.size >= 3 && (bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 255) == 216
        "image/png" -> bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))
        "image/webp" -> bytes.size >= 12 && String(bytes,0,4) == "RIFF" && String(bytes,8,4) == "WEBP"
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

internal fun sendChatAttachment(token: String,userId: String,conversation: String,body: String,file: NativeChatSelection): String {
    val ext = when(file.mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
    val path = "$userId/$conversation/${UUID.randomUUID()}.$ext"
    var uploaded = false
    var committed = false
    try {
        val upload=chatConnection(token,"/storage/v1/object/professional-message-files/$path","POST")
        try {
            upload.doOutput=true
            upload.setRequestProperty("Content-Type",file.mime)
            upload.setRequestProperty("x-upsert","false")
            upload.setFixedLengthStreamingMode(file.bytes.size)
            upload.outputStream.use { it.write(file.bytes) }
            if (upload.responseCode !in 200..299) return "File upload failed. Please retry."
            uploaded=true
        } finally { upload.disconnect() }
        val payload=JSONObject().put("p_conversation",conversation).put("p_body",body).put("p_path",path)
            .put("p_name",file.name).put("p_mime",file.mime).put("p_size",file.bytes.size)
        val rpc=chatConnection(token,"/rest/v1/rpc/sera_send_message_attachment","POST")
        try {
            rpc.doOutput=true
            rpc.setRequestProperty("Content-Type","application/json")
            rpc.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (rpc.responseCode !in 200..299) return "Could not send attachment. Please retry."
            committed=true
            val messageId=rpc.inputStream.bufferedReader().use{it.readText()}.trim().trim('"')
            SeraPush.dispatch(token,"message",messageId)
        } finally { rpc.disconnect() }
        return ""
    } catch (_: Exception) { return "Attachment transfer interrupted. Check your connection." }
    finally {
        if (uploaded && !committed) runCatching {
            val cleanup=chatConnection(token,"/storage/v1/object/professional-message-files","DELETE")
            try { cleanup.doOutput=true; cleanup.setRequestProperty("Content-Type","application/json"); cleanup.outputStream.use { it.write(JSONObject().put("prefixes",org.json.JSONArray().put(path)).toString().toByteArray()) }; cleanup.responseCode } finally { cleanup.disconnect() }
        }
    }
}
internal fun downloadChatFile(context: Context,token: String,file: NativeChatFile): Uri {
    require(file.mime in chatMimes && file.bytes in 1..CHAT_LIMIT)
    require(file.path.matches(Regex("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp|pdf)")))
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
