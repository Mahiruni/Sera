package et.sera.nativeapp

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.database.Cursor
import android.content.ActivityNotFoundException
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class SeraRelease(val versionCode: Int, val versionName: String, val url: String, val notes: String, val sha256: String)

/** Accept only published APK assets from the official SERA GitHub repository. */
internal suspend fun checkSeraUpdate(): SeraRelease? = withContext(Dispatchers.IO) {
    try {
        val connection = URL("https://sera.hisabtech.com/api/native/updates").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != 200) return@withContext null
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val code = data.optInt("versionCode", 0)
            val name = data.optString("versionName")
            val address = data.optString("apkUrl")
            val parsed = URL(address)
            val sha256 = data.optString("sha256").lowercase()
            if (!sha256.matches(Regex("[0-9a-f]{64}")) || code <= BuildConfig.VERSION_CODE || parsed.protocol != "https" ||
                parsed.host != "github.com" || parsed.userInfo != null ||
                parsed.path != "/Mahiruni/Sera/releases/download/android-v$name/SERA-$name.apk")
                return@withContext null
            SeraRelease(code, name, address, data.optString("notes").take(600), sha256)
        } finally { connection.disconnect() }
    } catch (_: Exception) { null }
}

internal fun downloadSeraUpdate(context: Context, release: SeraRelease): Long? {
    return try {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.url))
            .setTitle("SERA.et " + release.versionName)
            .setDescription("Downloading verified release package")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "sera-update-${release.versionCode}.apk")
            .setMimeType("application/vnd.android.package-archive")
        manager.enqueue(request)
    } catch (_: Exception) { null }
}

@Composable
internal fun SeraUpdatePrompt(release: SeraRelease, onDismiss: () -> Unit, onDownload: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A new SERA update is ready") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Version ${release.versionName}")
                if (release.notes.isNotBlank()) Text(release.notes)
                Text("Download the update inside SERA. Android will ask you to approve installation. Your account and data remain intact when the signing certificate matches.")
            }
        },
        confirmButton = { Button(onClick = onDownload) { Text("Download update") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        shape = RoundedCornerShape(22.dp)
    )
}

internal data class SeraDownloadStatus(val progress: Int, val complete: Boolean, val failed: Boolean)

internal fun seraDownloadStatus(context: Context, downloadId: Long): SeraDownloadStatus {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
    cursor.use {
        if (!it.moveToFirst()) return SeraDownloadStatus(0, false, true)
        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        return SeraDownloadStatus(progress, status == DownloadManager.STATUS_SUCCESSFUL,
            status == DownloadManager.STATUS_FAILED)
    }
}

/** Android always requires user approval to install an update. */
internal fun openSeraInstaller(context: Context, downloadId: Long): Boolean {
    return try {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(downloadId) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }
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
