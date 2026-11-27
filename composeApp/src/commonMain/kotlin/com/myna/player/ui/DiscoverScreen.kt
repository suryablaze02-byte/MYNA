package com.myna.player.ui

import com.myna.player.OnlineAlbum
import com.myna.player.named
import com.myna.player.songKey
import kotlinx.coroutines.async
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myna.player.OnlineList
import com.myna.player.Shelf
import com.myna.player.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(app: AppCtx) {
    val p = LocalPal.current
    val d = app.library.discover
    val shelves by d.shelves.collectAsState()
    val loading by d.loading.collectAsState()
    val st by app.engine.state.collectAsState()
    val problem = app.platform.online?.problem ?: if (app.platform.online == null) "Online music comes to Windows next" else null
    LaunchedEffect(Unit) { if (shelves.isEmpty()) d.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("From what you play", 13, FontWeight.Bold, p.sub)
                    Txt("Discover", 30, FontWeight.ExtraBold)
                }
                if (problem == null) {
                    if (loading) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), color = p.ink, strokeWidth = 2.dp)
                    else IconBtn(Ic.refresh) { d.refresh() }
                }
            }
        }
        if (problem != null) {
            item { Tile(Modifier.fillMaxWidth(), pad = 20.dp) { Txt(problem, 15, FontWeight.Bold, lines = 3) } }
        } else if (shelves.isEmpty()) {
            item {
                Tile(Modifier.fillMaxWidth(), pad = 20.dp) {
                    Txt(if (loading) "Finding music for you…" else "Couldn't load Discover.", 15, FontWeight.Bold, lines = 2)
                    Spacer(Modifier.height(4.dp))
                    Txt(if (loading) "Picking songs from what you play." else "Check the internet and tap refresh.", 13, color = p.sub, lines = 2)
                }
            }
        }
        shelves.forEach { shelf ->
            item(key = "h" + shelf.source + shelf.title) { ShelfHead(shelf) { app.engine.play(shelf.tracks, 0, from = shelf.title); app.nav.nowPlaying = true } }
            item(key = "r" + shelf.source + shelf.title) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(shelf.tracks.take(40), key = { i, t -> "$i" + t.id }) { i, t ->
                        SongCard(t, st.current?.id == t.id) { app.engine.play(shelf.tracks, i, from = shelf.title) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfHead(shelf: Shelf, onPlay: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(shelf.title, 18, FontWeight.ExtraBold)
            Txt(shelf.why, 12, color = p.sub)
        }
        RoundBtn(Ic.play, 40.dp, Lime, OnLime, onClick = onPlay)
    }
}

@Composable
fun SongCard(t: Track, current: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Tile(Modifier.width(140.dp), pad = 8.dp, onClick = onClick) {
        Box {
            Cover(t, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
            if (current) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                Ico(Ic.play, 12.dp, OnLime)
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(t.title, 14, FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        Txt(t.artist, 12, color = p.sub, modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 2.dp))
    }
}

/** Online results for a Search query: YouTube Music songs, and Spotify / Apple Music playlists that match. */
class OnlineSearch(
    val q: String, val songs: List<Track>?, val lists: List<OnlineList>, val problem: String?, val artist: com.myna.player.OnlineArtist? = null,
    val albums: List<OnlineAlbum> = emptyList(),
    /** The album the query names (a film's soundtrack), and its songs: the top result. */
    val topAlbum: OnlineAlbum? = null, val topTracks: List<Track> = emptyList(),
)

@Composable
fun rememberOnlineSearch(app: AppCtx, q: String): OnlineSearch? {
    val online = app.platform.online ?: return null
    var songs by remember { mutableStateOf<List<Track>?>(null) }
    var artist by remember { mutableStateOf<com.myna.player.OnlineArtist?>(null) }
    var albums by remember { mutableStateOf<List<OnlineAlbum>>(emptyList()) }
    var topAlbum by remember { mutableStateOf<OnlineAlbum?>(null) }
    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(q) {
        songs = null
        artist = null
        albums = emptyList()
        topAlbum = null
        topTracks = emptyList()
        if (q.length < 2 || online.problem != null) return@LaunchedEffect
        kotlinx.coroutines.delay(450) // wait until typing stops
        val a = async { runCatching { online.artist(q) }.getOrNull() }
        val al = async { online.albums(q) }
        val found = runCatching { online.songs(q, 15) }.getOrDefault(emptyList()).distinctBy { it.songKey }
        songs = found
        artist = a.await()
        albums = al.await()
        // A film or album named exactly ("Kabir Singh") beats a little-known artist of the same name;
        // an artist named exactly beats albums that only start with the words.
        val full = albums.filter { it.kind == "album" }
        val named = artist?.takeIf { it.named(q) }
        val album = full.firstOrNull { it.named(q) } ?: if (named == null) full.firstOrNull { it.named(q, start = true) } else null
        if (album != null) {
            topAlbum = album
            topTracks = runCatching { online.album(album) }.getOrDefault(emptyList())
            songs = (topTracks + found).distinctBy { it.songKey }
        } else if (named != null) {
            // Searching an artist: their best known songs (official covers) come first.
            songs = (named.top.take(5) + found).distinctBy { it.songKey }
        }
    }
    val lists = remember(q) { com.myna.player.Catalogue.search(q) }
    return OnlineSearch(q, songs, lists, online.problem, artist, albums, topAlbum, topTracks)
}

fun LazyListScope.onlineResults(app: AppCtx, r: OnlineSearch, currentId: String?, scope: CoroutineScope) {
    item { SectionHead("Stream") }
    val s = r.songs
    when {
        r.problem != null -> item { Txt(r.problem, 13, color = LocalPal.current.sub, modifier = Modifier.padding(4.dp)) }
        s == null -> item { Txt("Searching…", 13, color = LocalPal.current.sub, modifier = Modifier.padding(4.dp)) }
        s.isEmpty() -> item { Txt("Nothing found to stream", 13, color = LocalPal.current.sub, modifier = Modifier.padding(4.dp)) }
        else -> itemsIndexed(s, key = { _, t -> "o" + t.id }) { i, t -> SongRow(t, currentId == t.id) { app.engine.play(s, i) } }
    }
    if (r.lists.isNotEmpty()) {
        item { SectionHead("Playlists") }
        r.lists.forEach { l -> item(key = "l" + l.ref) { ListRow(l) { scope.launch { playList(app, l) } } } }
    }
}

internal suspend fun playList(app: AppCtx, l: OnlineList) {
    val ts = runCatching { app.platform.online!!.list(l) }.getOrDefault(emptyList())
    if (ts.isNotEmpty()) { app.engine.play(ts, 0); app.nav.nowPlaying = true }
}

@Composable
internal fun ListRow(l: OnlineList, onClick: () -> Unit) {
    val p = LocalPal.current
    var busy by remember { mutableStateOf(false) }
    Tile(Modifier.fillMaxWidth(), pad = 12.dp, onClick = { if (!busy) { busy = true; onClick() } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(l.name, 15, FontWeight.Bold)
                Txt(if (busy) "Loading…" else "Playlist", 12, color = p.sub)
            }
            Ico(Ic.play, 18.dp, p.ink)
        }
    }
}
