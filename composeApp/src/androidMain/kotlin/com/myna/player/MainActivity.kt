package com.myna.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.myna.player.ui.MynaApp
import com.myna.player.ui.Nav

class MainActivity : ComponentActivity() {
    private val nav = Nav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("np", false) == true) nav.nowPlaying = true
        val app = application as MynaApplication

        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.library.rescan() }
        if (checkSelfPermission(audio) == PackageManager.PERMISSION_GRANTED) {
            if (app.library.tracks.value.isEmpty()) app.library.rescan()
        } else {
            val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(audio, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(audio)
            ask.launch(perms)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!nav.back()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            // Status and navigation bar icons follow MYNA's own light / dark choice, not only the system's.
            val dark = when (app.library.prefs.theme.v) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MynaApp(app.platform, app.engine, app.library, nav)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("np", false)) nav.nowPlaying = true
    }
}
