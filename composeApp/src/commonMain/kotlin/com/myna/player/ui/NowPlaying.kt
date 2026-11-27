package com.myna.player.ui

import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myna.player.Lyrics
import com.myna.player.Repeat
import com.myna.player.Track
import com.myna.player.fmtTime
import com.myna.player.fmtTotal

/** The like colour from the mockups. */
val LikeRed = Color(0xFFFF4D6D)
private val Ink = Color(0xFF111111)

/**
 * Now Playing, as in the mockup: cover, title + like, seek bar with the crossfade zone, controls,
 * a big Pause tile and Lyrics / Queue / Sleep / Output tiles. [Nav.npPage] picks Lyrics, Queue or the Sleep sheet.
 */
@Composable
fun NowPlaying(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val t = st.current ?: return
    // Tint by cover: the page takes on a little of the cover's colour.
    val cache = LocalArt.current
    val tint by produceState<Color?>(null, t.id, app.library.prefs.tint.v) {
        value = if (app.library.prefs.tint.on) cache?.get(t)?.let(::averageColor) else null
    }
    val bg by animateColorAsState(tint?.let { lerp(p.tray, it, 0.15f) } ?: p.tray)
    Box(Modifier.fillMaxSize().background(bg).pointerInput(Unit) { detectTapGestures { } }.windowInsetsPadding(WindowInsets.systemBars)) {
        when (app.nav.npPage) {
            1 -> LyricsView(app, t)
            2 -> QueueView(app)
            else -> PlayerView(app, t)
        }
        if (app.nav.npPage == 3) SleepSheet(app)
        if (app.nav.npPage == 4) OutputSheet(app)
    }
}

@Composable
private fun NpHeader(app: AppCtx, left: ImageVector, onLeft: () -> Unit, start: Boolean = false, center: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        HdrBtn(left, onLeft)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally, content = center)
        MoreMenu(app)
    }
}

/** A header button as in the mockup: a small white tile. */
@Composable
internal fun HdrBtn(v: ImageVector, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.tile).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Ico(v, 22.dp, p.ink)
    }
}

/** The ⋯ button: go to the album or artist, or open Sound settings. */
@Composable
private fun MoreMenu(app: AppCtx) {
    val st by app.engine.state.collectAsState()
    val t = st.current ?: return
    var open by remember { mutableStateOf(false) }
    Box {
        HdrBtn(Ic.more) { open = true }
        DropdownMenu(open, { open = false }) {
            fun go(p: Page) { open = false; app.nav.npPage = 0; app.nav.push(p) }
            if (t.album.isNotBlank()) MenuItem("Go to album") { go(Page.AlbumP(t.albumKey)) }
            MenuItem("Go to artist") { go(Page.ArtistP(t.artist)) }
            MenuItem("Sound") { go(Page.Set(6)) }
        }
    }
}

@Composable
private fun PlayerView(app: AppCtx, t: Track) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        NpHeader(app, Ic.down, { app.nav.nowPlaying = false }) {
            Txt("PLAYING FROM", 11, FontWeight.Bold, p.sub)
            Txt(st.from.ifBlank { t.album.ifBlank { t.artist } }, 14, FontWeight.ExtraBold)
        }
        if (app.wide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    LivingCover(app, Modifier.widthIn(max = 460.dp).fillMaxWidth().aspectRatio(1f))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { NpTiles(app, t) }
            }
        } else {
            // The cover takes whatever height the tiles leave, so the whole player fits without scrolling.
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                // Square, so the whole cover always shows (no cropping when space is short).
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LivingCover(app, Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true))
                }
                Spacer(Modifier.height(8.dp))
                NpTiles(app, t)
            }
        }
    }
}

@Composable
private fun NpTiles(app: AppCtx, t: Track) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Modifier.weight(3f).fillMaxHeight(), pad = 10.dp) {
                Txt(t.title, 20, FontWeight.ExtraBold, lines = 1)
                Txt(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), 13, color = p.sub)
            }
            val liked = app.library.likes.has(t)
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(LikeRed)
                    .clickable { app.library.likes.toggle(t) },
                contentAlignment = Alignment.Center,
            ) { Ico(if (liked) Ic.heartOn else Ic.heart, 28.dp, Color.White) }
        }
        SeekTile(app)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CtlTile(Ic.shuffle, st.shuffle) { app.engine.toggleShuffle() }
            CtlTile(Ic.prev, false) { app.engine.prev() }
            CtlTile(Ic.next, false) { app.engine.next() }
            CtlTile(Ic.repeat, st.repeat != Repeat.Off, if (st.repeat == Repeat.One) "1" else null) { app.engine.cycleRepeat() }
        }
        PauseTile(app)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTile(Ic.lyr, "Lyrics") { app.nav.npPage = 1 }
            SmallTile(Ic.queue, "Queue") { app.nav.npPage = 2 }
            SmallTile(Ic.moon, st.sleepLeftMs?.let { fmtTime(it) } ?: "Sleep", st.sleepLeftMs != null || st.sleepEndOfSong || st.sleepEndOfAlbum) { app.nav.npPage = 3 }
            SmallTile(Ic.cast, "Output") { app.nav.npPage = 4 }
        }
    }
}

@Composable
private fun RowScope.CtlTile(v: ImageVector, on: Boolean, badge: String? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(if (on) p.soft else p.tile).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ico(v, 22.dp, p.ink)
        if (badge != null) Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).clip(CircleShape).background(p.block),
            contentAlignment = Alignment.Center,
        ) { Txt(badge, 10, FontWeight.Bold, p.onBlock) }
    }
}

@Composable
private fun RowScope.SmallTile(v: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Lime else p.tile).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Ico(v, 18.dp, if (on) OnLime else p.ink)
        Txt(label, 11, FontWeight.Bold, if (on) OnLime else p.ink)
    }
}

/** The big Pause / Play tile: black with a lime label (lime with black in dark mode). */
@Composable
fun PauseTile(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val bg = if (p.dark) Lime else Ink
    val fg = if (p.dark) OnLime else Lime
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable { app.engine.toggle() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(if (st.playing) Ic.pause else Ic.play, 26.dp, fg)
        Spacer(Modifier.width(8.dp))
        Txt(if (st.playing) "Pause" else "Play", 16, FontWeight.ExtraBold, fg)
    }
}

/** Seek bar in a tile. Its lime end is where the crossfade into the next song happens. */
@Composable
fun SeekTile(app: AppCtx, label: String? = null) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = st.durationMs.coerceAtLeast(1)
    val v = drag ?: (st.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val next = st.queue.getOrNull(st.index + 1)
    val fades = st.crossfadeSec > 0 && next != null
    val link = if (p.dark) Lime else Color(0xFF5B7F00)
    Tile(Modifier.fillMaxWidth(), pad = 8.dp) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(20.dp)
                .pointerInput(dur) { detectTapGestures { o -> app.engine.seek((o.x / size.width * dur).toLong()) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragEnd = { drag?.let { app.engine.seek((it * dur).toLong()) }; drag = null },
                        onDragCancel = { drag = null },
                    ) { ch, _ -> drag = (ch.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(p.line)) {
                if (fades) Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth((st.crossfadeSec * 1000f / dur).coerceIn(0.1f, 0.5f)).fillMaxHeight().background(Lime))
                Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(p.ink))
            }
            Box(Modifier.offset(x = (w * v - 9.dp).coerceIn(0.dp, w - 18.dp)).size(18.dp).clip(CircleShape).background(p.ink))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(fmtTime((v * dur).toLong()), 11, FontWeight.Bold, p.sub)
            Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                val mid = label ?: when {
                    st.fading -> "Crossfading…"
                    fades -> "${st.crossfadeSec} s fade → ${next!!.title}"
                    else -> ""
                }
                Txt(mid, 11, FontWeight.Bold, if (label != null) p.sub else link)
            }
            Txt(fmtTime(st.durationMs), 11, FontWeight.Bold, p.sub)
        }
    }
}

/** "Living cover": a slow zoom while the song plays. */
@Composable
private fun LivingCover(app: AppCtx, modifier: Modifier) {
    val st by app.engine.state.collectAsState()
    val inf = rememberInfiniteTransition()
    val zoom by inf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val mode = app.library.prefs.animCovers.v
    val moving = st.playing && (mode == 0 || mode == 1 && remember(st.current?.id) { app.platform.onWifi() })
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        Cover(st.current, Modifier.fillMaxSize().graphicsLayer { val z = if (moving) zoom else 1f; scaleX = z; scaleY = z }, 0.dp)
    }
}

// ---------------------------------------------------------------- Lyrics

@Composable
private fun LyricsView(app: AppCtx, t: Track) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val lyr by produceState<Lyrics?>(null, t.id) { value = null; value = Lyrics.forTrack(app.platform, t, app.library.prefs.lyricsDl.on) }
    val cur = lyr?.at(st.positionMs) ?: -1
    val list = rememberLazyListState()
    LaunchedEffect(cur) { if (cur >= 0) list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    val bg = if (p.dark) p.tile else Ink
    Column(Modifier.fillMaxSize()) {
        NpHeader(app, Ic.down, { app.nav.npPage = 0 }, start = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(t, Modifier.size(34.dp), 8.dp)
                Spacer(Modifier.width(8.dp))
                Column { Txt(t.title, 14, FontWeight.Bold); Txt(t.artist, 12, color = p.sub) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(20.dp)).background(bg)) {
            val l = lyr
            when {
                l == null -> Txt("Finding lyrics…", 18, FontWeight.ExtraBold, Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
                l.lines.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("No lyrics for this song", 18, FontWeight.ExtraBold, Color.White)
                    Txt(if (app.library.prefs.lyricsDl.on) "None were found online." else "Lyrics download is off in Settings.", 13, color = Color.White.copy(alpha = 0.6f))
                }
                else -> LazyColumn(state = list, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(l.lines) { i, (ms, line) ->
                        val now = i == cur
                        Txt(
                            line, if (now) 25 else 19, FontWeight.ExtraBold,
                            when { now -> Lime; !l.synced -> Color.White.copy(alpha = 0.85f); i < cur -> Color.White.copy(alpha = 0.22f); else -> Color.White.copy(alpha = 0.38f) },
                            lines = 4, modifier = Modifier.clickable(enabled = l.synced) { app.engine.seek(ms) },
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SeekTile(app, label = when { lyr?.synced == true -> "Synced lyrics"; lyr?.lines?.isNotEmpty() == true -> "Lyrics"; else -> "" })
            PauseTile(app)
        }
    }
}

// ---------------------------------------------------------------- Queue

@Composable
private fun QueueView(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val rest = st.queue.drop(st.index + 1)
    Column(Modifier.fillMaxSize()) {
        NpHeader(app, Ic.down, { app.nav.npPage = 0 }) {
            Txt("Queue", 16, FontWeight.ExtraBold)
            Txt("${st.queue.size - st.index} songs · ${fmtTotal(st.queue.drop(st.index).sumOf { it.durationMs })}", 12, color = p.sub)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionHead("Now playing") }
            st.current?.let { c -> item { SongRow(c, true) { app.nav.npPage = 0 } } }
            if (st.crossfadeSec > 0 && rest.isNotEmpty()) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)).background(p.soft).padding(8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Ic.fade, 16.dp, p.ink)
                    Spacer(Modifier.width(6.dp))
                    Txt("Next one fades in over ${st.crossfadeSec} s", 12, FontWeight.ExtraBold)
                }
            }
            item { SectionHead("Up next") }
            if (rest.isEmpty()) item { Txt("End of the queue", 14, color = p.sub, modifier = Modifier.padding(8.dp)) }
            itemsIndexed(rest.take(200)) { i, tr -> SongRow(tr, false) { app.engine.jump(st.index + 1 + i) } }
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Ic.shuffle, if (st.shuffle) "Shuffle on" else "Shuffle", st.shuffle) { app.engine.toggleShuffle() }
            ActionTile(Ic.heart, "Like all", false) { rest.forEach { if (!app.library.likes.has(it)) app.library.likes.toggle(it) } }
        }
    }
}

// ---------------------------------------------------------------- Sleep sheet

@Composable
private fun SleepSheet(app: AppCtx) {
    val p = LocalPal.current
    var pick by remember { mutableStateOf(30) }
    val opts = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min", -1 to "End of song", -2 to "End of album")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { app.nav.npPage = 0 }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(p.tray)
                .clickable(enabled = false) {}.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(CircleShape).background(p.line))
            Txt("Sleep timer", 20, FontWeight.ExtraBold)
            opts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (m, name) ->
                        val on = m == pick
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Lime else p.tile).clickable { pick = m },
                            contentAlignment = Alignment.Center,
                        ) { Txt(name, 15, FontWeight.ExtraBold, if (on) OnLime else p.ink) }
                    }
                }
            }
            Tile(Modifier.fillMaxWidth(), pad = 12.dp) {
                Row { Txt("Fade out over", 14, FontWeight.Bold, modifier = Modifier.weight(1f)); Txt("30 s", 14, FontWeight.ExtraBold) }
            }
            val stops = when (pick) {
                -1 -> "after this song"
                -2 -> "after this album"
                else -> "at ${app.platform.clock(app.platform.nowMs() + pick * 60_000L)}"
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Lime)
                    .clickable { app.engine.sleep(pick); app.nav.npPage = 0 },
                contentAlignment = Alignment.Center,
            ) { Txt("Start · stops $stops", 16, FontWeight.ExtraBold, OnLime) }
            val st by app.engine.state.collectAsState()
            if (st.sleepLeftMs != null || st.sleepEndOfSong || st.sleepEndOfAlbum) Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(18.dp)).background(p.tile)
                    .clickable { app.engine.sleep(0); app.nav.npPage = 0 },
                contentAlignment = Alignment.Center,
            ) { Txt("Turn timer off", 15, FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------- Play on sheet

@Composable
private fun OutputSheet(app: AppCtx) {
    val p = LocalPal.current
    val pf = app.platform
    var tick by remember { mutableStateOf(0) }
    // Headphones can come and go while the sheet is open.
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); tick++ } }
    val devices = remember(tick) { pf.outputs() }
    var cur by remember { mutableStateOf(pf.currentOutput()) }
    LaunchedEffect(tick) { cur = pf.currentOutput() }
    var vol by remember { mutableStateOf(pf.volume()) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { app.nav.npPage = 0 }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(p.tray)
                .clickable(enabled = false) {}.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(CircleShape).background(p.line))
            Txt("Play on", 20, FontWeight.ExtraBold)
            devices.forEach { d ->
                val on = d.id == cur
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) p.block else p.tile)
                        .clickable { pf.setOutput(d.id); cur = d.id }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Lime else p.tray), contentAlignment = Alignment.Center) {
                        Ico(when (d.kind) { 0 -> Ic.phone; 1 -> Ic.bt; else -> Ic.headphones }, 20.dp, if (on) OnLime else p.ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(d.name, 15, FontWeight.ExtraBold, if (on) p.onBlock else p.ink)
                        val kind = when (d.kind) { 0 -> "Speaker"; 1 -> "Bluetooth"; 2 -> "Wired"; else -> "USB" }
                        Txt(if (on) "$kind · playing" else kind, 13, FontWeight.SemiBold, if (on) p.onBlock.copy(alpha = 0.75f) else p.sub)
                    }
                    if (on) Ico(Ic.check, 22.dp, Lime)
                    Spacer(Modifier.width(6.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile).clickable { pf.openOutput() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.cast, 20.dp, p.ink)
                Spacer(Modifier.width(12.dp))
                Txt("More devices · Cast, pair new", 15, FontWeight.Bold, modifier = Modifier.weight(1f))
                Ico(Ic.chev, 18.dp, p.sub)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile).padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.vol, 20.dp, p.ink)
                MynaSlider(vol, { vol = it; pf.setVolume(it) }, Modifier.weight(1f).padding(horizontal = 10.dp))
                Txt("${(vol * 100).roundToInt()}", 14, FontWeight.ExtraBold, modifier = Modifier.width(30.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- shared

/** The average colour of a cover, from a sparse grid of its pixels. */
private fun averageColor(img: ImageBitmap): Color? = runCatching {
    val px = IntArray(img.width * img.height)
    img.readPixels(px)
    var r = 0L; var g = 0L; var b = 0L; var n = 0
    for (i in px.indices step 97) { val c = px[i]; r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; n++ }
    Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}.getOrNull()

/** The like button: a filled heart once the song is liked. */
@Composable
fun LikeBtn(app: AppCtx, t: Track, tint: Color = LocalPal.current.ink) {
    val on = app.library.likes.has(t)
    Box(Modifier.size(44.dp).clip(CircleShape).clickable { app.library.likes.toggle(t) }, contentAlignment = Alignment.Center) {
        Ico(if (on) Ic.heartOn else Ic.heart, 24.dp, if (on) LikeRed else tint)
    }
}

@Composable
fun SeekBar(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = st.durationMs.coerceAtLeast(1)
    val v = drag ?: (st.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt(fmtTime((v * dur).toLong()), 12, color = p.sub)
        Slider(
            value = v, onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { app.engine.seek((it * dur).toLong()) }; drag = null },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp), enabled = st.current != null,
            colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = Lime, inactiveTrackColor = p.line),
        )
        Txt(fmtTime(st.durationMs), 12, color = p.sub)
    }
}

@Composable
fun Transport(app: AppCtx, small: Boolean) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val big = if (small) 44.dp else 72.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 14.dp)) {
        Toggle(Ic.shuffle, st.shuffle) { app.engine.toggleShuffle() }
        IconBtn(Ic.prev) { app.engine.prev() }
        RoundBtn(if (st.playing) Ic.pause else Ic.play, big, Lime, OnLime) { app.engine.toggle() }
        IconBtn(Ic.next) { app.engine.next() }
        Box {
            Toggle(Ic.repeat, st.repeat != Repeat.Off) { app.engine.cycleRepeat() }
            if (st.repeat == Repeat.One) {
                Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(p.block), contentAlignment = Alignment.Center) {
                    Txt("1", 10, FontWeight.Bold, p.onBlock)
                }
            }
        }
    }
}

@Composable
private fun Toggle(v: ImageVector, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (on) p.soft else p.tray).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(v, 20.dp, if (on) p.ink else p.sub) }
}

/** Shows the crossfade length, and when a fade is running. Tap opens Settings › Playback. */
@Composable
fun FadeChip(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (st.fading) Lime else p.soft)
            .clickable { app.nav.push(Page.Set(0)) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Ic.fade, 16.dp, if (st.fading) OnLime else p.ink)
        Spacer(Modifier.size(6.dp))
        Txt(
            when {
                st.fading -> "Crossfading…"
                st.crossfadeSec == 0 -> "Crossfade off"
                else -> "Crossfade ${st.crossfadeSec} s"
            },
            13, FontWeight.Bold, if (st.fading) OnLime else p.ink,
        )
    }
}
