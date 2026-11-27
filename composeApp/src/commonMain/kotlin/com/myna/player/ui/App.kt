package com.myna.player.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
}

/** Screen stack plus the Now Playing sheet. [back] returns false when there is nothing left to close. */
class Nav {
    val stack = mutableStateListOf<Page>(Page.Home)
    var nowPlaying by mutableStateOf(false)
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
        Row(Modifier.weight(1f).clickable(enabled = t != null) { app.nav.nowPlaying = true }, verticalAlignment = Alignment.CenterVertically) {
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
            FadeChip(app)
            IconBtn(Ic.queue) { if (t != null) app.nav.nowPlaying = true }
        }
    }
}
