package app.veltrix.island.media

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.veltrix.island.IslandRuntime
import app.veltrix.island.SettingsStore

class MediaAccessService : NotificationListenerService() {
    private var manager: MediaSessionManager? = null
    private var active: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(active)
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish(active)
        override fun onSessionDestroyed() {
            active = null
            publish(null)
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        chooseController(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        manager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            manager?.addOnActiveSessionsChangedListener(
                sessionListener,
                ComponentName(this, MediaAccessService::class.java)
            )
            chooseController(manager?.getActiveSessions(ComponentName(this, MediaAccessService::class.java)).orEmpty())
        }
    }

    override fun onListenerDisconnected() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionListener) }
        active?.unregisterCallback(controllerCallback)
        active = null
        IslandRuntime.updateMedia(null, null, false, 0L, 0f, null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) return
        if (!SettingsStore(this).getBoolean("module_notifications", true)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isNotBlank() || text.isNotBlank()) {
            IslandRuntime.updateNotification(title.ifBlank { "Notification" }, text)
        }
    }

    private fun chooseController(controllers: List<MediaController>) {
        val selected = controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_PAUSED
        } ?: controllers.firstOrNull()

        if (active?.sessionToken != selected?.sessionToken) {
            active?.unregisterCallback(controllerCallback)
            active = selected
            active?.registerCallback(controllerCallback)
        }
        publish(active)
    }

    private fun publish(controller: MediaController?) {
        if (controller == null) {
            IslandRuntime.updateMedia(null, null, false, 0L, 0f, null)
            return
        }
        val metadata = controller.metadata
        val playback = controller.playbackState
        val state = playback?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        IslandRuntime.updateMedia(
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playing,
            playback?.position ?: 0L,
            playback?.playbackSpeed ?: 0f,
            controller
        )
    }
}
