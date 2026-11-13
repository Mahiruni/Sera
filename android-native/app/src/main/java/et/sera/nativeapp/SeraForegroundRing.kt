package et.sera.nativeapp

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** One alert owner while the in-app incoming screen is visible. */
internal class SeraForegroundRing private constructor(private val ringtone:Ringtone?,private val vibrator:Vibrator?):AutoCloseable {
    override fun close(){runCatching{ringtone?.stop()};runCatching{vibrator?.cancel()}}
    companion object {
        @Suppress("DEPRECATION")
        fun start(context:Context):SeraForegroundRing {
            val nm=context.getSystemService(NotificationManager::class.java)
            val audio=context.getSystemService(AudioManager::class.java)
            val channel=nm.getNotificationChannel(SeraPush.CALL_CHANNEL)
            // Fail quiet under DND; the OS notification can handle user-configured exceptions.
            val allowed=nm.areNotificationsEnabled()&&channel!=null&&channel.importance!=NotificationManager.IMPORTANCE_NONE&&nm.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_ALL
            val ringtone=if(allowed&&audio.ringerMode==AudioManager.RINGER_MODE_NORMAL&&channel?.sound!=null)runCatching{
                RingtoneManager.getRingtone(context,channel.sound).apply {
                    audioAttributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
                    if(Build.VERSION.SDK_INT>=28)isLooping=true
                    play()
                }
            }.getOrNull()else null
            val vibrator=if(allowed&&audio.ringerMode!=AudioManager.RINGER_MODE_SILENT&&channel?.shouldVibrate()==true)runCatching{
                val v=if(Build.VERSION.SDK_INT>=31)context.getSystemService(VibratorManager::class.java).defaultVibrator else context.getSystemService(Vibrator::class.java)
                if(v.hasVibrator()){v.vibrate(VibrationEffect.createWaveform(longArrayOf(0,350,250,350,1500),0));v}else null
            }.getOrNull()else null
            return SeraForegroundRing(ringtone,vibrator)
        }
    }
}
