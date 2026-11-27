package com.myna.player

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Keeps the app alive while music plays and runs the notification player's buttons. */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controls = (application as MynaApplication).controls
        val a = intent?.action
        if (a != null) { controls.command(a); return START_NOT_STICKY }
        val n = controls.notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(MediaControls.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(MediaControls.ID, n)
        return START_NOT_STICKY
    }
}
