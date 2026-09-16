package com.arcadia.shell.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.arcadia.shell.MainActivity
import com.arcadia.shell.R
import com.arcadia.shell.launcher.music.MusicPlaybackSession
import com.arcadia.shell.launcher.music.NowPlayingController
import com.arcadia.shell.launcher.music.NowPlayingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Foreground media session for on-device Now Playing.
 *
 * Keeps [NowPlayingController]'s MediaPlayer alive while the shell is backgrounded — XOrA
 * Emulator, Home, lock screen, or another app — and exposes play / pause / skip to the system.
 */
@AndroidEntryPoint
class MusicPlaybackService : Service() {

    @Inject lateinit var nowPlaying: NowPlayingController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        session = MediaSessionCompat(this, SESSION_TAG).apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        if (!nowPlaying.state.value.isPlaying) nowPlaying.togglePlayPause()
                    }

                    override fun onPause() {
                        if (nowPlaying.state.value.isPlaying) nowPlaying.togglePlayPause()
                    }

                    override fun onSkipToNext() {
                        nowPlaying.skipNext()
                    }

                    override fun onSkipToPrevious() {
                        nowPlaying.skipPrevious()
                    }

                    override fun onStop() {
                        if (nowPlaying.state.value.isPlaying) nowPlaying.togglePlayPause()
                    }
                },
            )
            isActive = true
        }
        promote(nowPlaying.state.value)
        nowPlaying.state
            .onEach { state ->
                if (!MusicPlaybackSession.shouldHoldService(state)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@onEach
                }
                publish(state)
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)
        when (intent?.action) {
            ACTION_PLAY -> if (!nowPlaying.state.value.isPlaying) nowPlaying.togglePlayPause()
            ACTION_PAUSE -> if (nowPlaying.state.value.isPlaying) nowPlaying.togglePlayPause()
            ACTION_NEXT -> nowPlaying.skipNext()
            ACTION_PREV -> nowPlaying.skipPrevious()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun publish(state: NowPlayingState) {
        val track = state.track ?: return
        val playing = state.isPlaying
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_STOP
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.albumTitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                .build(),
        )
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (playing) {
                        PlaybackStateCompat.STATE_PLAYING
                    } else {
                        PlaybackStateCompat.STATE_PAUSED
                    },
                    state.positionMs,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
        promote(state)
    }

    private fun promote(state: NowPlayingState) {
        runCatching {
            val notification = buildNotification(state)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "Could not promote music session to foreground", it) }
    }

    private fun buildNotification(state: NowPlayingState): Notification {
        val track = state.track
        val title = track?.title ?: getString(R.string.app_name)
        val text = track?.artist ?: "Now Playing"
        val sessionToken = session?.sessionToken
        val playPause = if (state.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                mediaIntent(ACTION_PAUSE),
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                mediaIntent(ACTION_PLAY),
            )
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openShellIntent())
            .setOngoing(state.isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    mediaIntent(ACTION_PREV),
                ),
            )
            .addAction(playPause)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    mediaIntent(ACTION_NEXT),
                ),
            )
        if (sessionToken != null) {
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }
        return builder.build()
    }

    private fun mediaIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(action)
        val request = when (action) {
            ACTION_PLAY -> 1
            ACTION_PAUSE -> 2
            ACTION_NEXT -> 3
            ACTION_PREV -> 4
            else -> 0
        }
        return PendingIntent.getService(
            this,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openShellIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Now Playing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "MusicPlayback"
        private const val SESSION_TAG = "xora.now_playing"
        private const val CHANNEL_ID = "sora_now_playing"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_PLAY = "com.arcadia.shell.music.PLAY"
        private const val ACTION_PAUSE = "com.arcadia.shell.music.PAUSE"
        private const val ACTION_NEXT = "com.arcadia.shell.music.NEXT"
        private const val ACTION_PREV = "com.arcadia.shell.music.PREV"

        fun setSessionActive(context: Context, active: Boolean) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            runCatching {
                if (active) {
                    context.startForegroundService(intent)
                } else {
                    context.stopService(intent)
                }
            }.onFailure { Log.w(TAG, "Music session service transition failed", it) }
        }
    }
}
