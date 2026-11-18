package app.veltrix.island.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.veltrix.island.IslandRuntime
import app.veltrix.island.MainActivity
import app.veltrix.island.R
import app.veltrix.island.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DynamicIslandService : Service() {
    companion object {
        const val ACTION_STOP = "app.veltrix.island.action.STOP_ISLAND"
        const val ACTION_REFRESH = "app.veltrix.island.action.REFRESH_ISLAND"
        private const val SERVICE_NOTIFICATION_ID = 501
        private const val CHANNEL_ID = "veltrix_island_service"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var statusView: TextView
    private lateinit var satelliteView: TextView
    private lateinit var previousView: TextView
    private lateinit var playPauseView: TextView
    private lateinit var nextView: TextView
    private lateinit var expandedDivider: View
    private lateinit var expandedControls: LinearLayout
    private lateinit var acceptView: TextView
    private var lastScheduledNotificationAt: Long = 0L
    private var lastRenderedTitle: String = ""
    private var lastRenderedSubtitle: String = ""
    private var lastAppliedWidth: Int = 0
    private var lastAppliedHeight: Int = 0
    private var lastAnimatedExpanded: Boolean? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var attached = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra("level", -1) ?: -1
            val scale = intent?.getIntExtra("scale", 100) ?: 100
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = intent?.getIntExtra("status", -1) ?: -1
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            IslandRuntime.updateBattery(percent, charging)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch { IslandRuntime.state.collect { render(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SettingsStore(this).putBoolean("master_enabled", false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            if (!attached) attachOverlay()
            lastScheduledNotificationAt = 0L
            render(IslandRuntime.state.value)
            return START_STICKY
        }
        if (!attached) attachOverlay()
        return START_STICKY
    }

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DynamicIslandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("VELTRIX Island is active")
            .setContentText("Tap to open control center")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Turn off", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(SERVICE_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "VELTRIX Island", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildOverlay() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF111319.toInt(), 0xFF08090D.toInt(), 0xFF171015.toInt())
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), 0xFF39313B.toInt())
            }
            elevation = dp(12).toFloat()
            clipToPadding = false
            clipChildren = false
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "V"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFF8790.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF382027.toInt())
                setStroke(dp(1), 0xFF8D4652.toInt())
            }
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(23), dp(23)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, 0, 0) }
        titleView = TextView(this).apply {
            text = "VELTRIX READY"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(0xFFF5F3F5.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        subtitleView = TextView(this).apply {
            text = "Tap to expand"
            textSize = 9f
            setTextColor(0xFF96939D.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        labels.addView(titleView)
        labels.addView(subtitleView)
        header.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusView = TextView(this).apply {
            text = "●"
            textSize = 10f
            setTextColor(0xFFFF6673.toInt())
        }
        header.addView(statusView)
        satelliteView = TextView(this).apply {
            text = "♫"
            textSize = 10f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC2C6.toInt())
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF30202A.toInt(), 0xFF17141D.toInt())
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF75404C.toInt())
            }
            visibility = View.GONE
            maxLines = 1
        }
        header.addView(satelliteView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(27)).apply { leftMargin = dp(7) })
        root.addView(header)

        expandedDivider = View(this).apply { setBackgroundColor(0xFF302B33.toInt()); visibility = View.GONE }
        root.addView(expandedDivider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(9); bottomMargin = dp(8) })
        expandedControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        val controls = expandedControls
        val hint = TextView(this).apply {
            text = "LIVE ACTIVITY"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(0xFF8E8994.toInt())
        }
        controls.addView(hint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        previousView = TextView(this).apply {
            text = "«"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFFF8F4F6.toInt())
            setOnClickListener { IslandRuntime.skipPrevious() }
        }
        playPauseView = TextView(this).apply {
            text = "Ⅱ"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFFF8F4F6.toInt())
            setOnClickListener { IslandRuntime.playPause() }
        }
        nextView = TextView(this).apply {
            text = "»"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFFF8F4F6.toInt())
            setOnClickListener { IslandRuntime.skipNext() }
        }
        controls.addView(previousView, LinearLayout.LayoutParams(dp(34), dp(34)))
        controls.addView(playPauseView, LinearLayout.LayoutParams(dp(34), dp(34)))
        controls.addView(nextView, LinearLayout.LayoutParams(dp(34), dp(34)))
        acceptView = TextView(this).apply {
            text = "ACCEPT"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFB9BF.toInt())
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                val accept = Intent(this@DynamicIslandService, app.veltrix.island.prayer.PrayerActionReceiver::class.java)
                    .setAction(app.veltrix.island.prayer.PrayerActionReceiver.ACTION_ACCEPT)
                    .putExtra(app.veltrix.island.prayer.PrayerAlarmReceiver.EXTRA_ID, 6200)
                sendBroadcast(accept)
            }
        }
        controls.addView(acceptView, LinearLayout.LayoutParams(dp(62), dp(34)))
        root.addView(controls)
        root.setOnClickListener { expanded = !expanded; applySize() }
        root.setOnLongClickListener { SettingsStore(this).putBoolean("master_enabled", false); stopSelf(); true }
    }

    private fun attachOverlay() {
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val maxWidthDp = (screenWidthDp - 28).coerceAtLeast(120)
        val initialWidth = dp(minOf(230, maxWidthDp))
        params = WindowManager.LayoutParams(
            initialWidth,
            dp(45),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topSafeOffset()
        }
        runCatching {
            windowManager.addView(root, params)
            attached = true
            root.post { updateSafeOffset() }
        }.onFailure { stopSelf() }
    }

    private fun updateSafeOffset() {
        val p = params ?: return
        val insetsTop = if (Build.VERSION.SDK_INT >= 28) runCatching { root.rootWindowInsets?.displayCutout?.safeInsetTop ?: 0 }.getOrDefault(0) else 0
        val newY = maxOf(statusBarHeight(), insetsTop) + dp(5)
        if (p.y != newY) {
            p.y = newY
            runCatching { windowManager.updateViewLayout(root, p) }
        }
    }

    private fun topSafeOffset(): Int = maxOf(statusBarHeight(), 0) + dp(5)

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun render(state: app.veltrix.island.IslandUiState) {
        val prefs = SettingsStore(this)
        applyAppearance(prefs)
        val adhanActive = state.adhanTitle.isNotBlank() && (!state.adhanIsPreAlert || SystemClockAge(state.adhanAt) < 90_000L) && prefs.getBoolean("module_prayer", false)
        val notificationDurationMs = prefs.getInt("notification_duration_seconds", 3).coerceIn(1, 7) * 1_000L
        val notificationActive = state.notificationTitle.isNotBlank() && SystemClockAge(state.notificationAt) < notificationDurationMs && prefs.getBoolean("module_notifications", true)
        val batteryActive = prefs.getBoolean("module_battery", true) && state.batteryPercent >= 0
        val musicActive = prefs.getBoolean("module_music", true) && state.mediaTitle != "Nothing playing"
        val chargingActive = batteryActive && state.charging
        val activeKinds = buildList {
            if (adhanActive) add("adhan")
            if (notificationActive) add("notification")
            if (musicActive) add("music")
            if (chargingActive) add("battery")
        }
        val primaryKind = when {
            adhanActive -> "adhan"
            notificationActive -> "notification"
            musicActive -> "music"
            chargingActive -> "battery"
            else -> "idle"
        }
        val secondaryKinds = activeKinds.filterNot { it == primaryKind }
        satelliteView.visibility = if (secondaryKinds.isNotEmpty()) View.VISIBLE else View.GONE
        satelliteView.text = when {
            secondaryKinds.size > 1 -> "+${secondaryKinds.size - 1}"
            secondaryKinds.firstOrNull() == "music" -> "♫"
            secondaryKinds.firstOrNull() == "notification" -> "N"
            secondaryKinds.firstOrNull() == "battery" -> "${state.batteryPercent}%"
            secondaryKinds.firstOrNull() == "adhan" -> "A"
            else -> ""
        }
        when {
            adhanActive && prefs.getBoolean("module_prayer", false) && state.adhanIsPreAlert -> {
                setAnimatedText(titleView, "UPCOMING ${state.adhanTitle.uppercase()}", true)
                setAnimatedText(subtitleView, "Prayer reminder · ${state.adhanTime}", false)
                statusView.setTextColor(0xFFE5B75D.toInt())
            }
            adhanActive && prefs.getBoolean("module_prayer", false) -> {
                setAnimatedText(titleView, "ADZAN ${state.adhanTitle.uppercase()}", true)
                setAnimatedText(subtitleView, "Prayer time · ${state.adhanTime}", false)
                statusView.setTextColor(0xFFFF6673.toInt())
            }
            notificationActive && prefs.getBoolean("module_notifications", true) -> {
                setAnimatedText(titleView, state.notificationTitle.ifBlank { "Notification" }, true)
                setAnimatedText(subtitleView, if (prefs.getBoolean("privacy_mode", false) || !prefs.getBoolean("notification_preview", true)) "New notification" else state.notificationText.ifBlank { "New notification" }, false)
                statusView.setTextColor(0xFF6FE2B0.toInt())
            }
            musicActive -> {
                setAnimatedText(titleView, state.mediaTitle, true)
                setAnimatedText(subtitleView, state.mediaArtist + if (state.isPlaying) " · Playing" else " · Paused", false)
                statusView.setTextColor(0xFFFF6673.toInt())
            }
            chargingActive -> {
                setAnimatedText(titleView, "CHARGING  ${state.batteryPercent}%", true)
                setAnimatedText(subtitleView, "Power connected", false)
                statusView.setTextColor(0xFF78D9AB.toInt())
            }
            else -> {
                setAnimatedText(titleView, "VELTRIX ACTIVE", true)
                setAnimatedText(subtitleView, if (batteryActive) "Battery ${state.batteryPercent}% · Tap to expand" else "Tap to expand", false)
                statusView.setTextColor(0xFFFF6673.toInt())
            }
        }
        playPauseView.text = if (state.isPlaying) "Ⅱ" else "▶"
        acceptView.visibility = if (adhanActive && !state.adhanIsPreAlert && prefs.getBoolean("module_prayer", false)) View.VISIBLE else View.GONE
        if (notificationActive && state.notificationAt != lastScheduledNotificationAt) {
            lastScheduledNotificationAt = state.notificationAt
            root.postDelayed({ render(IslandRuntime.state.value) }, notificationDurationMs + 120L)
        }
        previousView.visibility = if (musicActive) View.VISIBLE else View.GONE
        playPauseView.visibility = if (musicActive) View.VISIBLE else View.GONE
        nextView.visibility = if (musicActive) View.VISIBLE else View.GONE
        if (attached) applySize()
    }

    private fun setAnimatedText(view: TextView, value: String, primary: Boolean) {
        val previous = if (primary) lastRenderedTitle else lastRenderedSubtitle
        if (previous == value) return
        if (primary) lastRenderedTitle = value else lastRenderedSubtitle = value
        view.animate().cancel()
        if (!animationsAllowed()) {
            view.alpha = 1f
            view.text = value
            return
        }
        view.animate().alpha(0f).setDuration(85L).withEndAction {
            view.text = value
            view.animate().alpha(1f).setDuration(145L).start()
        }.start()
    }

    private fun applySize() {
        val p = params ?: return
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val maxWidthDp = (screenWidthDp - 24).coerceAtLeast(120)
        val desiredWidthDp = if (expanded) 330 else if (satelliteView.visibility == View.VISIBLE) 286 else 230
        val newWidth = dp(minOf(desiredWidthDp, maxWidthDp))
        val newHeight = dp(if (expanded) 105 else 45)
        val layoutChanged = newWidth != lastAppliedWidth || newHeight != lastAppliedHeight
        p.width = newWidth
        p.height = newHeight
        lastAppliedWidth = newWidth
        lastAppliedHeight = newHeight
        expandedDivider.visibility = if (expanded) View.VISIBLE else View.GONE
        expandedControls.visibility = if (expanded) View.VISIBLE else View.GONE
        if (lastAnimatedExpanded != expanded) {
            lastAnimatedExpanded = expanded
            root.animate().cancel()
            if (animationsAllowed()) {
                root.animate().scaleX(if (expanded) 1f else 0.985f).scaleY(1f).setDuration(220L).start()
            } else {
                root.scaleX = 1f
                root.scaleY = 1f
            }
        }
        if (attached && layoutChanged) runCatching { windowManager.updateViewLayout(root, p) }
    }

    private fun applyAppearance(prefs: SettingsStore) {
        val glow = prefs.getBoolean("glow_enabled", true)
        (root.background as? GradientDrawable)?.setStroke(
            dp(if (glow) 2 else 1),
            if (glow) 0xFF75404C.toInt() else 0xFF39313B.toInt()
        )
        root.elevation = dp(if (glow) 13 else 4).toFloat()
    }

    private fun animationsAllowed(): Boolean {
        val prefs = SettingsStore(this)
        return prefs.getBoolean("motion_enabled", true) && !prefs.getBoolean("reduced_motion", false)
    }

    private fun SystemClockAge(timestamp: Long): Long = if (timestamp <= 0L) Long.MAX_VALUE else android.os.SystemClock.elapsedRealtime() - timestamp
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (attached) {
            applySize()
            updateSafeOffset()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(batteryReceiver) }
        if (attached) runCatching { windowManager.removeView(root) }
        attached = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
