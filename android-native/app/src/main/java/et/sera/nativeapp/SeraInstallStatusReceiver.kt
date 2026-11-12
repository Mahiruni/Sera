package et.sera.nativeapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * Receives PackageInstaller's trusted session result. Only the Android system
 * can invoke this private receiver via the explicit mutable PendingIntent.
 * Never treat a staging/commit call as proof that the APK was installed.
 */
class SeraInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation: Intent? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                if (confirmation == null) {
                    reportInstallFailure(context, "Android did not provide an installation approval screen.")
                    return
                }
                try {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                } catch (_: Exception) {
                    reportInstallFailure(
                        context,
                        "Android blocked the installer screen. Reopen SERA and tap Install update to retry."
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val id=context.getSharedPreferences("sera_update",Context.MODE_PRIVATE).getLong("id",-1L)
                if(id>0) discardSeraDownload(context,id) else clearSeraPendingUpdate(context)
                reopenSeraAfterUpdate(context)
            }
            else -> {
                val explanation = when (status) {
                    PackageInstaller.STATUS_FAILURE_BLOCKED ->
                        "Android blocked the installation. Check the Install unknown apps permission for SERA."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "The installed app has a different signature or package. Do not uninstall SERA; contact support."
                    PackageInstaller.STATUS_FAILURE_INVALID ->
                        "Android rejected the APK. Its signature or format may be invalid."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "This APK is not compatible with the device."
                    PackageInstaller.STATUS_FAILURE_STORAGE ->
                        "Not enough available storage to install the update."
                    PackageInstaller.STATUS_FAILURE_ABORTED ->
                        "The installation was cancelled. Your existing SERA app is unchanged."
                    else -> "Android could not install the update. Please retry from SERA."
                }
                reportInstallFailure(context, explanation)
            }
        }
    }
}

internal fun reportInstallFailure(context: Context, message: String) {
    context.getSharedPreferences("sera_update", Context.MODE_PRIVATE).edit()
        .putString("install_error", message).apply()
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

internal fun consumeSeraInstallError(context: Context): String? {
    val preferences = context.getSharedPreferences("sera_update", Context.MODE_PRIVATE)
    val error = preferences.getString("install_error", null)
    if (error != null) preferences.edit().remove("install_error").apply()
    return error
}

/** Android may restrict background activity launches; retain a user-tappable fallback. */
internal fun reopenSeraAfterUpdate(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    runCatching { context.startActivity(launch) }
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
    runCatching {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = "sera_update_complete"
        manager.createNotificationChannel(android.app.NotificationChannel(channel,"SERA updates",android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(context, 903, launch, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        manager.notify(903, androidx.core.app.NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SERA is updated").setContentText("Tap to open the latest version.").setContentIntent(open).setAutoCancel(true).build())
    }
}
class SeraPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs=context.getSharedPreferences("sera_update",Context.MODE_PRIVATE)
        val id=prefs.getLong("id",-1L)
        if(id>0 && prefs.getInt("code",0)<=BuildConfig.VERSION_CODE) {
            discardSeraDownload(context,id)
            reopenSeraAfterUpdate(context)
        }
    }
}
