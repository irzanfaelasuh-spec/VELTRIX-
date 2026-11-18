package app.veltrix.island.prayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.veltrix.island.IslandRuntime
import app.veltrix.island.R
import app.veltrix.island.SettingsStore
import org.json.JSONObject

class PrayerAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PRAYER = "app.veltrix.island.action.PRAYER_TIME"
        const val ACTION_REFRESH = "app.veltrix.island.action.REFRESH_PRAYERS"
        const val EXTRA_NAME = "prayer_name"
        const val EXTRA_ID = "prayer_id"
        const val EXTRA_TIME = "prayer_time"
        const val EXTRA_PRE_ALERT = "pre_alert"
        const val EXTRA_PRE_MINUTES = "pre_minutes"
        const val CHANNEL = "veltrix_prayer_alerts"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore(context).getBoolean("module_prayer", false)) return
        when (intent.action) {
            ACTION_REFRESH -> refreshTomorrow(context)
            ACTION_PRAYER -> showPrayerAlert(
                context,
                intent.getStringExtra(EXTRA_NAME).orEmpty(),
                intent.getIntExtra(EXTRA_ID, 6200),
                intent.getStringExtra(EXTRA_TIME).orEmpty(),
                intent.getBooleanExtra(EXTRA_PRE_ALERT, false),
                intent.getIntExtra(EXTRA_PRE_MINUTES, 0)
            )
        }
    }

    private fun showPrayerAlert(context: Context, prayer: String, id: Int, time: String, preAlert: Boolean, preMinutes: Int) {
        if (prayer.isBlank()) return
        IslandRuntime.updateAdhan(prayer, time, preAlert)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Prayer reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Optional VELTRIX prayer-time reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 260, 140, 260)
            })
        }
        val notificationId = if (preAlert) id else 6200
        val accept = PendingIntent.getBroadcast(
            context, id + 100,
            Intent(context, PrayerActionReceiver::class.java).setAction(PrayerActionReceiver.ACTION_ACCEPT).putExtra(EXTRA_ID, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = PendingIntent.getActivity(
            context, id + 200,
            Intent(context, app.veltrix.island.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (preAlert) "Adzan $prayer sebentar lagi" else "Adzan $prayer")
            .setContentText(if (preAlert) "Adzan dimulai dalam $preMinutes menit · $time" else "Waktu salat $prayer telah tiba · $time")
            .setContentIntent(content)
            .setAutoCancel(preAlert)
            .setOngoing(!preAlert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .apply { if (!preAlert) addAction(0, "ACCEPT", accept) }
            .build()
        manager.notify(notificationId, notification)
        if (SettingsStore(context).getBoolean("adhan_vibration", true)) {
            val vibrator: android.os.Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            val duration = if (preAlert) 260L else 420L
            runCatching {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        if (preAlert) return

        val audioUri = SettingsStore(context).getString("adhan_audio_uri")
        if (audioUri.isNotBlank()) {
            val play = Intent(context, AdhanPlaybackService::class.java)
                .setAction(AdhanPlaybackService.ACTION_PLAY)
                .putExtra(AdhanPlaybackService.EXTRA_URI, audioUri)
                .putExtra(EXTRA_NAME, prayer)
            runCatching { ContextCompat.startForegroundService(context, play) }
        }
    }

    private fun refreshTomorrow(context: Context) {
        val store = SettingsStore(context)
        val lat = store.getString("prayer_latitude").toDoubleOrNull() ?: return
        val lon = store.getString("prayer_longitude").toDoubleOrNull() ?: return
        val pendingResult = goAsync()
        Thread {
            try {
                val job = kotlinx.coroutines.runBlocking { PrayerTimesRepository.fetch(lat, lon) }
                PrayerAlarmScheduler.saveAndSchedule(context, job.asMap(), lat, lon, job.hijriDate)
            } catch (_: Exception) {
                PrayerAlarmScheduler.restoreCachedSchedule(context)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

class PrayerActionReceiver : BroadcastReceiver() {
    companion object { const val ACTION_ACCEPT = "app.veltrix.island.action.PRAYER_ACCEPT" }
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(PrayerAlarmReceiver.EXTRA_ID, 6200)
        context.getSystemService(NotificationManager::class.java).cancel(id)
        IslandRuntime.clearAdhan()
        context.stopService(Intent(context, AdhanPlaybackService::class.java))
    }
}

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore(context).getBoolean("module_prayer", false)) return
        PrayerAlarmScheduler.restoreCachedSchedule(context)
    }
}
