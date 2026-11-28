package com.myna.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myna.player.ui.Cover
import com.myna.player.ui.Ic
import com.myna.player.ui.Ico
import com.myna.player.ui.LocalArt
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tapping the lock screen's media player opens this: the album art fills the lock screen,
 * with the clock and the controls on top. Tapping the art minimises it back. The phone stays locked.
 */
class LockPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones stay upright; tablets (600 dp and wider) can turn.
        if (resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (!MediaControls.locked(this)) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("np", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        enableEdgeToEdge()
        // Full screen like a lock screen: no back / home buttons and no second clock; a swipe shows them for a moment.
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val app = application as MynaApplication
        setContent {
            CompositionLocalProvider(LocalArt provides app.library.art) { LockArt(app.engine) { minimise() } }
        }
    }

    private fun minimise() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

@Composable
private fun LockArt(engine: Engine, minimise: () -> Unit) {
    val st by engine.state.collectAsState()
    val t = st.current
    LaunchedEffect(t == null) { if (t == null) minimise() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val none = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable(none, null, onClick = minimise)) {
        Cover(t, Modifier.fillMaxSize(), radius = 0.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.45f), 0.3f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f),
        )))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 28.dp)) {
            Spacer(Modifier.height(36.dp))
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), Modifier.align(Alignment.CenterHorizontally),
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "H:mm" else "h:mm", Locale.getDefault()).format(now), Modifier.align(Alignment.CenterHorizontally),
                color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (t != null) {
                // The controls take their own taps, so only the art minimises.
                Column(Modifier.clickable(none, null) {}) {
                    Text(t.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    val dur = st.durationMs.takeIf { it > 0 } ?: t.durationMs
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(if (dur > 0) (st.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(Color.White))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(time(st.positionMs), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                        Text("-" + time((dur - st.positionMs).coerceAtLeast(0)), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Btn(Ic.prev, 36) { engine.prev() }
                        Btn(if (st.playing) Ic.pause else Ic.play, 52) { engine.toggle() }
                        Btn(Ic.next, 36) { engine.next() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Btn(icon: ImageVector, size: Int, onClick: () -> Unit) =
    Box(Modifier.size((size + 28).dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Ico(icon, size.dp, Color.White)
    }

private fun time(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
