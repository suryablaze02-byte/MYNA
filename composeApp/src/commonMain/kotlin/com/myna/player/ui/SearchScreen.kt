package com.myna.player.ui

import com.myna.player.baseTitle
import com.myna.player.named
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myna.player.Catalogue
import com.myna.player.Track
import com.myna.player.albums
import com.myna.player.artists
import kotlinx.coroutines.launch

private val MOODS = listOf("Party", "Chill", "Focus", "Workout")
private val GENRES = listOf("Pop", "Country", "Hip hop", "R&B", "Latin", "K-pop", "Bollywood", "Telugu", "Tamil")

/** True when every word of [q] is somewhere in [text]. */
private fun matches(text: String, words: List<String>) = text.lowercase().let { t -> words.all { it in t } }

@Composable
fun SearchScreen(app: AppCtx) {
    val p = LocalPal.current
    val nav = app.nav
    val prefs = app.library.prefs
    val tracks by app.library.tracks.collectAsState()
    val st by app.engine.state.collectAsState()
    val q = nav.query.trim()
    val words = remember(q) { q.lowercase().split(' ').filter { it.isNotBlank() } }
    val all = remember(tracks, app.library.likes.tracks, app.library.recent.tracks) { app.library.known(tracks) }
    val mine = remember(all) { all.map { "${it.title.lowercase()}|${it.artist.lowercase()}" }.toHashSet() }
    val songs = remember(all, words) { if (words.isEmpty()) emptyList() else all.filter { matches("${it.title} ${it.artist} ${it.album}", words) } }
    val albums = remember(all, words) { if (words.isEmpty()) emptyList() else all.filter { it.album.isNotBlank() }.albums().filter { matches("${it.title} ${it.artist}", words) } }
    val artists = remember(all, words) { if (words.isEmpty()) emptyList() else all.artists().filter { matches(it.name, words) } }
    val online = rememberOnlineSearch(app, q)
    val scope = rememberCoroutineScope()
    val save = { prefs.addSearch(q) }
    val tab = nav.searchTab

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Header("Search", null, app) }
        item { SearchField(app, save) }
        if (q.isEmpty()) {
            browse(app)
            return@LazyColumn
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Songs", "Albums", "Artists").forEachIndexed { i, s -> Chip(s, tab == i) { nav.searchTab = i } }
            }
        }
        val web = online?.songs.orEmpty().filter { "${it.title.lowercase()}|${it.artist.lowercase()}" !in mine }
        // Searching an artist by name: the artist is the top result, not one of their songs.
        // A film, album or artist named exactly is the top result, even over songs you have.
        val topAlbum = online?.topAlbum
        val topArtist = online?.artist?.takeIf { topAlbum == null && it.named(q) }
        val top = songs.firstOrNull() ?: web.firstOrNull()
        if (tab == 0 && topAlbum != null) item {
            val kind = if (topAlbum.title.contains("Soundtrack", true)) "SOUNDTRACK" else "ALBUM"
            TopResult(topAlbum.coverTrack, kind, topAlbum.baseTitle, listOfNotNull(topAlbum.artist, topAlbum.year.takeIf { it > 0 }?.toString()).joinToString(" · "), round = false,
                onPlay = { save(); if (online.topTracks.isNotEmpty()) { app.engine.play(online.topTracks, 0, from = topAlbum.baseTitle); nav.nowPlaying = true } }) {
                save(); nav.push(Page.OnlineAlbumP(topAlbum))
            }
        } else if (tab == 0 && topArtist != null) item {
            TopResult(topArtist.pictureTrack, "ARTIST", topArtist.name, "Artist", round = true,
                onPlay = { save(); app.engine.play(topArtist.top, 0, from = topArtist.name); nav.nowPlaying = true }) { save(); nav.push(Page.ArtistP(topArtist.name)) }
        } else if (tab == 0 && top != null) item {
            val list = if (songs.isNotEmpty()) songs else web
            val play = { save(); app.engine.play(list, 0, from = "Search"); nav.nowPlaying = true }
            TopResult(top, "SONG", top.title, top.artist, round = false, onPlay = play, onClick = play)
        }
        val shown = if (tab == 0) songs.drop(if (topAlbum != null || topArtist != null) 0 else 1).take(5) else songs
        if (tab <= 1 && shown.isNotEmpty()) {
            item { SectionHead("Your songs") }
            itemsIndexed(shown, key = { _, t -> "q" + t.id }) { _, t ->
                val on = st.current?.id == t.id
                SongRow(t, on, trail = { SongMenu(app, t, on) }) { save(); app.engine.play(songs, songs.indexOf(t), from = "Search") }
            }
        }
        // The artist's releases found online: albums first, then singles and EPs.
        val webAlbums = (online?.albums.orEmpty() + online?.artist?.albums.orEmpty())
            .filter { w -> w.kind != "compile" && albums.none { it.title.equals(w.title, true) } }
            .distinctBy { it.title.lowercase() + it.kind }
            .sortedBy { if (it == online?.topAlbum) 0 else if (it.kind == "album") 1 else 2 }
        if (tab == 0 || tab == 2) if (albums.isNotEmpty() || webAlbums.isNotEmpty()) {
            item { SectionHead("Albums") }
            val mineShown = if (tab == 0) albums.take(4) else albums
            items(mineShown, key = { "al" + it.key }) { a ->
                LinkRow(a.tracks.first(), a.title, "Album · ${a.artist}", round = false) { save(); nav.push(Page.AlbumP(a.key)) }
            }
            items(if (tab == 0) webAlbums.take(4 - mineShown.size.coerceAtMost(4)) else webAlbums, key = { "wa" + it.id }) { a ->
                val kind = when (a.kind) { "album" -> "Album"; "ep" -> "EP"; else -> "Single" }
                LinkRow(a.coverTrack, a.title, listOfNotNull(kind, a.artist, if (a.year > 0) "${a.year}" else null).joinToString(" · "), round = false) {
                    save(); nav.push(Page.OnlineAlbumP(a))
                }
            }
            if (tab == 0 && albums.size + webAlbums.size > 4) item {
                Txt("See all ${albums.size + webAlbums.size}", 14, FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.searchTab = 2 }.padding(8.dp))
            }
        }
        val webArtist = online?.artist?.takeIf { w -> w != topArtist && artists.none { it.name.equals(w.name, true) } }
        if (tab == 0 || tab == 3) if (webArtist != null) {
            item { SectionHead("Artists") }
            item { LinkRow(webArtist.pictureTrack, webArtist.name, "Artist", round = true) { save(); nav.push(Page.ArtistP(webArtist.name)) } }
        }
        if (tab == 0 || tab == 3) if (artists.isNotEmpty()) {
            if (webArtist == null) item { SectionHead("Artists") }
            items(if (tab == 0) artists.take(4) else artists, key = { "ar" + it.name }) { a ->
                LinkRow(a.tracks.first(), a.name, "Artist · ${a.tracks.size} song${if (a.tracks.size == 1) "" else "s"}", round = true) { save(); nav.push(Page.ArtistP(a.name)) }
            }
        }
        if (online != null && tab <= 1) {
            item { SectionHead("Stream") }
            val s = online.songs?.let { web }
            when {
                online.problem != null -> item { Txt(online.problem, 13, color = p.sub, lines = 3, modifier = Modifier.padding(4.dp)) }
                s == null -> item { Txt("Searching…", 13, color = p.sub, modifier = Modifier.padding(4.dp)) }
                s.isEmpty() -> item { Txt("Nothing found to stream", 13, color = p.sub, modifier = Modifier.padding(4.dp)) }
                else -> itemsIndexed(if (tab == 0 && songs.isEmpty() && topArtist == null && topAlbum == null) s.drop(1) else s, key = { _, t -> "o" + t.id }) { _, t ->
                    val on = st.current?.id == t.id
                    SongRow(t, on, trail = { SongMenu(app, t, on) }) { save(); app.engine.play(s, s.indexOf(t), from = "Search") }
                }
            }
        }
        if (tab == 0 && online != null && online.lists.isNotEmpty()) {
            item { SectionHead("Playlists") }
            items(online.lists, key = { "l" + it.ref }) { l -> ListRow(l) { save(); scope.launch { playList(app, l) } } }
        }
        if (online == null && songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) item {
            Txt("Nothing matches “$q”", 14, color = p.sub, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun SearchField(app: AppCtx, onSearch: () -> Unit) {
    val p = LocalPal.current
    val keys = LocalSoftwareKeyboardController.current
    val typed = app.nav.query.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Ic.search, 20.dp, if (typed) p.ink else p.sub)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (!typed) Txt("Songs, albums, artists", 15, FontWeight.SemiBold, p.sub)
            BasicTextField(
                app.nav.query, { app.nav.query = it }, singleLine = true,
                textStyle = TextStyle(color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(); keys?.hide() }),
            )
        }
        if (typed) Box(Modifier.clip(CircleShape).clickable { app.nav.query = "" }.padding(2.dp)) { Ico(Ic.x, 18.dp, p.sub) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.browse(app: AppCtx) {
    val prefs = app.library.prefs
    item(key = "recent") {
        if (prefs.searches.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHead("Recent", "Clear") { prefs.clearSearches() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prefs.searches.forEach { s -> Chip(s, false) { app.nav.query = s } }
            }
        }
    }
    item { SectionHead("Moods") }
    item {
        val p = LocalPal.current
        val tones = listOf(p.tile to p.ink, p.soft to p.ink, p.block to p.onBlock, p.lav to p.ink)
        val icons = listOf(Ic.sparkle, Ic.moon, Ic.clock, Ic.fade)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in 0..1) {
                    val i = r * 2 + c
                    MoodTile(MOODS[i], icons[i], tones[i].first, tones[i].second) { app.nav.query = MOODS[i].lowercase() }
                }
            }
        }
    }
    item { SectionHead("Genres") }
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GENRES.forEach { g -> Chip(g, false) { app.nav.query = g } }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MoodTile(name: String, icon: ImageVector, bg: Color, fg: Color, onClick: () -> Unit) {
    val n = remember(name) { Catalogue.search(name).size }
    Tile(Modifier.weight(1f).height(92.dp), color = bg, onClick = onClick) {
        Ico(icon, 20.dp, fg)
        Spacer(Modifier.weight(1f))
        Txt(name, 16, FontWeight.ExtraBold, fg)
        Txt(if (n > 0) "$n playlist${if (n == 1) "" else "s"}" else "Songs", 12, FontWeight.Bold, fg.copy(alpha = 0.7f))
    }
}

@Composable
private fun TopResult(cover: Track, kind: String, title: String, sub: String, round: Boolean, onPlay: () -> Unit, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tile).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, Modifier.size(72.dp), if (round) 36.dp else 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt("TOP RESULT · $kind", 11, FontWeight.Bold, p.sub)
            Txt(title, 20, FontWeight.ExtraBold)
            Txt(sub, 13, FontWeight.SemiBold, p.sub)
        }
        RoundBtn(Ic.play, 44.dp, Lime, OnLime, onClick = onPlay)
    }
}

@Composable
private fun LinkRow(t: Track, title: String, sub: String, round: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(t, Modifier.size(46.dp), if (round) 23.dp else 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, 15, FontWeight.ExtraBold)
            Txt(sub, 13, FontWeight.SemiBold, p.sub)
        }
        Ico(Ic.chev, 18.dp, p.sub)
    }
}
