package app.veltrix.island.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import app.veltrix.island.MainActivity

class AdhanPlaybackService : Service() {
    companion object {
        const val ACTION_PLAY = "app.veltrix.island.action.PLAY_ADHAN"
        const val ACTION_STOP = "app.veltrix.island.action.STOP_ADHAN"
        const val EXTRA_URI = "audio_uri"
        private const val CHANNEL = "veltrix_adhan_audio"
        private const val NOTIFICATION_ID = 6350
    }

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Adzan audio", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PLAY) {
            val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
            if (uri == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            startAsForeground()
            runCatching {
                player?.release()
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    setDataSource(this@AdhanPlaybackService, uri)
                    setOnCompletionListener { stopPlayback() }
                    setOnErrorListener { _, _, _ -> stopPlayback(); true }
                    setOnPreparedListener { mediaPlayer -> mediaPlayer.start() }
                    prepareAsync()
                }
            }.onFailure { stopPlayback() }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val accept = PendingIntent.getBroadcast(
            this, 6351,
            Intent(this, PrayerActionReceiver::class.java).setAction(PrayerActionReceiver.ACTION_ACCEPT).putExtra(PrayerAlarmReceiver.EXTRA_ID, 6200),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(this, 6352, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("VELTRIX · Adzan")
            .setContentText("Audio adzan sedang diputar")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "ACCEPT", accept)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopPlayback() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
