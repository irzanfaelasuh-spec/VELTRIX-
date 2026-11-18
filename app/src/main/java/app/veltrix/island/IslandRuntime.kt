package app.veltrix.island

import android.media.session.MediaController
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandUiState(
    val mediaTitle: String = "Nothing playing",
    val mediaArtist: String = "Start music on your device",
    val isPlaying: Boolean = false,
    val mediaPositionMs: Long = 0L,
    val mediaSpeed: Float = 0f,
    val mediaPositionUpdatedAt: Long = 0L,
    val notificationTitle: String = "",
    val notificationText: String = "",
    val notificationAt: Long = 0L,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val adhanTitle: String = "",
    val adhanTime: String = "",
    val adhanIsPreAlert: Boolean = false,
    val adhanAt: Long = 0L
)

object IslandRuntime {
    private val mutable = MutableStateFlow(IslandUiState())
    val state = mutable.asStateFlow()

    @Volatile var activeController: MediaController? = null

    fun updateMedia(title: String?, artist: String?, playing: Boolean, positionMs: Long, speed: Float, controller: MediaController?) {
        activeController = controller
        mutable.value = mutable.value.copy(
            mediaTitle = if (controller == null) "Nothing playing" else title?.takeIf { it.isNotBlank() } ?: "Unknown track",
            mediaArtist = if (controller == null) "Start music on your device" else artist?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            isPlaying = playing,
            mediaPositionMs = positionMs.coerceAtLeast(0L),
            mediaSpeed = speed,
            mediaPositionUpdatedAt = SystemClock.elapsedRealtime()
        )
    }

    fun updateNotification(title: String, text: String) {
        mutable.value = mutable.value.copy(
            notificationTitle = title.take(70),
            notificationText = text.take(110),
            notificationAt = SystemClock.elapsedRealtime()
        )
    }

    fun updateBattery(percent: Int, isCharging: Boolean) {
        mutable.value = mutable.value.copy(batteryPercent = percent, charging = isCharging)
    }

    fun updateAdhan(prayerName: String, time: String, preAlert: Boolean) {
        mutable.value = mutable.value.copy(adhanTitle = prayerName, adhanTime = time, adhanIsPreAlert = preAlert, adhanAt = SystemClock.elapsedRealtime())
    }

    fun clearAdhan() { mutable.value = mutable.value.copy(adhanTitle = "", adhanTime = "", adhanIsPreAlert = false, adhanAt = 0L) }

    fun currentPosition(): Long {
        val s = mutable.value
        if (!s.isPlaying || s.mediaSpeed <= 0f || s.mediaPositionUpdatedAt == 0L) return s.mediaPositionMs
        val delta = SystemClock.elapsedRealtime() - s.mediaPositionUpdatedAt
        return (s.mediaPositionMs + delta * s.mediaSpeed).coerceAtLeast(0L)
    }

    fun playPause() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == android.media.session.PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
    }

    fun skipNext() { activeController?.transportControls?.skipToNext() }
    fun skipPrevious() { activeController?.transportControls?.skipToPrevious() }
}
