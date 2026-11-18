package app.veltrix.island.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.veltrix.island.SettingsStore
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object PrayerAlarmScheduler {
    fun saveAndSchedule(
        context: Context,
        times: Map<String, String>,
        latitude: Double,
        longitude: Double,
        hijriDate: String = ""
    ) {
        val store = SettingsStore(context)
        store.putString("prayer_times_json", JSONObject(times).toString())
        store.putString("prayer_latitude", latitude.toString())
        store.putString("prayer_longitude", longitude.toString())
        store.putString("prayer_hijri", hijriDate)
        scheduleToday(context, times)
    }

    fun readCachedSchedule(context: Context): PrayerSchedule? {
        val store = SettingsStore(context)
        val raw = store.getString("prayer_times_json")
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val values = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya").map { json.getString(it) }
            if (values.any { it.isBlank() }) return null
            PrayerSchedule(
                fajr = values[0],
                dhuhr = values[1],
                asr = values[2],
                maghrib = values[3],
                isha = values[4],
                hijriDate = store.getString("prayer_hijri")
            )
        }.getOrNull()
    }

    fun scheduleToday(context: Context, times: Map<String, String>) {
        if (!SettingsStore(context).getBoolean("module_prayer", false)) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val preMinutes = SettingsStore(context).getInt("adhan_pre_minutes", 5).coerceIn(0, 15)
        times.entries.forEachIndexed { index, entry ->
            val time = runCatching { LocalTime.parse(entry.value) }.getOrNull() ?: return@forEachIndexed
            val trigger = today.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val prayerIntent = Intent(context, PrayerAlarmReceiver::class.java)
                .setAction(PrayerAlarmReceiver.ACTION_PRAYER)
                .putExtra(PrayerAlarmReceiver.EXTRA_NAME, entry.key)
                .putExtra(PrayerAlarmReceiver.EXTRA_TIME, entry.value)
                .putExtra(PrayerAlarmReceiver.EXTRA_ID, 6200 + index)
            val prayerPending = PendingIntent.getBroadcast(
                context, 6200 + index, prayerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (trigger > now) scheduleAlarm(alarmManager, trigger, prayerPending)

            if (preMinutes > 0) {
                val preTrigger = trigger - preMinutes * 60_000L
                if (preTrigger > now) {
                    val preIntent = Intent(context, PrayerAlarmReceiver::class.java)
                        .setAction(PrayerAlarmReceiver.ACTION_PRAYER)
                        .putExtra(PrayerAlarmReceiver.EXTRA_NAME, entry.key)
                        .putExtra(PrayerAlarmReceiver.EXTRA_TIME, entry.value)
                        .putExtra(PrayerAlarmReceiver.EXTRA_ID, 6300 + index)
                        .putExtra(PrayerAlarmReceiver.EXTRA_PRE_ALERT, true)
                        .putExtra(PrayerAlarmReceiver.EXTRA_PRE_MINUTES, preMinutes)
                    val prePending = PendingIntent.getBroadcast(
                        context, 6300 + index, preIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    scheduleAlarm(alarmManager, preTrigger, prePending)
                }
            }
        }
        scheduleDailyRefresh(context)
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, trigger: Long, pending: PendingIntent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }

    private fun scheduleDailyRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val refreshAt = LocalDate.now().plusDays(1).atTime(0, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            context, 6299,
            Intent(context, PrayerAlarmReceiver::class.java).setAction(PrayerAlarmReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, refreshAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, refreshAt, pending)
            }
        }
    }

    fun restoreCachedSchedule(context: Context) {
        val raw = SettingsStore(context).getString("prayer_times_json")
        if (raw.isBlank()) return
        runCatching {
            val json = JSONObject(raw)
            val map = json.keys().asSequence().associateWith { json.getString(it) }
            scheduleToday(context, map)
        }
    }
}
