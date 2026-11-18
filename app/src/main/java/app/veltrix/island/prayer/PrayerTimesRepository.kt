package app.veltrix.island.prayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PrayerSchedule(
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val hijriDate: String,
    val source: String = "AlAdhan · KEMENAG method 20"
) {
    fun asMap(): Map<String, String> = linkedMapOf(
        "Subuh" to fajr,
        "Dzuhur" to dhuhr,
        "Ashar" to asr,
        "Maghrib" to maghrib,
        "Isya" to isha
    )
}

object PrayerTimesRepository {
    suspend fun fetch(latitude: Double, longitude: Double): PrayerSchedule = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Coordinates are outside the valid range." }
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val address = "https://api.aladhan.com/v1/timings/$date?latitude=$latitude&longitude=$longitude&method=20"
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("User-Agent", "VELTRIX-Android")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Prayer-time service returned HTTP $code")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = json.getJSONObject("data")
            val timings = data.getJSONObject("timings")
            val hijri = data.getJSONObject("date").getJSONObject("hijri").optString("date", "")
            PrayerSchedule(
                cleanTime(timings.getString("Fajr")),
                cleanTime(timings.getString("Dhuhr")),
                cleanTime(timings.getString("Asr")),
                cleanTime(timings.getString("Maghrib")),
                cleanTime(timings.getString("Isha")),
                hijri
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanTime(value: String): String = value.substringBefore(" ").take(5)
}
