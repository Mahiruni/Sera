package et.sera.nativeapp

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import java.io.FileInputStream
import android.os.Build
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class SeraRelease(val versionCode: Int, val versionName: String, val url: String, val notes: String, val sha256: String, val highlights: List<String> = emptyList())

/** Accept only published APK assets from the official SERA GitHub repository. */
internal data class SeraUpdateCheck(val release:SeraRelease?=null,val successful:Boolean=false)

internal suspend fun checkSeraUpdateResult(): SeraUpdateCheck = withContext(Dispatchers.IO) {
    try {
        val connection = URL("https://sera.hisabtech.com/api/native/updates").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != 200) return@withContext SeraUpdateCheck()
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val code = data.optInt("versionCode", 0)
            val name = data.optString("versionName")
            val address = data.optString("apkUrl")
            val parsed = URL(address)
            val sha256 = data.optString("sha256").lowercase()
            val firstParty = parsed.host == "sera.hisabtech.com" &&
                parsed.path == "/downloads/sera-android.apk" && parsed.query == "version=$name"
            val officialRelease = parsed.host == "github.com" &&
                parsed.path == "/Mahiruni/Sera/releases/download/android-v$name/SERA-$name.apk"
            if (!name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) ||
                !sha256.matches(Regex("[0-9a-f]{64}")) ||
                code <= 0 || parsed.protocol != "https" ||
                parsed.userInfo != null || !(firstParty || officialRelease))
                return@withContext SeraUpdateCheck()
            if (code <= BuildConfig.VERSION_CODE) return@withContext SeraUpdateCheck(successful=true)
            // Eliminate the extra first-party redirect for new clients.
            // Legacy clients still use the compatible URL served by the API.
            val directDownload = if (firstParty)
                "https://github.com/Mahiruni/Sera/releases/download/android-v$name/SERA-$name.apk"
                else address
            val highlights = data.optJSONArray("highlights")?.let { arr -> (0 until minOf(arr.length(),8)).map { arr.optString(it).take(48) }.filter { it.isNotBlank() } }.orEmpty()
            SeraUpdateCheck(SeraRelease(code, name, directDownload, data.optString("notes").take(600), sha256, highlights),true)
        } finally { connection.disconnect() }
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { SeraUpdateCheck() }
}

internal suspend fun checkSeraUpdate(): SeraRelease? = checkSeraUpdateResult().release

internal fun downloadSeraUpdate(context: Context, release: SeraRelease): Long? {
    return try {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.url))
            .setTitle("SERA.et " + release.versionName)
            .setDescription("Downloading verified release package")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setVisibleInDownloadsUi(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "sera-update-${release.versionCode}-${System.currentTimeMillis()}.apk")
            .setMimeType("application/vnd.android.package-archive")
        manager.enqueue(request)
    } catch (_: Exception) { null }
}

internal data class SeraDownloadStatus(
    val progress: Int, val complete: Boolean, val failed: Boolean,
    val downloadedBytes: Long = 0L, val totalBytes: Long = -1L
)

internal fun seraDownloadStatus(context: Context, downloadId: Long): SeraDownloadStatus {
    return try {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return SeraDownloadStatus(0, false, true)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
            SeraDownloadStatus(progress, status == DownloadManager.STATUS_SUCCESSFUL,
                status == DownloadManager.STATUS_FAILED,
                downloadedBytes = downloaded, totalBytes = total)
        }
    } catch (_: Exception) { SeraDownloadStatus(0, false, true) }
}

internal enum class SeraInstallResult { LAUNCHED, PERMISSION_REQUIRED, FAILED }

internal data class SeraPendingUpdate(val id: Long, val release: SeraRelease)

/** Preserve an automatic download across app restarts and backgrounding. */
internal fun saveSeraPendingUpdate(context: Context, id: Long, release: SeraRelease) {
    context.getSharedPreferences("sera_update", Context.MODE_PRIVATE).edit()
        .putBoolean("automatic_install_attempted",false)
        .putLong("id", id)
        .putInt("code", release.versionCode)
        .putString("name", release.versionName)
        .putString("sha256", release.sha256)
        .putString("highlights",org.json.JSONArray(release.highlights).toString())
        .apply()
}
internal fun clearSeraPendingUpdate(context: Context) {
    context.getSharedPreferences("sera_update", Context.MODE_PRIVATE).edit().clear().apply()
}
internal fun readSeraPendingUpdate(context: Context): SeraPendingUpdate? {
    val prefs = context.getSharedPreferences("sera_update", Context.MODE_PRIVATE)
    val id = prefs.getLong("id", -1L)
    val code = prefs.getInt("code", 0)
    val name = prefs.getString("name", "").orEmpty()
    val sha = prefs.getString("sha256", "").orEmpty()
    if (id > 0 && code <= BuildConfig.VERSION_CODE) { discardSeraDownload(context,id); return null }
    if (id <= 0 ||
        !name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) ||
        !sha.matches(Regex("[a-f0-9]{64}"))) {
        clearSeraPendingUpdate(context)
        return null
    }
    val highlights=runCatching { val arr=org.json.JSONArray(prefs.getString("highlights","[]")); (0 until minOf(arr.length(),8)).map { arr.optString(it) } }.getOrDefault(emptyList())
    return SeraPendingUpdate(id, SeraRelease(code, name, "", "", sha, highlights))
}

internal fun shouldAutomaticallyOfferSeraInstall(foreground:Boolean,restoring:Boolean,downloaded:Boolean,attempted:Boolean):Boolean =
    foreground && !restoring && downloaded && !attempted

internal fun seraAutomaticInstallAttempted(context:Context,id:Long):Boolean {
    val prefs=context.getSharedPreferences("sera_update",Context.MODE_PRIVATE)
    return prefs.getLong("id",-1L)==id && prefs.getBoolean("automatic_install_attempted",false)
}
internal fun markSeraAutomaticInstallAttempted(context:Context,id:Long) {
    val prefs=context.getSharedPreferences("sera_update",Context.MODE_PRIVATE)
    if(prefs.getLong("id",-1L)==id) prefs.edit().putBoolean("automatic_install_attempted",true).apply()
}

internal fun discardSeraDownload(context: Context, downloadId: Long) {
    clearSeraPendingUpdate(context)
    runCatching {
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
    }
}

/** Opening the system installer still requires explicit Android user approval. */
/**
 * Stage the already SHA-256-verified APK in Android's own PackageInstaller.
 * Unlike ACTION_VIEW with a DownloadManager content URI, commit produces a
 * status callback and explicit user confirmation when Android requires it.
 *
 * Called on Dispatchers.IO because staging copies the APK into an OS session.
 */
internal fun openSeraInstaller(context: Context, downloadId: Long, expectedSha256: String): SeraInstallResult {
    if (!expectedSha256.matches(Regex("[0-9a-f]{64}"))) return SeraInstallResult.FAILED
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()) {
        return SeraInstallResult.PERMISSION_REQUIRED
    }

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val installer = context.packageManager.packageInstaller
    var sessionId = -1
    return try {
        manager.openDownloadedFile(downloadId).use { apk ->
            val size = apk.statSize
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                if (size > 0L) setSize(size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(
                        PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                    )
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(apk.fileDescriptor).use { input ->
                    session.openWrite("base.apk", 0, if (size > 0L) size else -1L).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        session.fsync(output)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII),expectedSha256.toByteArray(Charsets.US_ASCII))) { "APK changed before installation" }
                val receiver = Intent(context, SeraInstallStatusReceiver::class.java).apply {
                    action = "et.sera.nativeapp.INSTALL_STATUS"
                }
                // API 35+ requires a mutable status PendingIntent so Android
                // can attach EXTRA_STATUS and its user-confirmation Intent.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(
                    context, sessionId, receiver, flags
                )
                session.commit(callback.intentSender)
            }
        }
        SeraInstallResult.LAUNCHED
    } catch (_: Exception) {
        if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
        SeraInstallResult.FAILED
    }
}

internal fun requestSeraInstallPermission(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) { false }
}

/** Verify every byte of the downloaded APK before offering installation. */
internal fun verifySeraDownload(context: Context, downloadId: Long, expectedSha256: String): Boolean {
    if (!expectedSha256.matches(Regex("[0-9a-f]{64}"))) return false
    return try {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val digest = MessageDigest.getInstance("SHA-256")
        manager.openDownloadedFile(downloadId).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { stream ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII),
            expectedSha256.toByteArray(Charsets.US_ASCII))
    } catch (_: Exception) { false }
}
