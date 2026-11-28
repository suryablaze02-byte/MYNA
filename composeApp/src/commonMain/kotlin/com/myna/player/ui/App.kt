package com.myna.player.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myna.player.Engine
import com.myna.player.Library
import com.myna.player.Platform

sealed interface Page {
    data object Home : Page
    data object Discover : Page
    data object Lib : Page
    data object Search : Page
    data object Settings : Page
    /** A Settings page: 0 Playback, 1 Appearance & covers, 2 Library, 3 Sleep timer, 4 Your stats, 5 About. */
    data class Set(val which: Int) : Page
    data class AlbumP(val key: String) : Page
    data class ArtistP(val name: String) : Page
    /** A list of songs: "liked", "added", "repeat", "forgotten", "pl:<name>" or "folder:<path>". */
    data class ListP(val id: String) : Page
    /** An album found online (from an artist page or Search). */
    data class OnlineAlbumP(val album: com.myna.player.OnlineAlbum) : Page
    /** A streamed song's album, looked up online when the page opens. */
    data class SongAlbumP(val track: com.myna.player.Track) : Page
}

/** Screen stack plus the Now Playing sheet. [back] returns false when there is nothing left to close. */
class Nav {
    val stack = mutableStateListOf<Page>(Page.Home)
    /** Windows has no Now Playing page: the bar, the queue panel and the mini player do its work. */
    var noPlayerPage = false
    private var np by mutableStateOf(false)
    var nowPlaying: Boolean
        get() = np
        set(v) { np = v && !noPlayerPage }
    /** Windows: the small always-on-top player is showing instead of the main window. */
    var mini by mutableStateOf(false)
    /** Windows: the queue panel on the right is open. */
    var queueOpen by mutableStateOf(false)
    /** Windows: the lyrics panel beside the page. */
    var lyricsOpen by mutableStateOf(false)
    var libTab by mutableIntStateOf(0)
    var songSort by mutableIntStateOf(0)
    var albumSort by mutableIntStateOf(0)
    var searchTab by mutableIntStateOf(0)
    /** Inside Now Playing: 0 player, 1 lyrics, 2 queue, 3 sleep sheet. */
    var npPage by mutableIntStateOf(0)
    var query by mutableStateOf("")
    val top: Page get() = stack.last()
    val root: Page get() = stack.first()
    val inSettings: Boolean get() = top == Page.Settings || top is Page.Set

    fun go(p: Page) { stack.clear(); stack.add(p); nowPlaying = false }
    fun push(p: Page) { stack.add(p); nowPlaying = false }
    fun back(): Boolean {
        if (nowPlaying && npPage != 0) { npPage = 0; return true }
        if (nowPlaying) { nowPlaying = false; return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (top != Page.Home) { go(Page.Home); return true }
        return false
    }
}

class AppCtx(val platform: Platform, val engine: Engine, val library: Library, val nav: Nav, val wide: Boolean)

@Composable
fun MynaApp(platform: Platform, engine: Engine, library: Library, nav: Nav) {
    MynaTheme(library.prefs.theme.v) {
        CompositionLocalProvider(LocalArt provides library.art) {
            val p = LocalPal.current
            BoxWithConstraints(Modifier.fillMaxSize().background(p.tray)) {
                val app = AppCtx(platform, engine, library, nav, wide = maxWidth >= 760.dp)
                // On phones, size everything in the same proportion to the screen as the mockup
                // (its 300px phone lays out about 330dp wide), so wide phones don't look slim.
                val d = LocalDensity.current
                val scale = if (app.wide) 1f else (maxWidth.value / 330f).coerceIn(1f, 1.35f)
                CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
                    if (app.wide) WideLayout(app) else PhoneLayout(app)
                    AnimatedVisibility(
                        nav.nowPlaying,
                        enter = slideInVertically { it / 3 } + fadeIn(),
                        exit = slideOutVertically { it / 3 } + fadeOut(),
                    ) { NowPlaying(app) }
                }
            }
        }
    }
}

@Composable
private fun PageContent(app: AppCtx) {
    when (val pg = app.nav.top) {
        Page.Home -> HomeScreen(app)
        Page.Discover -> DiscoverScreen(app)
        Page.Lib -> LibraryScreen(app)
        Page.Search -> SearchScreen(app)
        Page.Settings -> SettingsScreen(app)
        is Page.Set -> SettingsPage(app, pg.which)
        is Page.AlbumP -> AlbumScreen(app, pg.key)
        is Page.ArtistP -> ArtistScreen(app, pg.name)
        is Page.ListP -> ListScreen(app, pg.id)
        is Page.OnlineAlbumP -> OnlineAlbumScreen(app, pg.album)
        is Page.SongAlbumP -> SongAlbumScreen(app, pg.track)
    }
}

// ---------------------------------------------------------------- phone

@Composable
private fun PhoneLayout(app: AppCtx) {
    val st by app.engine.state.collectAsState()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Box(Modifier.weight(1f)) { PageContent(app) }
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st.current != null) MiniPlayer(app)
            TabBar(app)
        }
    }
}

@Composable
private fun MiniPlayer(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val t = st.current ?: return
    // As in the mockup: a white tile, lime play button, plain Next, and a thin progress line under the text.
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tile).clickable { app.nav.nowPlaying = true }) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t, Modifier.size(44.dp), 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(t.title, 15, FontWeight.ExtraBold)
                Txt(if (st.fading) "Fading in…" else t.artist, 13, FontWeight.Bold, p.sub)
            }
            LikeBtn(app, t)
            RoundBtn(if (st.playing) Ic.pause else Ic.play, 42.dp, Lime, OnLime) { app.engine.toggle() }
            IconBtn(Ic.next) { app.engine.next() }
        }
        val frac = if (st.durationMs > 0) (st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(start = 64.dp, end = 16.dp, bottom = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.ink))
        }
    }
}

@Composable
private fun TabBar(app: AppCtx) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF111111)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tab(Ic.home, "Home", app.nav.root == Page.Home || app.nav.root == Page.Settings) { app.nav.go(Page.Home) }
        Tab(Ic.compass, "Discover", app.nav.root == Page.Discover) { app.nav.go(Page.Discover) }
        Tab(Ic.lib, "Library", app.nav.root == Page.Lib) { app.nav.go(Page.Lib) }
        Tab(Ic.search, "Search", app.nav.root == Page.Search) { app.nav.go(Page.Search) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(v: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    // Black bar, icon over label on every tab; the open tab is a lime pill.
    val fg = if (on) OnLime else Color(0xFF8C8C94)
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Lime else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ico(v, 20.dp, fg)
        Txt(label, 11, FontWeight.ExtraBold, fg)
    }
}

// ---------------------------------------------------------------- wide (tablet / Windows)

@Composable
private fun WideLayout(app: AppCtx) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.weight(1f)) {
            Sidebar(app)
            Box(Modifier.weight(1f)) { PageContent(app) }
            if (app.nav.lyricsOpen) LyricsPanel(app)
            if (app.nav.queueOpen) QueuePanel(app)
        }
        PlayerBar(app)
    }
}

@Composable
private fun Sidebar(app: AppCtx) {
    val p = LocalPal.current
    Column(Modifier.width(230.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(start = 8.dp, bottom = 18.dp, top = 4.dp)) { Wordmark(26.dp) }
        SideItem(Ic.home, "Home", app.nav.root == Page.Home && !app.nav.inSettings) { app.nav.go(Page.Home) }
        SideItem(Ic.compass, "Discover", app.nav.root == Page.Discover) { app.nav.go(Page.Discover) }
        SideItem(Ic.lib, "Library", app.nav.root == Page.Lib) { app.nav.go(Page.Lib) }
        SideItem(Ic.search, "Search", app.nav.root == Page.Search) { app.nav.go(Page.Search) }
        Spacer(Modifier.weight(1f))
        SideItem(Ic.gear, "Settings", app.nav.inSettings) { app.nav.go(Page.Settings) }
    }
}

@Composable
private fun SideItem(v: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) Lime else p.tray)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(v, 20.dp, if (on) OnLime else p.ink)
        Spacer(Modifier.width(12.dp))
        Txt(label, 15, FontWeight.Bold, if (on) OnLime else p.ink)
    }
}

@Composable
private fun PlayerBar(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val t = st.current
    Row(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp)).background(p.tile).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // On Windows the bar is the player, so the cover opens nothing.
        Row(Modifier.weight(1f).clickable(enabled = t != null && !app.platform.isDesktop) { app.nav.nowPlaying = true }, verticalAlignment = Alignment.CenterVertically) {
            Cover(t, Modifier.size(56.dp), 12.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Txt(t?.title ?: "Nothing playing", 15, FontWeight.Bold)
                Txt(t?.artist ?: "Pick a song to start", 13, color = p.sub)
            }
        }
        Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            Transport(app, small = true)
            SeekBar(app)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            t?.let { LikeBtn(app, it) }
            if (app.platform.isDesktop) {
                t?.let { SongMenu(app, it, false) }
                IconBtn(Ic.lyr, if (app.nav.lyricsOpen) p.ink else p.sub) { app.nav.lyricsOpen = !app.nav.lyricsOpen; app.nav.queueOpen = false }
            }
            FadeChip(app)
            if (app.platform.isDesktop) IconBtn(Ic.mini) { app.nav.mini = true }
            // The queue: a panel on Windows, Now Playing's queue page on tablets.
            IconBtn(Ic.queue) {
                if (app.platform.isDesktop) { app.nav.queueOpen = !app.nav.queueOpen; app.nav.lyricsOpen = false }
                else if (t != null) { app.nav.npPage = 2; app.nav.nowPlaying = true }
            }
        }
    }
}

/** Windows: the playing song's lyrics beside the page. */
@Composable
private fun LyricsPanel(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    Column(Modifier.width(380.dp).fillMaxHeight().padding(top = 16.dp, end = 12.dp).clip(RoundedCornerShape(20.dp)).background(p.tile)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Lyrics", 18, FontWeight.ExtraBold)
                st.current?.let { Txt(it.title, 12, color = p.sub) }
            }
            IconBtn(Ic.x) { app.nav.lyricsOpen = false }
        }
        val t = st.current
        if (t == null) Txt("Nothing playing", 14, color = p.sub, modifier = Modifier.padding(18.dp))
        else LyricsBox(app, rememberLyrics(app, t), Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
    }
}

/** Windows: the queue beside the page. Drag songs by the handle to change the order. */
@Composable
private fun QueuePanel(app: AppCtx) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    Column(Modifier.width(380.dp).fillMaxHeight().padding(top = 16.dp, end = 12.dp).clip(RoundedCornerShape(20.dp)).background(p.tile)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Queue", 18, FontWeight.ExtraBold)
                if (st.queue.isNotEmpty()) Txt("${st.queue.size - st.index} songs · ${com.myna.player.fmtTotal(st.queue.drop(st.index).sumOf { it.durationMs })}", 12, color = p.sub)
            }
            IconBtn(Ic.x) { app.nav.queueOpen = false }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            st.current?.let { c -> item { SectionHead("Now playing") }; item { SongRow(c, true) {} } }
            item { SectionHead("Up next") }
            upNext(app)
        }
    }
}

// ---------------------------------------------------------------- Windows mini player

/** A small square of the album art; the controls show while the mouse is over it, or when paused. */
@Composable
fun MiniPlayer(platform: Platform, engine: Engine, library: Library, nav: Nav) {
    MynaTheme(library.prefs.theme.v) {
        CompositionLocalProvider(LocalArt provides library.art) {
            val st by engine.state.collectAsState()
            val t = st.current
            val hover = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val over by hover.collectIsHoveredAsState()
            Box(Modifier.fillMaxSize().background(Color(0xFF111111)).hoverable(hover)) {
                Cover(t, Modifier.fillMaxSize(), 0.dp)
                // Faded, never removed: the first click on the window always reaches the buttons.
                val shown by androidx.compose.animation.core.animateFloatAsState(if (over || !st.playing || t == null) 1f else 0f)
                run {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }.background(Color.Black.copy(alpha = 0.55f)).padding(12.dp)) {
                        Box(Modifier.align(Alignment.TopEnd)) { IconBtn(Ic.expand, Color.White, 36.dp) { nav.mini = false } }
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(t?.title ?: "Nothing playing", 15, FontWeight.ExtraBold, Color.White)
                            Txt(t?.artist ?: "Pick a song in MYNA", 12, FontWeight.Bold, Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconBtn(Ic.prev, Color.White) { engine.prev() }
                                RoundBtn(if (st.playing) Ic.pause else Ic.play, 52.dp, Lime, OnLime) { engine.toggle() }
                                IconBtn(Ic.next, Color.White) { engine.next() }
                            }
                            val frac = if (st.durationMs > 0) (st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
                            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Lime))
                            }
                        }
                    }
                }
            }
        }
    }
}
