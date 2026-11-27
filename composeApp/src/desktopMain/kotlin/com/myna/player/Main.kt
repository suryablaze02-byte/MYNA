package com.myna.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.myna.player.ui.MiniPlayer
import com.myna.player.ui.MynaApp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.WindowPosition
import com.myna.player.ui.Nav
import kotlinx.coroutines.MainScope
import org.bytedeco.ffmpeg.global.avutil
import java.util.logging.Level
import java.util.logging.Logger

fun main() {
    device = "PC"
    avutil.av_log_set_level(avutil.AV_LOG_ERROR)
    Logger.getLogger("org.jaudiotagger").level = Level.OFF
    val scope = MainScope()
    val platform = DesktopPlatform()
    val prefs = Prefs(platform)
    val engine = Engine(platform, scope, prefs)
    val library = Library(platform, scope, prefs)
    engine.onPlayed = { library.history.add(it); library.recent.push(it) }
    platform.applySound(prefs.soundFx.v, prefs.eqPreset.v, prefs.clearBass.v)
    library.rescan()
    val nav = Nav().apply { noPlayerPage = true }
    val icon = BitmapPainter(useResource("myna.png", ::loadImageBitmap))

    application {
        Window(
            onCloseRequest = ::exitApplication,
            visible = !nav.mini,
            title = "MYNA",
            icon = icon,
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
            // Esc goes back, like Android's back gesture.
            onPreviewKeyEvent = { it.type == KeyEventType.KeyDown && it.key == Key.Escape && nav.back() },
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            MynaApp(platform, engine, library, nav)
        }
        // The mini player: a borderless square of album art that stays on top. Drag it anywhere.
        if (nav.mini) Window(
            onCloseRequest = { nav.mini = false },
            title = "MYNA mini player",
            icon = icon,
            undecorated = true,
            alwaysOnTop = true,
            resizable = false,
            state = rememberWindowState(width = 300.dp, height = 300.dp, position = WindowPosition(Alignment.BottomEnd)),
            onPreviewKeyEvent = { it.type == KeyEventType.KeyDown && it.key == Key.Escape && run { nav.mini = false; true } },
        ) {
            WindowDraggableArea { MiniPlayer(platform, engine, library, nav) }
        }
    }
}
