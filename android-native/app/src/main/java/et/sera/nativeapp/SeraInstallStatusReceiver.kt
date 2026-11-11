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
                clearSeraPendingUpdate(context)
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
