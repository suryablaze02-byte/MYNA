package com.myna.player

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The phone's media controls: the notification player, the lock screen player,
 * headset and Bluetooth buttons. All of them follow [Engine.state].
 */
class MediaControls(
    private val ctx: Context, private val engine: Engine, private val platform: Platform, private val scope: CoroutineScope,
) {
    private val session = MediaSession(ctx, "MYNA")
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var shown: PlayerState? = null
    private var art: Bitmap? = null
    private var statePos = 0L
    private var stateAt = 0L
    private var stopLater: Job? = null

    /** Opens the full-size player; over the lock screen when the phone is locked. */
    private val openPlayer = PendingIntent.getActivity(
        ctx, 1, Intent(ctx, LockPlayerActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    init {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW))
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = command(PLAY)
            override fun onPause() = command(PAUSE)
            override fun onStop() = command(PAUSE)
            override fun onSkipToNext() = command(NEXT)
            override fun onSkipToPrevious() = command(PREV)
            override fun onSeekTo(pos: Long) = engine.seek(pos)
        })
        session.setSessionActivity(openPlayer)
        scope.launch { engine.state.collect { show(it) } }
    }

    fun command(a: String) {
        val playing = engine.state.value.playing
        when (a) {
            PLAY -> if (!playing) engine.toggle()
            PAUSE -> if (playing) engine.toggle()
            TOGGLE -> engine.toggle()
            NEXT -> engine.next()
            PREV -> engine.prev()
        }
    }

    private fun show(s: PlayerState) {
        val old = shown
        shown = s
        val t = s.current
        if (t == null) {
            if (old?.current != null) { session.isActive = false; stopService() }
            return
        }
        val newTrack = old?.current?.id != t.id
        if (newTrack) {
            art = null
            scope.launch {
                val b = runCatching { platform.art(t)?.asAndroidBitmap() }.getOrNull()
                if (shown?.current?.id == t.id && b != null) { art = b; meta(); post() }
            }
        }
        if (newTrack || old?.durationMs != s.durationMs) meta()
        // Only tell Android about jumps (play, pause, seek); it moves the bar along by itself.
        val expected = statePos + if (old?.playing == true) SystemClock.elapsedRealtime() - stateAt else 0
        if (newTrack || old?.playing != s.playing || abs(s.positionMs - expected) > 1500) {
            statePos = s.positionMs
            stateAt = SystemClock.elapsedRealtime()
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO,
                    )
                    .setState(if (s.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, s.positionMs, if (s.playing) 1f else 0f)
                    .build(),
            )
        }
        if (!newTrack && old?.playing == s.playing) return
        session.isActive = true
        if (s.playing) {
            stopLater?.cancel()
            // Starting again re-shows the notification as the foreground one.
            runCatching { ctx.startForegroundService(Intent(ctx, PlaybackService::class.java)) }.onFailure { post() }
        } else {
            post()
            // A paused player stays on the lock screen for a while, then goes away.
            stopLater?.cancel()
            stopLater = scope.launch { delay(30 * 60_000L); stopService() }
        }
    }

    private fun stopService() { ctx.stopService(Intent(ctx, PlaybackService::class.java)); nm.cancel(ID) }

    private fun meta() {
        val s = shown ?: return
        val t = s.current ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs.takeIf { it > 0 } ?: t.durationMs)
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
    }

    private fun post() { if (shown?.current != null) nm.notify(ID, notification()) }

    fun notification(): Notification {
        val s = shown
        val t = s?.current
        val playing = s?.playing == true
        fun act(icon: Int, name: String, a: String) = Notification.Action.Builder(
            Icon.createWithResource(ctx, icon), name,
            PendingIntent.getService(ctx, a.hashCode(), Intent(ctx, PlaybackService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_myna)
            .setContentTitle(t?.title ?: "MYNA")
            .setContentText(t?.artist.orEmpty())
            .setLargeIcon(art)
            .setContentIntent(openPlayer)
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(act(R.drawable.ic_prev, "Previous", PREV))
            .addAction(if (playing) act(R.drawable.ic_pause, "Pause", TOGGLE) else act(R.drawable.ic_play, "Play", TOGGLE))
            .addAction(act(R.drawable.ic_next, "Next", NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    companion object {
        const val ID = 1
        private const val CHANNEL = "playback"
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val TOGGLE = "toggle"
        const val NEXT = "next"
        const val PREV = "prev"

        fun locked(ctx: Context) = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }
}
