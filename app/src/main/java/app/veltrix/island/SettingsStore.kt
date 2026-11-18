package app.veltrix.island

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("veltrix_settings", Context.MODE_PRIVATE)

    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
}

data class ModuleSpec(
    val id: String,
    val title: String,
    val description: String,
    val group: String,
    val integration: String = "Core"
)

object ModuleCatalog {
    val all = listOf(
        ModuleSpec("music", "Music Island", "Track, artist, playback state and media controls.", "ACTIVITY", "Core"),
        ModuleSpec("notifications", "Notification Island", "Short notification banners with preview controls.", "ACTIVITY", "Core"),
        ModuleSpec("battery", "Battery Intelligence", "Battery percentage and charging state.", "ACTIVITY", "Core"),
        ModuleSpec("prayer", "Adzan Reminder", "Optional prayer schedule and reminder notifications.", "LIFESTYLE", "Integrated"),
        ModuleSpec("prayer_schedule", "Prayer Schedule", "Daily five-prayer timetable and next-prayer countdown.", "LIFESTYLE", "Integrated"),
        ModuleSpec("weather", "Weather Island", "Weather summary and forecast cards.", "ACTIVITY", "Provider"),
        ModuleSpec("timer", "Timer & Stopwatch", "Countdown and stopwatch activity cards.", "TOOLS", "Extension"),
        ModuleSpec("clipboard", "Clipboard Island", "Quick access to recently copied text.", "TOOLS", "Extension"),
        ModuleSpec("navigation", "Navigation Island", "Turn guidance from supported navigation notifications.", "ACTIVITY", "Extension"),
        ModuleSpec("live_score", "Live Score", "Live match scores and match status.", "ACTIVITY", "Provider"),
        ModuleSpec("quick_tools", "Quick Tools", "Calculator, notes, search and app shortcuts.", "TOOLS", "Extension"),
        ModuleSpec("bluetooth", "Bluetooth Connect", "Connected device state when Android exposes it.", "ACTIVITY", "Extension"),
        ModuleSpec("focus", "Focus Mode", "A focused session indicator.", "TOOLS", "Extension"),
        ModuleSpec("pomodoro", "Pomodoro", "Work and break intervals.", "TOOLS", "Extension"),
        ModuleSpec("screen_activity", "Screen Activity", "Privacy status only when Android exposes a supported signal.", "SYSTEM", "Platform-limited"),
        ModuleSpec("app_launch", "App Launch", "Pinned app shortcuts.", "TOOLS", "Extension"),
        ModuleSpec("volume", "Volume HUD", "Compact volume feedback.", "SYSTEM", "Platform-limited"),
        ModuleSpec("network", "Network Monitor", "Connection state and network quality summary.", "SYSTEM", "Extension"),
        ModuleSpec("data", "Data Monitor", "Mobile data usage summary where accessible.", "SYSTEM", "Platform-limited"),
        ModuleSpec("notes", "Mini Notes", "Fast local notes.", "TOOLS", "Extension"),
        ModuleSpec("qr", "QR Scanner", "Launch a QR scanner shortcut.", "TOOLS", "Extension"),
        ModuleSpec("temperature", "Device Temperature", "Thermal status when the device exposes it.", "SYSTEM", "Platform-limited"),
        ModuleSpec("lyrics", "Live Lyrics", "Timestamp-synced LRC lyrics for the active media session.", "MEDIA", "LRC import")
    )

    fun defaultEnabled(id: String): Boolean = id in setOf("music", "notifications", "battery", "lyrics")
}
