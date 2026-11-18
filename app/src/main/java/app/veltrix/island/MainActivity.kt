package app.veltrix.island

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import app.veltrix.island.lyrics.LrcLine
import app.veltrix.island.lyrics.LrcParser
import app.veltrix.island.overlay.DynamicIslandService
import app.veltrix.island.prayer.PrayerAlarmScheduler
import app.veltrix.island.prayer.PrayerSchedule
import app.veltrix.island.prayer.PrayerTimesRepository
import app.veltrix.island.updates.ReleaseInfo
import app.veltrix.island.updates.UpdateCheck
import app.veltrix.island.updates.UpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private val Night = Color(0xFF08090D)
private val Panel = Color(0xFF11141B)
private val Panel2 = Color(0xFF171B24)
private val Line = Color(0xFF292E39)
private val TextMain = Color(0xFFF4F2F5)
private val TextMuted = Color(0xFF969AA7)
private val Accent = Color(0xFFFF5868)
private val Accent2 = Color(0xFFFF9B73)
private val Good = Color(0xFF5ED6A1)
private val Amber = Color(0xFFE5B75D)
private val Bad = Color(0xFFE15D6B)

private enum class AppTab(val label: String, val glyph: String) {
    HOME("Home", "⌂"), MODULES("Modules", "◈"), LYRICS("Lyrics", "≋"), SETTINGS("Settings", "⌘"), ABOUT("About", "ⓘ"), PRAYER("Prayer", "◷")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent,
                secondary = Accent2,
                background = Night,
                surface = Panel,
                onPrimary = Color.White,
                onBackground = TextMain,
                onSurface = TextMain
            )) {
                VeltrixApp(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val store = SettingsStore(this)
        if (store.getBoolean("master_enabled", false) && Settings.canDrawOverlays(this)) {
            runCatching { ContextCompat.startForegroundService(this, Intent(this, DynamicIslandService::class.java)) }
        }
    }
}

@Composable
private fun VeltrixApp(activity: MainActivity) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val runtime by IslandRuntime.state.collectAsState()
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var masterEnabled by remember { mutableStateOf(store.getBoolean("master_enabled", false)) }
    var repository by remember { mutableStateOf(store.getString("github_repository")) }
    var lrcLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var lyricPosition by remember { mutableLongStateOf(0L) }
    var prayerSchedule by remember { mutableStateOf<PrayerSchedule?>(null) }
    var prayerLoading by remember { mutableStateOf(false) }
    var prayerError by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(store.getString("prayer_latitude")) }
    var longitude by remember { mutableStateOf(store.getString("prayer_longitude")) }
    var adhanAudio by remember { mutableStateOf(store.getString("adhan_audio_uri")) }
    var updateCheck by remember { mutableStateOf<UpdateCheck?>(null) }
    var updateLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                lrcLines = LrcParser.parse(text)
                store.putString("lyrics_uri", uri.toString())
                store.putString("lyrics_name", uri.lastPathSegment ?: "Imported LRC")
                if (lrcLines.isEmpty()) Toast.makeText(context, "No timestamped LRC lines were found.", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, "Could not read that lyrics file.", Toast.LENGTH_LONG).show() }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                adhanAudio = uri.toString()
                store.putString("adhan_audio_uri", uri.toString())
                Toast.makeText(context, "Adhan audio selected.", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "Could not save audio access.", Toast.LENGTH_LONG).show() }
        }
    }

    val locationPicker = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            val location = readLastLocation(activity)
            if (location != null) {
                latitude = location.first.toString()
                longitude = location.second.toString()
                store.putString("prayer_latitude", latitude)
                store.putString("prayer_longitude", longitude)
                Toast.makeText(context, "Location loaded. Refresh the prayer schedule.", Toast.LENGTH_LONG).show()
            } else {
                prayerError = "No recent location fix. Enter coordinates manually or open a maps app once, then retry."
            }
        }
    }

    LaunchedEffect(Unit) {
        val savedLyrics = store.getString("lyrics_uri")
        if (savedLyrics.isNotBlank()) {
            runCatching {
                val text = context.contentResolver.openInputStream(Uri.parse(savedLyrics))?.bufferedReader()?.use { it.readText() }.orEmpty()
                LrcParser.parse(text)
            }.onSuccess { lrcLines = it }
        }
        prayerSchedule = PrayerAlarmScheduler.readCachedSchedule(context)
    }

    LaunchedEffect(tab) {
        if (tab == AppTab.LYRICS && store.getBoolean("module_lyrics", ModuleCatalog.defaultEnabled("lyrics"))) {
            while (isActive) {
                lyricPosition = IslandRuntime.currentPosition()
                delay(250)
            }
        }
    }

    LaunchedEffect(tab, repository) {
        if (tab == AppTab.ABOUT && repository.contains('/') && store.getBoolean("auto_update_check", true)) {
            updateLoading = true
            updateCheck = runCatching { UpdateRepository.check(repository, BuildConfig.VERSION_NAME) }
                .getOrElse { UpdateCheck("error", it.message ?: "Update check failed.") }
            updateLoading = false
        }
    }

    fun setMaster(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(context)) {
            masterEnabled = false
            store.putBoolean("master_enabled", false)
            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            Toast.makeText(context, "Allow display over other apps, then turn the Island on again.", Toast.LENGTH_LONG).show()
            return
        }
        masterEnabled = enabled
        store.putBoolean("master_enabled", enabled)
        if (enabled) {
            ContextCompat.startForegroundService(context, Intent(context, DynamicIslandService::class.java))
        } else {
            activity.stopService(Intent(context, DynamicIslandService::class.java))
        }
    }

    fun fetchPrayerTimes(lat: Double, lon: Double) {
        prayerLoading = true
        prayerError = ""
        scope.launch {
            runCatching { PrayerTimesRepository.fetch(lat, lon) }
                .onSuccess { schedule ->
                    prayerSchedule = schedule
                    latitude = lat.toString()
                    longitude = lon.toString()
                    store.putString("prayer_latitude", latitude)
                    store.putString("prayer_longitude", longitude)
                    PrayerAlarmScheduler.saveAndSchedule(context, schedule.asMap(), lat, lon, schedule.hijriDate)
                    Toast.makeText(context, "Prayer times refreshed. Reminders follow the Adzan setting.", Toast.LENGTH_LONG).show()
                }
                .onFailure { prayerError = it.message ?: "Could not retrieve prayer times." }
            prayerLoading = false
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0x283F2433), Color.Transparent)), radius = size.width * 0.72f, center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.04f))
            drawCircle(Brush.radialGradient(listOf(Color(0x1C8D303B), Color.Transparent)), radius = size.width * 0.62f, center = androidx.compose.ui.geometry.Offset(size.width * 0.02f, size.height * 0.58f))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            AnimatedContent(targetState = tab, label = "screen_transition", modifier = Modifier.weight(1f)) { current ->
                when (current) {
                    AppTab.HOME -> HomeScreen(
                        enabled = masterEnabled,
                        onMasterChange = ::setMaster,
                        onNavigate = { tab = it },
                        state = runtime,
                        store = store
                    )
                    AppTab.MODULES -> ModulesScreen(store)
                    AppTab.LYRICS -> LyricsScreen(
                        state = runtime,
                        enabled = store.getBoolean("module_lyrics", ModuleCatalog.defaultEnabled("lyrics")),
                        lines = lrcLines,
                        position = lyricPosition,
                        onImport = { lyricsPicker.launch(arrayOf("text/*", "application/octet-stream")) }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        store = store,
                        masterEnabled = masterEnabled,
                        onMasterChange = ::setMaster,
                        repository = repository,
                        onRepositoryChange = { repository = it; store.putString("github_repository", it) },
                        onOpenOverlayPermission = { activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                        onOpenNotificationPermission = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpenAppNotifications = { activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                        onChooseAdhanAudio = { audioPicker.launch(arrayOf("audio/*")) },
                        onAdhanAudioClear = { adhanAudio = ""; store.putString("adhan_audio_uri", "") }
                    )
                    AppTab.ABOUT -> AboutScreen(
                        repository = repository,
                        currentVersion = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                        check = updateCheck,
                        loading = updateLoading,
                        onRefresh = {
                            if (!repository.contains('/')) {
                                updateCheck = UpdateCheck("error", "Set your public GitHub repository in Settings first.")
                            } else {
                                scope.launch {
                                    updateLoading = true
                                    updateCheck = runCatching { UpdateRepository.check(repository, BuildConfig.VERSION_NAME) }
                                        .getOrElse { UpdateCheck("error", it.message ?: "Update check failed.") }
                                    updateLoading = false
                                }
                            }
                        },
                        onDownload = { release -> downloadRelease(context, release) }
                    )
                    AppTab.PRAYER -> PrayerScreen(
                        store = store,
                        scheduleEnabled = store.getBoolean("module_prayer_schedule", ModuleCatalog.defaultEnabled("prayer_schedule")),
                        schedule = prayerSchedule,
                        loading = prayerLoading,
                        error = prayerError,
                        latitude = latitude,
                        longitude = longitude,
                        onLatitude = { latitude = it },
                        onLongitude = { longitude = it },
                        onBack = { tab = AppTab.HOME },
                        onUseLocation = {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) {
                                val location = readLastLocation(activity)
                                if (location != null) {
                                    latitude = location.first.toString(); longitude = location.second.toString()
                                } else prayerError = "No recent location fix. Enter coordinates manually or try again outdoors."
                            } else locationPicker.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        onFetch = { lat, lon -> fetchPrayerTimes(lat, lon) },
                        onChooseAudio = { audioPicker.launch(arrayOf("audio/*")) },
                        audioSelected = adhanAudio.isNotBlank(),
                        onExactAlarmSettings = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        }
                    )
                }
            }
            BottomNavigation(selected = tab, onSelect = { tab = it })
        }
    }
}

private fun readLastLocation(context: Context): Pair<Double, Double>? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    for (provider in providers) {
        val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (location != null) return location.latitude to location.longitude
    }
    return null
}

private fun downloadRelease(context: Context, release: ReleaseInfo) {
    val url = release.apkUrl
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "This release has no APK asset attached.", Toast.LENGTH_LONG).show()
        return
    }
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("VELTRIX ${release.tag}")
            .setDescription("Downloading VELTRIX update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, release.apkName ?: "veltrix-update.apk")
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(context, "Update download started. Open the completed download to install.", Toast.LENGTH_LONG).show()
    }.onFailure { Toast.makeText(context, "Could not start the download.", Toast.LENGTH_LONG).show() }
}

private fun setModuleEnabled(context: Context, store: SettingsStore, moduleId: String, enabled: Boolean) {
    store.putBoolean("module_${moduleId}", enabled)
    if (moduleId == "prayer") {
        if (enabled) {
            PrayerAlarmScheduler.restoreCachedSchedule(context)
        } else {
            context.stopService(Intent(context, app.veltrix.island.prayer.AdhanPlaybackService::class.java))
            IslandRuntime.clearAdhan()
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            (6200..6204).forEach { notifications.cancel(it) }
            (6300..6304).forEach { notifications.cancel(it) }
            notifications.cancel(6350)
        }
    }
    refreshIslandIfEnabled(context)
}

private fun refreshIslandIfEnabled(context: Context) {
    val store = SettingsStore(context)
    if (store.getBoolean("master_enabled", false) && Settings.canDrawOverlays(context)) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DynamicIslandService::class.java).setAction(DynamicIslandService.ACTION_REFRESH)
            )
        }
    }
}

@Composable
private fun BottomNavigation(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 7.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xE812151C)).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(AppTab.HOME, AppTab.MODULES, AppTab.LYRICS, AppTab.SETTINGS, AppTab.ABOUT).forEach { item ->
            val active = selected == item
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color(0xFF30202A) else Color.Transparent)
                    .clickable { onSelect(item) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(item.glyph, color = if (active) Accent2 else TextMuted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(item.label, color = if (active) TextMain else TextMuted, fontSize = 9.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    enabled: Boolean,
    onMasterChange: (Boolean) -> Unit,
    onNavigate: (AppTab) -> Unit,
    state: IslandUiState,
    store: SettingsStore
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("CONTROL / CENTER", color = TextMuted, fontSize = 9.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.SemiBold)
                Text("VELTRIX", color = TextMain, fontSize = 25.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.clip(CircleShape).background(Color(0xFF191D26)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("BUILD  001", color = Accent2, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF562630), Color(0xFF281C2B), Color(0xFF171923)))
        ).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("DYNAMIC ISLAND", color = Color(0xFFFFC1C1), fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                        Text(if (enabled) "System is active" else "System is standing by", color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    PremiumSwitch(checked = enabled, onCheckedChange = onMasterChange)
                }
                Text("A compact activity layer for your Android screen.", color = Color(0xFFC8B7BF), fontSize = 12.sp, lineHeight = 18.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (enabled) Good else TextMuted))
                        Text(if (enabled) "OVERLAY ENABLED" else "OVERLAY DISABLED", color = if (enabled) Good else TextMuted, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("MINI  /  EXPANDED", color = Color(0xFFB7AAB2), fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionHeading("LIVE PREVIEW", "The overlay adapts to the current activity")
            IslandPreview(state)
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionHeading("QUICK ACCESS", "Jump directly into a module")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("◷", "Prayer schedule", "KEMENAG method", Modifier.weight(1f)) { onNavigate(AppTab.PRAYER) }
                QuickCard("≋", "Live lyrics", "Timestamp sync", Modifier.weight(1f)) { onNavigate(AppTab.LYRICS) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("◈", "All modules", "23 module controls", Modifier.weight(1f)) { onNavigate(AppTab.MODULES) }
                QuickCard("⌘", "Preferences", "Visual & behavior", Modifier.weight(1f)) { onNavigate(AppTab.SETTINGS) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionHeading("SYSTEM NOTES", "Designed to avoid screen-edge collisions")
            InfoRow("Adaptive width", "The floating pill is constrained to the available screen width.")
            InfoRow("Permission aware", "Overlay and notification access are requested only when needed.")
            InfoRow("Local settings", "Module choices and preferences stay on this device.")
        }
    }
}

@Composable
private fun IslandPreview(state: IslandUiState) {
    val label = when {
        state.adhanTitle.isNotBlank() -> "ADZAN ${state.adhanTitle.uppercase(Locale.getDefault())}"
        state.notificationTitle.isNotBlank() -> state.notificationTitle
        state.mediaTitle != "Nothing playing" -> state.mediaTitle
        state.charging -> "CHARGING ${state.batteryPercent}%"
        else -> "VELTRIX ACTIVE"
    }
    val detail = when {
        state.adhanTitle.isNotBlank() -> "Prayer time has arrived"
        state.notificationTitle.isNotBlank() -> state.notificationText
        state.mediaTitle != "Nothing playing" -> state.mediaArtist
        else -> "Waiting for an activity"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF252731), Color(0xFF0C0D12), Color(0xFF24151F))))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail.ifBlank { "Waiting for an activity" }, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (state.isPlaying) "Ⅱ" else "◈", color = Accent2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickCard(glyph: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(glyph, color = Accent2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(title, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = TextMain, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun InfoRow(title: String, description: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.Top) {
        Text("/", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ModulesScreen(store: SettingsStore) {
    val context = LocalContext.current
    var enabledMap by remember { mutableStateOf(ModuleCatalog.all.associate { it.id to store.getBoolean("module_${it.id}", ModuleCatalog.defaultEnabled(it.id)) }) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        ScreenHeader("Modules", "Control every Island module independently.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ModuleCatalog.all.groupBy { it.group }.forEach { (group, modules) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.replace('_', ' '), color = Accent2, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    modules.forEach { module ->
                        ModuleRow(module, enabledMap[module.id] ?: false) { enabled ->
                            setModuleEnabled(context, store, module.id, enabled)
                            enabledMap = enabledMap.toMutableMap().apply { put(module.id, enabled) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModuleRow(module: ModuleSpec, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(module.title, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IntegrationChip(module.integration)
            }
            Text(module.description, color = TextMuted, fontSize = 9.sp, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        PremiumSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun IntegrationChip(value: String) {
    val color = when (value) { "Core", "Integrated", "LRC import" -> Good; "Provider" -> Amber; else -> TextMuted }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(value.uppercase(), color = color, fontSize = 7.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LyricsScreen(state: IslandUiState, enabled: Boolean, lines: List<LrcLine>, position: Long, onImport: () -> Unit) {
    val active = LrcParser.activeIndex(lines, position)
    val listState = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem(active) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Live Lyrics", "LRC timestamps follow the active Android media session.")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A202A), Panel2, Panel))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("NOW PLAYING", color = Color(0xFFFFB7BC), fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
            Text(state.mediaTitle, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.mediaArtist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.isPlaying) "PLAYING" else "PAUSED / IDLE", color = if (state.isPlaying) Good else TextMuted, fontSize = 8.sp, letterSpacing = 1.sp)
                Text(formatPosition(position), color = TextMuted, fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (lines.isEmpty()) "NO LYRICS LOADED" else "SYNCHRONIZED LYRICS", color = Accent2, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onImport) { Text("IMPORT .LRC", color = Accent2, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        if (!enabled || lines.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Panel).padding(18.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("≋", color = Accent, fontSize = 34.sp)
                    Text(if (enabled) "Bring your own timestamped lyrics" else "Live Lyrics is disabled", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(if (enabled) "Import an .LRC file. The active line follows playback position; Spotify does not expose lyrics through its public Web API." else "Enable Live Lyrics in Modules to use synchronized lyrics.", color = TextMuted, fontSize = 10.sp, lineHeight = 16.sp)
                    if (enabled) Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Choose LRC file", color = Color.White) }
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(lines) { index, line ->
                    val selected = index == active
                    Text(
                        line.text,
                        color = if (selected) TextMain else TextMuted.copy(alpha = 0.72f),
                        fontSize = if (selected) 19.sp else 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = if (selected) 27.sp else 21.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                            .background(if (selected) Color(0xFF30212A) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

private fun formatPosition(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

@Composable
private fun SettingsScreen(
    store: SettingsStore,
    masterEnabled: Boolean,
    onMasterChange: (Boolean) -> Unit,
    repository: String,
    onRepositoryChange: (String) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onOpenAppNotifications: () -> Unit,
    onChooseAdhanAudio: () -> Unit,
    onAdhanAudioClear: () -> Unit
) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(store.getBoolean("sfx_enabled", true)) }
    var vibration by remember { mutableStateOf(store.getBoolean("vibration_enabled", true)) }
    var autoCheck by remember { mutableStateOf(store.getBoolean("auto_update_check", true)) }
    var reducedMotion by remember { mutableStateOf(store.getBoolean("reduced_motion", false)) }
    var moduleEnabled by remember { mutableStateOf(ModuleCatalog.all.associate { it.id to store.getBoolean("module_${it.id}", ModuleCatalog.defaultEnabled(it.id)) }) }
    var notificationDuration by remember { mutableIntStateOf(store.getInt("notification_duration_seconds", 3).let { if (it in listOf(1, 3, 5, 7)) it else 3 }) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Settings", "Tune the behavior, look and permissions.")
        SettingsGroup("SYSTEM CONTROL") {
            PreferenceSwitch("Dynamic Island", "Master switch for the floating overlay service.", masterEnabled, onMasterChange)
        }
        SettingsGroup("MODULE SWITCHBOARD") {
            Text("Every module can be controlled here independently. Some integrations need their own provider or permission.", color = TextMuted, fontSize = 9.sp, lineHeight = 14.sp)
            ModuleCatalog.all.groupBy { it.group }.forEach { (group, modules) ->
                Text(group.replace('_', ' '), color = Accent2, fontSize = 8.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                modules.forEach { module ->
                    ModuleRow(module, moduleEnabled[module.id] ?: false) { enabled ->
                        setModuleEnabled(context, store, module.id, enabled)
                        moduleEnabled = moduleEnabled.toMutableMap().apply { put(module.id, enabled) }
                    }
                }
            }
        }
        SettingsGroup("ISLAND APPEARANCE") {
            PreferenceSwitch("Subtle glow", "Soft edge lighting around the pill.", store.getBoolean("glow_enabled", true)) { store.putBoolean("glow_enabled", it); refreshIslandIfEnabled(context) }
            PreferenceSwitch("Motion effects", "Smooth transitions for active states.", store.getBoolean("motion_enabled", true)) { store.putBoolean("motion_enabled", it); refreshIslandIfEnabled(context) }
            PreferenceSwitch("Reduced motion", "Use minimal movement and transitions.", reducedMotion) { reducedMotion = it; store.putBoolean("reduced_motion", it); refreshIslandIfEnabled(context) }
        }
        SettingsGroup("SOUND & FEEDBACK") {
            PreferenceSwitch("Interface SFX", "Subtle interaction sounds.", sound) { sound = it; store.putBoolean("sfx_enabled", it) }
            PreferenceSwitch("Vibration", "Use haptic feedback where permitted.", vibration) { vibration = it; store.putBoolean("vibration_enabled", it) }
        }
        SettingsGroup("NOTIFICATIONS") {
            PreferenceSwitch("Show preview text", "Display a short notification preview.", store.getBoolean("notification_preview", true)) { store.putBoolean("notification_preview", it); refreshIslandIfEnabled(context) }
            PreferenceSwitch("Privacy mode", "Hide notification body text in the Island.", store.getBoolean("privacy_mode", false)) { store.putBoolean("privacy_mode", it); refreshIslandIfEnabled(context) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Banner duration", color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("How long a temporary notification stays in the Island.", color = TextMuted, fontSize = 9.sp)
                }
                TextButton(onClick = {
                    notificationDuration = when (notificationDuration) { 1 -> 3; 3 -> 5; 5 -> 7; else -> 1 }
                    store.putInt("notification_duration_seconds", notificationDuration)
                    refreshIslandIfEnabled(context)
                }) { Text("${notificationDuration}s", color = Accent2, fontWeight = FontWeight.Bold) }
            }
        }
        SettingsGroup("ADZAN REMINDER") {
            Text("Enable or disable Adzan Reminder in Module Switchboard above. The entire module is optional.", color = TextMuted, fontSize = 9.sp, lineHeight = 14.sp)
            PreferenceSwitch("Adzan vibration", "Vibrate before and when prayer time begins.", store.getBoolean("adhan_vibration", true)) { store.putBoolean("adhan_vibration", it) }
            var preMinutes by remember { mutableIntStateOf(store.getInt("adhan_pre_minutes", 5).coerceIn(0, 15)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Pre-adzan alert", color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("Show a small reminder before the prayer time.", color = TextMuted, fontSize = 9.sp)
                }
                TextButton(onClick = {
                    preMinutes = when (preMinutes) { 0 -> 5; 5 -> 10; 10 -> 15; else -> 0 }
                    store.putInt("adhan_pre_minutes", preMinutes)
                }) {
                    Text(when (preMinutes) { 0 -> "OFF"; else -> "$preMinutes MIN" }, color = Accent2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Adzan audio file", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (store.getString("adhan_audio_uri").isBlank()) "Select an audio file to hear the adzan when prayer time begins." else "Your selected adzan audio will play at prayer time.", color = TextMuted, fontSize = 9.sp, lineHeight = 13.sp)
                }
                TextButton(onClick = onChooseAdhanAudio) { Text("SELECT", color = Accent2, fontSize = 9.sp) }
            }
            if (store.getString("adhan_audio_uri").isNotBlank()) TextButton(onClick = onAdhanAudioClear) { Text("Remove selected audio", color = Bad, fontSize = 9.sp) }
        }
        SettingsGroup("PERMISSIONS") {
            PermissionRow("Display over other apps", "Required for the floating Island.", "OPEN SETTINGS", onOpenOverlayPermission)
            PermissionRow("Notification access", "Reads media sessions and notification previews.", "OPEN SETTINGS", onOpenNotificationPermission)
            PermissionRow("Post notifications", "Required to show adzan reminder alerts on Android 13+.", "APP NOTIFICATION SETTINGS", onOpenAppNotifications)
        }
        SettingsGroup("UPDATE CENTER") {
            PreferenceSwitch("Check for updates on About", "Read public GitHub Releases when About is opened.", autoCheck) { autoCheck = it; store.putBoolean("auto_update_check", it) }
            Text("GitHub repository", color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = repository,
                onValueChange = onRepositoryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("owner/repository", color = TextMuted) },
                textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 12.sp)
            )
        }
        SettingsGroup("PERFORMANCE") {
            PreferenceSwitch("Start Island after manual activation", "VELTRIX never enables the overlay without your action.", store.getBoolean("manual_start", true)) { store.putBoolean("manual_start", it) }
            Text("The app does not run a permanent polling loop for disabled modules.", color = TextMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun AboutScreen(
    repository: String,
    currentVersion: String,
    check: UpdateCheck?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit
) {
    val statusColor = when (check?.status) { "available" -> Good; "development" -> Amber; "none" -> Bad; else -> TextMuted }
    val statusTitle = when (check?.status) { "available" -> "UPDATE AVAILABLE"; "development" -> "IN DEVELOPMENT"; "none" -> "NO UPDATE"; else -> "NOT CHECKED" }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("About VELTRIX", "Build information and release channel.")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF53242E), Color(0xFF201A27), Panel))).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VELTRIX", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text("SMART ISLAND / ANDROID", color = Color(0xFFFFB7BC), fontSize = 9.sp, letterSpacing = 1.5.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VERSION", color = TextMuted, fontSize = 9.sp)
                    Text(currentVersion, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("Built for adaptive screens, user-controlled modules and local-first settings.", color = Color(0xFFC8BAC3), fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        SettingsGroup("UPDATE") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                Text(statusTitle, color = statusColor, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            }
            Text(check?.message ?: "Connect a public GitHub repository in Settings, then check for the latest release.", color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
            if (check?.release != null) {
                val release = check.release
                Divider(color = Line)
                LabelValue("Latest version", release.tag)
                LabelValue("Published", UpdateRepository.formatPublishedAt(release.publishedAt))
                LabelValue("APK asset", release.apkName ?: "No APK attached")
                LabelValue("Download size", UpdateRepository.formatBytes(release.apkSize))
                Text("RELEASE NOTES", color = Accent2, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Text(release.body.ifBlank { "No release notes were added." }, color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
                if (check.status == "available") {
                    Button(onClick = { onDownload(release) }, enabled = release.apkUrl != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("DOWNLOAD UPDATE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Last checked · ${check?.checkedAt ?: "—"}", color = TextMuted, fontSize = 8.sp)
                TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "CHECKING…" else "CHECK NOW", color = Accent2, fontSize = 9.sp) }
            }
        }
        SettingsGroup("PROJECT") {
            LabelValue("Package", "app.veltrix.island")
            LabelValue("Minimum Android", "Android 8.0 (API 26)")
            LabelValue("Target SDK", "35")
            LabelValue("Update source", if (repository.isBlank()) "Not configured" else repository)
        }
        Text("Automatic release checks are available when this page opens. Start downloads from a release card; Android still asks you to confirm APK installation.", color = TextMuted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun PrayerScreen(
    store: SettingsStore,
    scheduleEnabled: Boolean,
    schedule: PrayerSchedule?,
    loading: Boolean,
    error: String,
    latitude: String,
    longitude: String,
    onLatitude: (String) -> Unit,
    onLongitude: (String) -> Unit,
    onBack: () -> Unit,
    onUseLocation: () -> Unit,
    onFetch: (Double, Double) -> Unit,
    onChooseAudio: () -> Unit,
    audioSelected: Boolean,
    onExactAlarmSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Accent2, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp))
            Column {
                Text("Prayer Schedule", color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("KEMENAG calculation method · 20", color = TextMuted, fontSize = 9.sp)
            }
        }
        Text("This is an optional module. Enable Adzan Reminder in Settings or Modules before scheduling alerts.", color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        SettingsGroup("LOCATION") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = latitude, onValueChange = onLatitude, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Latitude") }, textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 11.sp))
                OutlinedTextField(value = longitude, onValueChange = onLongitude, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Longitude") }, textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 11.sp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUseLocation, colors = ButtonDefaults.buttonColors(containerColor = Panel2)) { Text("USE DEVICE LOCATION", color = TextMain, fontSize = 8.sp) }
                Button(onClick = { val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull(); if (lat != null && lon != null) onFetch(lat, lon) }, enabled = !loading && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text(if (loading) "LOADING…" else "REFRESH", color = Color.White, fontSize = 8.sp)
                }
            }
            if (error.isNotBlank()) Text(error, color = Bad, fontSize = 9.sp, lineHeight = 14.sp)
        }
        if (schedule != null && scheduleEnabled) {
            SettingsGroup("TODAY'S PRAYER TIMES") {
                schedule.asMap().forEach { (name, time) -> LabelValue(name, time) }
                Divider(color = Line)
                LabelValue("Hijri date", schedule.hijriDate)
                Text("Source: ${schedule.source}. Verify local adjustments with your local authority if needed.", color = TextMuted, fontSize = 8.sp, lineHeight = 13.sp)
            }
            SettingsGroup("REMINDER DELIVERY") {
                LabelValue("Exact alarm access", if (Build.VERSION.SDK_INT < 31 || activityCanScheduleExactAlarms(LocalContext.current)) "Available" else "Permission may be required")
                Text("When exact-alarm access is unavailable, Android may delay reminders while saving power.", color = TextMuted, fontSize = 9.sp, lineHeight = 14.sp)
                TextButton(onClick = onExactAlarmSettings) { Text("OPEN ALARM PERMISSION", color = Accent2, fontSize = 9.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom adhan audio", color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (audioSelected) "An audio file is selected." else "Choose an audio file from your device.", color = TextMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = onChooseAudio) { Text("SELECT", color = Accent2, fontSize = 9.sp) }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(18.dp), contentAlignment = Alignment.Center) {
                val message = when {
                    !scheduleEnabled -> "Prayer Schedule display is disabled. Enable it in Modules to show the daily timetable."
                    schedule == null -> "No prayer schedule loaded. Enter coordinates or use device location, then refresh."
                    else -> "Prayer Schedule is enabled. The timetable is loading from the saved schedule."
                }
                Text(message, color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

private fun activityCanScheduleExactAlarms(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms() else true

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(title, color = Accent2, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var localChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) { localChecked = checked }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextMuted, fontSize = 9.sp, lineHeight = 13.sp)
        }
        PremiumSwitch(localChecked) { value ->
            localChecked = value
            onChange(value)
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, action: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = TextMuted, fontSize = 9.sp)
        TextButton(onClick = onClick) { Text(action, color = Accent2, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = TextMuted, fontSize = 9.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, color = TextMain, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun PremiumSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val prefs = remember(context) { SettingsStore(context) }
    val progress by animateFloatAsState(if (checked) 1f else 0f, animationSpec = tween(durationMillis = 220), label = "switch")
    val background = lerp(Color(0xFF2A2E39), Color(0xFF923C50), progress)
    Box(
        Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape).background(background)
            .clickable {
                if (prefs.getBoolean("vibration_enabled", true)) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                if (prefs.getBoolean("sfx_enabled", true)) {
                    val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 28)
                    tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 65)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 100L)
                }
                onCheckedChange(!checked)
            }.padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.offset(x = 20.dp * progress).size(22.dp).shadow(3.dp, CircleShape)
                .clip(CircleShape).background(if (checked) Color(0xFFFFF7F7) else Color(0xFF9EA2AC))
        )
    }
}
