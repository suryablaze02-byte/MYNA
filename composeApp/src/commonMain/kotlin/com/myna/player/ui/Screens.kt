package com.myna.player.ui

import com.myna.player.device
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myna.player.Album
import com.myna.player.Track
import com.myna.player.albums
import com.myna.player.artists
import com.myna.player.fmtTotal
import kotlin.math.roundToInt

internal val PAD = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
internal fun Header(title: String, small: String? = null, app: AppCtx, back: Boolean = false, gear: Boolean = false, search: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back) { HdrBtn(Ic.back) { app.nav.back() }; Spacer(Modifier.width(12.dp)) }
        else if (title == "Home") {
            // The mark on a lime tile, like the app icon; a bare thin line was hard to read.
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Lime), contentAlignment = Alignment.Center) { MynaMark(32.dp, line = OnLime, accent = OnLime) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (small != null) Txt(small, 13, FontWeight.Bold, LocalPal.current.sub)
            Txt(title, 30, FontWeight.ExtraBold)
        }
        if (search) HdrBtn(Ic.search) { app.nav.go(Page.Search) }
        if (gear && !app.wide) HdrBtn(Ic.gear) { app.nav.push(Page.Settings) }
    }
}

@Composable
internal fun EmptyLibrary(app: AppCtx) {
    val p = LocalPal.current
    val scanning by app.library.scanning.collectAsState()
    val phone = app.library.prefs.phoneSongs.on
    Tile(Modifier.fillMaxWidth(), pad = 20.dp) {
        if (!phone) {
            Txt("Songs on this $device are hidden", 20, FontWeight.ExtraBold, lines = 2)
            Spacer(Modifier.height(6.dp))
            Txt("Find songs in Discover and Search, then tap ⋯ › Add to library to keep them here.", 14, color = p.sub, lines = 3)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Ic.compass, "Discover", true) { app.nav.go(Page.Discover) }
                ActionTile(Ic.folder, "Show $device songs", false) { app.library.prefs.phoneSongs.set(1); app.library.refilter() }
            }
        } else if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = p.ink, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Txt("Finding your music…", 16, FontWeight.Bold)
            }
        } else {
            Txt("No music found yet", 20, FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Txt(
                if (app.platform.isDesktop) "MYNA looks in ${app.platform.sourceLabel()}. Choose the folder where your songs are."
                else "Allow MYNA to read your music, or copy songs to this $device, then rescan.",
                14, color = p.sub, lines = 4,
            )
            app.platform.problem?.let { Spacer(Modifier.height(6.dp)); Txt(it, 14, FontWeight.Bold, lines = 4) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (app.platform.isDesktop) ActionTile(Ic.folder, "Choose folder", true) { if (app.platform.pickFolder()) app.library.rescan() }
                ActionTile(Ic.refresh, "Rescan", !app.platform.isDesktop) { app.library.rescan() }
            }
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(app: AppCtx) {
    val p = LocalPal.current
    val tracks by app.library.tracks.collectAsState()
    val st by app.engine.state.collectAsState()
    val liked = app.library.likes.tracks
    val hist = app.library.history
    val now = app.platform.nowMs()
    // Play history only has names: match them to songs MYNA knows (library, liked, recently played).
    val byKey = remember(tracks, liked, app.library.recent.tracks) {
        app.library.known(tracks).associateBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
    }
    val recent = remember(byKey) {
        app.library.recent.tracks.ifEmpty { hist.rows.mapNotNull { byKey["${it.title.lowercase()}|${it.artist.lowercase()}"] }.distinctBy { it.id }.take(20) }
    }
    val h = app.platform.hour()
    val greet = when { h < 5 -> "Good night"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
    val weekMs = remember(recent) { hist.rows.filter { now - it.at < 7 * DAY }.sumOf { it.ms } }
    // On repeat: songs played most in the last 30 days (twice or more).
    val onRepeat = remember(byKey) {
        hist.rows.filter { now - it.at < 30 * DAY }.groupingBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }.eachCount()
            .filter { it.value >= 2 }.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(10)
    }
    val lastAlbum = recent.firstOrNull { it.album.isNotBlank() }
    val topArtist = remember(recent) { hist.topArtists(1).firstOrNull() }
    val artistCover = recent.firstOrNull { it.artist == topArtist } ?: byKey.values.firstOrNull { it.artist == topArtist }

    // On a wide window the page stays at most 1200 dp wide and sits in the middle, so tiles don't stretch.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.fillMaxHeight().widthIn(max = 1200.dp), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(GAP(app))) {
        item { Header("Home", greet, app, gear = true) }
        item { UpdateTile(app, banner = true) }
        if (tracks.isEmpty() && liked.isEmpty() && recent.isEmpty()) {
            item { EmptyLibrary(app) }
            return@LazyColumn
        }
        // Jump back in: Liked songs, or the whole library when nothing is liked yet.
        val list = liked.ifEmpty { tracks }
        val name = if (liked.isNotEmpty()) "Liked songs" else "Your library"
        val play = { if (list.isNotEmpty()) { app.engine.play(list, 0, from = name); app.nav.nowPlaying = true } }
        val hero: @Composable (Modifier) -> Unit = { m ->
            Box(m.clip(RoundedCornerShape(if (app.wide) 24.dp else 20.dp))
                .clickable { if (liked.isNotEmpty()) { app.nav.push(Page.ListP("liked")) } else play() }) {
                CoverFill(list.firstOrNull())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8000000)))))
                Column(Modifier.align(Alignment.BottomStart).padding(if (app.wide) 20.dp else 14.dp).padding(end = 64.dp)) {
                    Txt("JUMP BACK IN", 11, FontWeight.Bold, Color.White.copy(alpha = 0.85f))
                    Txt(name, if (app.wide) 30 else 22, FontWeight.ExtraBold, Color.White)
                    Txt("${list.size} song${if (list.size == 1) "" else "s"} · ${fmtTotal(list.sumOf { it.durationMs })}", 12, color = Color.White.copy(alpha = 0.85f))
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(if (app.wide) 16.dp else 12.dp)) { RoundBtn(Ic.play, 54.dp, Lime, OnLime) { play() } }
            }
        }
        val week: @Composable (Modifier) -> Unit = { m ->
            Tile(m, color = p.block, onClick = { app.nav.push(Page.Set(4)) }) {
                Txt("This week", 12, FontWeight.Bold, p.onBlock.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Txt(fmtListened(weekMs), 30, FontWeight.ExtraBold, if (p.dark) p.onBlock else Lime)
                Txt("listened", 12, FontWeight.Bold, p.onBlock.copy(alpha = 0.7f))
            }
        }
        val repeat: @Composable (Modifier) -> Unit = { m ->
            Tile(m, color = p.soft, onClick = { if (onRepeat.isNotEmpty()) { app.engine.play(onRepeat, 0, from = "On repeat"); app.nav.nowPlaying = true } }) {
                Txt("On repeat", 12, FontWeight.Bold, p.ink.copy(alpha = 0.7f))
                Txt(if (onRepeat.isEmpty()) "Nothing yet" else "${onRepeat.size} song${if (onRepeat.size == 1) "" else "s"}", 17, FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                CoverStack(onRepeat.ifEmpty { recent }.take(3), p.soft)
            }
        }
        val shortcuts: @Composable () -> Unit = {
            if (lastAlbum != null || topArtist != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP(app))) {
                if (lastAlbum != null) ShortTile(lastAlbum, lastAlbum.album, "Album", p.tile, p.ink) { app.nav.push(Page.AlbumP(lastAlbum.albumKey)) }
                if (topArtist != null) ShortTile(artistCover, topArtist, "Artist", p.block, p.onBlock) { app.nav.push(Page.ArtistP(topArtist)) }
            }
        }
        if (app.wide) item {
            // Bento grid: the big Jump back in tile on the left, the small tiles in a 2 x 2 block beside it.
            Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                hero(Modifier.weight(1.15f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        week(Modifier.weight(1f).fillMaxHeight())
                        repeat(Modifier.weight(1f).fillMaxHeight())
                    }
                    shortcuts()
                }
            }
        } else {
            item { hero(Modifier.fillMaxWidth().height(200.dp)) }
            item {
                Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    week(Modifier.weight(1f).fillMaxHeight())
                    repeat(Modifier.weight(1f).fillMaxHeight())
                }
            }
            item { shortcuts() }
        }
        if (recent.isNotEmpty()) {
            item { SectionHead("Recently played") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(recent.take(20), key = { _, t -> "rp" + t.id }) { i, t ->
                        SongCard(t, st.current?.id == t.id) { app.engine.play(recent.take(20), i, from = "Recently played") }
                    }
                }
            }
        }
    }
    }
}

/** Space between Home's tiles: roomier on a wide window. */
private fun GAP(app: AppCtx) = if (app.wide) 16.dp else 10.dp

private const val DAY = 86_400_000L

private fun fmtListened(ms: Long): String {
    val m = ms / 60_000
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}

/** Up to three covers overlapping, as on the On repeat tile. */
@Composable
private fun CoverStack(ts: List<Track>, edge: Color) {
    Box(Modifier.height(38.dp)) {
        ts.forEachIndexed { i, t ->
            Box(Modifier.offset(x = (i * 26).dp).size(38.dp).clip(RoundedCornerShape(10.dp)).background(edge).padding(2.dp)) {
                Cover(t, Modifier.fillMaxSize(), 8.dp)
            }
        }
    }
}

/** A shortcut tile: cover, name and what it is. */
@Composable
private fun RowScope.ShortTile(t: Track?, name: String, kind: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Tile(Modifier.weight(1f), color = bg, pad = 8.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(t, Modifier.size(42.dp), 10.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Txt(name.ifBlank { "—" }, 14, FontWeight.ExtraBold, fg)
                Txt(kind, 12, color = fg.copy(alpha = 0.65f))
            }
        }
    }
}
