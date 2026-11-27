package com.myna.player.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myna.player.OnlineAlbum
import com.myna.player.OnlineArtist
import com.myna.player.Track
import com.myna.player.albums
import com.myna.player.artists
import com.myna.player.fmtTotal
import kotlinx.coroutines.launch

private const val DAY_MS = 86_400_000L
private fun songKey(title: String, artist: String) = "${title.lowercase()}|${artist.lowercase()}"
private fun plural(n: Int, w: String) = "$n $w${if (n == 1) "" else "s"}"

/** The title and songs behind a [Page.ListP] id. */
internal fun songList(app: AppCtx, id: String, lib: List<Track>): Pair<String, List<Track>> {
    val l = app.library
    val now = app.platform.nowMs()
    return when {
        id == "liked" -> "Liked songs" to l.likes.tracks
        id == "added" -> "Recently added" to lib.sortedByDescending { it.addedAt }.take(25)
        id == "repeat" -> {
            val byKey = l.known(lib).associateBy { songKey(it.title, it.artist) }
            "On repeat" to l.history.rows.filter { now - it.at < 30 * DAY_MS }.groupingBy { songKey(it.title, it.artist) }.eachCount()
                .filter { it.value >= 2 }.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(10)
        }
        id == "forgotten" -> {
            // Library songs not played for 30 days; ones you used to play come first.
            val last = HashMap<String, Long>()
            l.history.rows.forEach { r -> val k = songKey(r.title, r.artist); if ((last[k] ?: 0) < r.at) last[k] = r.at }
            val at = { t: Track -> last[songKey(t.title, t.artist)] ?: 0L }
            "Not played in a while" to lib.filter { now - at(it) > 30 * DAY_MS }.sortedByDescending(at).take(40)
        }
        id.startsWith("pl:") -> id.removePrefix("pl:").let { it to l.playlists.songs(it).tracks }
        id.startsWith("folder:") -> id.removePrefix("folder:").let { f -> f.substringAfterLast('/').ifEmpty { "Phone" } to lib.filter { it.folder == f } }
        else -> "" to emptyList()
    }
}

// ---------------------------------------------------------------- Library

@Composable
fun LibraryScreen(app: AppCtx) {
    val p = LocalPal.current
    val nav = app.nav
    val tracks by app.library.tracks.collectAsState()
    val st by app.engine.state.collectAsState()
    val pls = app.library.playlists
    val tab = nav.libTab
    val songs = remember(tracks, nav.songSort) {
        when (nav.songSort) {
            1 -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            2 -> tracks.sortedByDescending { it.addedAt }
            else -> tracks.sortedBy { it.title.lowercase() }
        }
    }
    val albums = remember(tracks, nav.albumSort) {
        val a = tracks.albums()
        when (nav.albumSort) {
            0 -> a.sortedByDescending { al -> al.tracks.maxOf { it.addedAt } }
            2 -> a.sortedBy { it.artist.lowercase() }
            else -> a
        }
    }
    val artists = remember(tracks) { tracks.artists() }
    val folders = remember(tracks) { tracks.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() } }
    val small = when (tab) {
        0 -> "${plural(tracks.size, "song")} · ${fmtTotal(tracks.sumOf { it.durationMs })}"
        1 -> plural(albums.size, "album")
        2 -> plural(artists.size, "artist")
        3 -> "${plural(pls.names.size, "playlist")} · 4 auto"
        else -> plural(folders.size, "folder")
    }
    var naming by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val cols = if (app.wide) GridCells.Adaptive(170.dp) else GridCells.Fixed(2)

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(cols, Modifier.fillMaxSize(), grid, contentPadding = PAD, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header("Library", small, app, search = true) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Songs", "Albums", "Artists", "Playlists", "Folders").forEachIndexed { i, s -> Chip(s, tab == i) { nav.libTab = i } }
                }
            }
            if (tracks.isEmpty() && tab != 3) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibrary(app) }
                return@LazyVerticalGrid
            }
            when (tab) {
                0 -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile(Ic.play, "Play all", true) { app.engine.play(songs, 0, from = "Library") }
                            ActionTile(Ic.shuffle, "Shuffle", false) { app.engine.play(songs, songs.indices.random(), shuffle = true, from = "Library") }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SortRow(listOf("Title A–Z", "Artist A–Z", "Recently added")[nav.songSort]) { nav.songSort = (nav.songSort + 1) % 3 }
                    }
                    items(songs.size, key = { "s" + songs[it].id }, span = { GridItemSpan(maxLineSpan) }) { i ->
                        val t = songs[i]
                        val on = st.current?.id == t.id
                        SongRow(t, on, trail = { SongMenu(app, t, on) }) { app.engine.play(songs, i, from = "Library") }
                    }
                }
                1 -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SortRow(listOf("Recently added", "Title A–Z", "Artist A–Z")[nav.albumSort]) { nav.albumSort = (nav.albumSort + 1) % 3 }
                    }
                    items(albums, key = { it.key }) { a -> AlbumCard(a.tracks.first(), a.title, a.artist) { nav.push(Page.AlbumP(a.key)) } }
                }
                2 -> items(artists, key = { "a" + it.name }, span = { GridItemSpan(maxLineSpan) }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile)
                            .clickable { nav.push(Page.ArtistP(a.name)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(a.tracks.first(), Modifier.size(46.dp).clip(CircleShape), 23.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(a.name, 15, FontWeight.ExtraBold)
                            Txt(plural(a.tracks.size, "song"), 13, FontWeight.SemiBold, p.sub)
                        }
                        Ico(Ic.chev, 18.dp, p.sub)
                    }
                }
                3 -> {
                    item(span = { GridItemSpan(maxLineSpan) }) { Row { ActionTile(Ic.plus, "New playlist", true) { naming = true } } }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Made for you", "auto") }
                    items(listOf("liked", "added", "repeat", "forgotten"), key = { "auto$it" }) { AutoTile(app, it, tracks) }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Your playlists") }
                    if (pls.names.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Tile(Modifier.fillMaxWidth(), pad = 18.dp) {
                            Txt("No playlists yet", 17, FontWeight.ExtraBold)
                            Txt("Make one above, or use ⋯ on any song.", 13, color = p.sub, lines = 2)
                        }
                    }
                    items(pls.names, key = { "pl$it" }, span = { GridItemSpan(maxLineSpan) }) { PlaylistRow(app, it) }
                }
                else -> items(folders, key = { "f" + it.first }, span = { GridItemSpan(maxLineSpan) }) { (f, ts) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile)
                            .clickable { nav.push(Page.ListP("folder:$f")) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(p.soft), contentAlignment = Alignment.Center) { Ico(Ic.folder, 22.dp, p.ink) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(f.substringAfterLast('/').ifEmpty { "Phone" }, 15, FontWeight.ExtraBold)
                            Txt("${plural(ts.size, "song")} · ${f.ifEmpty { "/" }}", 13, FontWeight.SemiBold, p.sub)
                        }
                        Ico(Ic.chev, 18.dp, p.sub)
                    }
                }
            }
        }
        if (tab == 0 && nav.songSort != 2 && songs.size > 20) AzRail(songs, nav.songSort == 1, grid, base = 4)
    }
    if (naming) NameDialog({ pls.create(it) }) { naming = false }
}

@Composable
private fun SortRow(label: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("Sort: $label", 13, FontWeight.Bold, p.sub)
        Spacer(Modifier.width(4.dp))
        Ico(Ic.down, 14.dp, p.sub)
    }
}

/** Letters down the right edge: tap or drag to jump to songs starting with that letter. */
@Composable
private fun BoxScope.AzRail(songs: List<Track>, byArtist: Boolean, grid: LazyGridState, base: Int) {
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val letters = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val letter = { t: Track -> ((if (byArtist) t.artist else t.title).firstOrNull()?.uppercaseChar() ?: '#').let { if (it in 'A'..'Z') it else '#' } }
    val first = remember(songs, byArtist) { HashMap<Char, Int>().also { m -> songs.forEachIndexed { i, t -> m.putIfAbsent(letter(t), i) } } }
    val cur = songs.getOrNull((grid.firstVisibleItemIndex - base).coerceAtLeast(0))?.let(letter)
    fun jump(y: Float, h: Int) {
        val i = (y / h * letters.length).toInt().coerceIn(0, letters.lastIndex)
        // The letter touched, or the next one that has songs.
        val at = letters.substring(i).firstNotNullOfOrNull { first[it] } ?: return
        scope.launch { grid.scrollToItem(base + at) }
    }
    Column(
        Modifier.align(Alignment.CenterEnd).width(16.dp)
            .pointerInput(first) { detectTapGestures { jump(it.y, size.height) } }
            .pointerInput(first) { detectVerticalDragGestures { ch, _ -> jump(ch.position.y, size.height) } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { c ->
            val on = c == cur
            Box(Modifier.size(14.dp, 15.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Lime else Color.Transparent), contentAlignment = Alignment.Center) {
                Txt(c.toString(), 9, FontWeight.ExtraBold, if (on) OnLime else p.sub)
            }
        }
    }
}

@Composable
private fun AutoTile(app: AppCtx, id: String, lib: List<Track>) {
    val p = LocalPal.current
    val n = remember(id, lib, app.library.likes.tracks) { songList(app, id, lib).second.size }
    val (name, icon, bg) = when (id) {
        "liked" -> Triple("Liked songs", Ic.heartOn, p.tile)
        "added" -> Triple("Recently added", Ic.sparkle, p.soft)
        "repeat" -> Triple("On repeat", Ic.repeat, p.block)
        else -> Triple("Not played in a while", Ic.clock, p.lav)
    }
    val fg = if (id == "repeat") p.onBlock else p.ink
    Tile(Modifier.fillMaxWidth().height(118.dp), color = bg, onClick = { app.nav.push(Page.ListP(id)) }) {
        Ico(icon, 24.dp, if (id == "liked") LikeRed else fg)
        Spacer(Modifier.weight(1f))
        Txt(name, 16, FontWeight.ExtraBold, fg, lines = 2)
        Txt(plural(n, "song"), 12, FontWeight.Bold, fg.copy(alpha = 0.7f))
    }
}

@Composable
private fun PlaylistRow(app: AppCtx, name: String) {
    val p = LocalPal.current
    val ts = app.library.playlists.songs(name).tracks
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tile)
            .clickable { app.nav.push(Page.ListP("pl:$name")) }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mosaic(ts, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(name, 15, FontWeight.ExtraBold)
            Txt(plural(ts.size, "song"), 13, FontWeight.SemiBold, p.sub)
        }
        var ask by remember { mutableStateOf(false) }
        MoreBtn(listOf("Delete playlist" to { ask = true }), p.sub)
        if (ask) DeleteDialog(name, { app.library.playlists.delete(name) }) { ask = false }
    }
}

/** A 2×2 grid of the first four covers. */
@Composable
private fun Mosaic(ts: List<Track>, size: Dp) {
    if (ts.size < 4) { Cover(ts.firstOrNull(), Modifier.size(size), 10.dp); return }
    Column(Modifier.size(size).clip(RoundedCornerShape(10.dp))) {
        for (r in 0..1) Row { for (c in 0..1) Cover(ts[r * 2 + c], Modifier.size(size / 2), 0.dp) }
    }
}

// ---------------------------------------------------------------- menus

/** A ⋯ button that opens a short menu. */
@Composable
internal fun MoreBtn(items: List<Pair<String, () -> Unit>>, tint: Color = LocalPal.current.ink, tile: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (tile) HdrBtn(Ic.more) { open = true } else IconBtn(Ic.more, tint) { open = true }
        DropdownMenu(open, { open = false }) {
            items.forEach { (label, act) -> MenuItem(label) { open = false; act() } }
        }
    }
}

/** The ⋯ on a song: like, add to a playlist, go to its album or artist, or take it out of [playlist]. */
@Composable
internal fun SongMenu(app: AppCtx, t: Track, current: Boolean, playlist: String? = null) {
    val p = LocalPal.current
    val pls = app.library.playlists
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val close = { open = false; pick = false }
    Box {
        IconBtn(Ic.more, if (current) p.onBlock else p.sub) { open = true }
        DropdownMenu(open, close) {
            if (pick) {
                pls.names.forEach { n -> MenuItem(n) { pls.songs(n).add(t); close() } }
                MenuItem("New playlist…") { close(); naming = true }
            } else {
                MenuItem(if (app.library.likes.has(t)) "Unlike" else "Like") { app.library.likes.toggle(t); close() }
                MenuItem("Add to playlist…") { pick = true }
                if (t.album.isNotBlank()) MenuItem("Go to album") { close(); app.nav.push(Page.AlbumP(t.albumKey)) }
                MenuItem("Go to artist") { close(); app.nav.push(Page.ArtistP(t.artist)) }
                if (playlist != null) MenuItem("Remove from playlist") { pls.songs(playlist).remove(t); close() }
            }
        }
    }
    if (naming) NameDialog({ pls.create(it); pls.songs(it.trim()).add(t) }) { naming = false }
}

@Composable
internal fun MenuItem(label: String, onClick: () -> Unit) = DropdownMenuItem({ Txt(label, 15, FontWeight.Bold) }, onClick)

@Composable
internal fun NameDialog(onDone: (String) -> Unit, onClose: () -> Unit) {
    val p = LocalPal.current
    var name by remember { mutableStateOf("") }
    MynaDialog("New playlist", "Create", Lime to OnLime, enabled = name.isNotBlank(), onConfirm = { onDone(name) }, onClose = onClose) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.tray).padding(14.dp)) {
            if (name.isEmpty()) Txt("Name", 16, FontWeight.SemiBold, p.sub)
            BasicTextField(
                name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), cursorBrush = SolidColor(p.ink),
            )
        }
    }
}

/** Asks before a playlist is deleted: it can't be brought back. */
@Composable
private fun DeleteDialog(name: String, onDelete: () -> Unit, onClose: () -> Unit) =
    MynaDialog("Delete “$name”?", "Delete", Color(0xFFE5484D) to Color.White, onConfirm = onDelete, onClose = onClose) {
        Txt("The playlist goes for good. The songs stay in your library.", 14, FontWeight.SemiBold, LocalPal.current.sub, lines = 3)
    }

/** A dialog in MYNA's look: a rounded tile, a title, and Cancel / [action] pills. */
@Composable
private fun MynaDialog(
    title: String, action: String, colors: Pair<Color, Color>, enabled: Boolean = true,
    onConfirm: () -> Unit, onClose: () -> Unit, body: @Composable () -> Unit,
) {
    val p = LocalPal.current
    Dialog(onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.tile).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt(title, 20, FontWeight.ExtraBold, lines = 2)
            body()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(p.tray).clickable(onClick = onClose), Alignment.Center) {
                    Txt("Cancel", 15, FontWeight.ExtraBold)
                }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(if (enabled) colors.first else p.line)
                        .clickable(enabled) { onConfirm(); onClose() },
                    Alignment.Center,
                ) { Txt(action, 15, FontWeight.ExtraBold, if (enabled) colors.second else p.sub) }
            }
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun PageHdr(app: AppCtx, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HdrBtn(Ic.back) { app.nav.back() }
        Spacer(Modifier.weight(1f))
        right()
    }
}

@Composable
internal fun AlbumCard(cover: Track, title: String, sub: String, width: Dp? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Tile(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth(), pad = 8.dp, onClick = onClick) {
        Cover(cover, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
        Spacer(Modifier.height(8.dp))
        Txt(title, 14, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Txt(sub, 12, FontWeight.SemiBold, p.sub, modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 2.dp))
    }
}

@Composable
private fun PlayShuffle(app: AppCtx, list: List<Track>, from: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(Ic.play, "Play", true) { if (list.isNotEmpty()) app.engine.play(list, 0, from = from) }
        ActionTile(Ic.shuffle, "Shuffle", false) { if (list.isNotEmpty()) app.engine.play(list, list.indices.random(), shuffle = true, from = from) }
    }
}

/** Liked songs, an auto playlist, one of yours, or a folder. */
@Composable
fun ListScreen(app: AppCtx, id: String) {
    val p = LocalPal.current
    val tracks by app.library.tracks.collectAsState()
    val st by app.engine.state.collectAsState()
    val pl = if (id.startsWith("pl:")) id.removePrefix("pl:") else null
    val (title, list) = songList(app, id, tracks)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PageHdr(app) {
                if (pl != null) {
                    var ask by remember { mutableStateOf(false) }
                    MoreBtn(listOf("Delete playlist" to { ask = true }), tile = true)
                    if (ask) DeleteDialog(pl, { app.library.playlists.delete(pl); app.nav.back() }) { ask = false }
                }
            }
        }
        item {
            Tile(Modifier.fillMaxWidth(), pad = 16.dp) {
                Txt(title, 28, FontWeight.ExtraBold, lines = 2)
                Txt("${plural(list.size, "song")} · ${fmtTotal(list.sumOf { it.durationMs })}", 13, FontWeight.Bold, p.sub)
            }
        }
        if (list.isEmpty()) item {
            Txt(
                when (id) { "liked" -> "Tap the heart on a playing song to keep it here."; "repeat" -> "Songs you play twice in a month show here."
                    "added" -> "New songs on this phone show here."; "forgotten" -> "Songs you haven't played for a month show here."; else -> "Nothing here yet. Use ⋯ on any song to add it." },
                14, color = p.sub, lines = 3, modifier = Modifier.padding(4.dp),
            )
        } else item { PlayShuffle(app, list, title) }
        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
            val on = st.current?.id == t.id
            SongRow(t, on, trail = { SongMenu(app, t, on, pl) }) { app.engine.play(list, i, from = title) }
        }
    }
}

@Composable
fun AlbumScreen(app: AppCtx, key: String) {
    val p = LocalPal.current
    val tracks by app.library.tracks.collectAsState()
    val st by app.engine.state.collectAsState()
    val all = remember(tracks, app.library.likes.tracks, app.library.recent.tracks) { app.library.known(tracks) }
    val a = remember(all, key) { all.albums().firstOrNull { it.key == key } } ?: return
    val year = a.tracks.maxOf { it.year }
    val info = listOfNotNull(a.artist, if (year > 0) "$year" else null, plural(a.tracks.size, "song"), fmtTotal(a.tracks.sumOf { it.durationMs })).joinToString(" · ")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHdr(app) { MoreBtn(listOf("Go to artist" to { app.nav.push(Page.ArtistP(a.artist)) }), tile = true) } }
        item { Cover(a.tracks.first(), Modifier.fillMaxWidth(if (app.wide) 0.5f else 1f).aspectRatio(1.15f), 20.dp) }
        item {
            Tile(Modifier.fillMaxWidth(), pad = 16.dp) {
                Txt(a.title, 28, FontWeight.ExtraBold, lines = 2)
                Spacer(Modifier.height(2.dp))
                Txt(info, 13, FontWeight.Bold, p.sub, lines = 2)
            }
        }
        item { PlayShuffle(app, a.tracks, a.title) }
        itemsIndexed(a.tracks, key = { _, t -> t.id }) { i, t ->
            SongRow(t, st.current?.id == t.id, number = t.trackNo) { app.engine.play(a.tracks, i, from = a.title) }
        }
    }
}

@Composable
fun ArtistScreen(app: AppCtx, name: String) {
    val p = LocalPal.current
    val tracks by app.library.tracks.collectAsState()
    val st by app.engine.state.collectAsState()
    val all = remember(tracks, app.library.likes.tracks, app.library.recent.tracks) { app.library.known(tracks) }
    val list = remember(all, name) { all.filter { it.artist == name } }
    val albums = remember(list) { list.filter { it.album.isNotBlank() }.albums() }
    val counts = remember(list) { app.library.history.rows.filter { it.artist == name }.groupingBy { it.title.lowercase() }.eachCount() }
    // The rest of the artist's music, from the internet. null while it loads.
    val web by produceState<Result<OnlineArtist?>?>(null, name) {
        val o = app.platform.online
        value = if (o == null || o.problem != null) Result.success(null) else runCatching { o.artist(name) }
    }
    val net = web?.getOrNull()
    val top = remember(list, counts, net) {
        (list.sortedByDescending { counts[it.title.lowercase()] ?: 0 } + net?.top.orEmpty())
            .distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }.take(10)
    }
    val mine = remember(albums) { albums.map { it.title.lowercase() }.toSet() }
    val webAlbums = remember(net, mine) { net?.albums.orEmpty().filter { it.title.lowercase() !in mine && it.kind != "compile" } }
    val full = webAlbums.filter { it.kind == "album" }
    val singles = webAlbums.filter { it.kind != "album" }
    val cols = if (app.wide) GridCells.Adaptive(170.dp) else GridCells.Fixed(2)
    LazyVerticalGrid(cols, Modifier.fillMaxSize(), contentPadding = PAD, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHdr(app) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().aspectRatio(if (app.wide) 2.4f else 1.25f).clip(RoundedCornerShape(20.dp))) {
                CoverFill(net?.pictureTrack ?: list.firstOrNull())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xB8000000)))))
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp).padding(end = 64.dp)) {
                    Txt("ARTIST", 11, FontWeight.Bold, Color.White.copy(alpha = 0.85f))
                    Txt(name, 26, FontWeight.ExtraBold, Color.White, lines = 2)
                    val nAlbums = albums.size + full.size
                    Txt(listOfNotNull(if (nAlbums == 0) null else plural(nAlbums, "album"), if (net != null) null else plural(list.size, "song"), counts.values.sum().let { if (it > 0) plural(it, "play") else null }).joinToString(" · "), 12, FontWeight.Bold, Color.White.copy(alpha = 0.85f))
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                    RoundBtn(Ic.play, 50.dp, Lime, OnLime) { if (top.isNotEmpty()) app.engine.play(top, 0, from = name) }
                }
            }
        }
        if (top.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Top songs") }
            items(top.size, key = { "t" + top[it].id }, span = { GridItemSpan(maxLineSpan) }) { i ->
                val t = top[i]
                val on = st.current?.id == t.id
                SongRow(t, on, number = i + 1, cover = true, trail = { SongMenu(app, t, on) }) { app.engine.play(top, i, from = name) }
            }
        }
        if (albums.isNotEmpty() || full.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Albums") }
            items(albums, key = { "al" + it.key }) { a ->
                val y = a.tracks.maxOf { it.year }
                AlbumCard(a.tracks.first(), a.title, if (y > 0) "$y" else plural(a.tracks.size, "song")) { app.nav.push(Page.AlbumP(a.key)) }
            }
            items(full, key = { "wa" + it.id }) { a -> AlbumCard(a.coverTrack, a.title, if (a.year > 0) "${a.year}" else "Album") { app.nav.push(Page.OnlineAlbumP(a)) } }
        }
        if (singles.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Singles & EPs") }
            items(singles, key = { "ws" + it.id }) { a ->
                AlbumCard(a.coverTrack, a.title, listOfNotNull(if (a.kind == "ep") "EP" else "Single", if (a.year > 0) "${a.year}" else null).joinToString(" · ")) { app.nav.push(Page.OnlineAlbumP(a)) }
            }
        }
        if (web == null) item(span = { GridItemSpan(maxLineSpan) }) {
            Txt("Finding more from $name…", 13, FontWeight.SemiBold, p.sub, modifier = Modifier.padding(4.dp))
        }
    }
}

/** An album found online: its songs load from the internet and play from YouTube. */
@Composable
fun OnlineAlbumScreen(app: AppCtx, a: OnlineAlbum) {
    val p = LocalPal.current
    val st by app.engine.state.collectAsState()
    val songs by produceState<List<Track>?>(null, a.id) { value = app.platform.online?.album(a).orEmpty() }
    val list = songs.orEmpty()
    val info = listOfNotNull(
        a.artist, if (a.year > 0) "${a.year}" else null,
        if (list.isEmpty()) null else plural(list.size, "song"), if (list.isEmpty()) null else fmtTotal(list.sumOf { it.durationMs }),
    ).joinToString(" · ")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHdr(app) { MoreBtn(listOf("Go to artist" to { app.nav.push(Page.ArtistP(a.artist)) }), tile = true) } }
        item { Cover(a.coverTrack, Modifier.fillMaxWidth(if (app.wide) 0.5f else 1f).aspectRatio(1.15f), 20.dp) }
        item {
            Tile(Modifier.fillMaxWidth(), pad = 16.dp) {
                Txt(a.title, 28, FontWeight.ExtraBold, lines = 2)
                Spacer(Modifier.height(2.dp))
                Txt(info, 13, FontWeight.Bold, p.sub, lines = 2)
            }
        }
        when {
            songs == null -> item { Txt("Loading songs…", 14, color = p.sub, modifier = Modifier.padding(4.dp)) }
            list.isEmpty() -> item { Txt("Couldn't load this album's songs. Check your connection.", 14, color = p.sub, lines = 2, modifier = Modifier.padding(4.dp)) }
            else -> item { PlayShuffle(app, list, a.title) }
        }
        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
            val on = st.current?.id == t.id
            SongRow(t, on, number = t.trackNo, trail = { SongMenu(app, t, on) }) { app.engine.play(list, i, from = a.title) }
        }
    }
}
