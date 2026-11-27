package com.myna.player.ui

import com.myna.player.device
import androidx.compose.foundation.shape.CircleShape
import com.myna.player.Update
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myna.player.Prefs
import com.myna.player.fmtTime
import kotlin.math.roundToInt

private const val VERSION = "1.2.0"
private val AppCtx.version get() = platform.updater?.version ?: VERSION
private val THEMES = listOf("System", "Light", "Dark")
private val ANIM = listOf("On", "Wi-Fi only", "Off")
private val SPACES = listOf(
    "Off" to "As it was recorded", "Studio" to "A small, close room", "Stage" to "Live, in front of the stage",
    "Big Hall" to "A large concert hall", "Headset" to "Wide surround in headphones",
)
private val EQS = listOf("Flat", "Bass", "Vocal", "Bright", "Warm")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

// ---------------------------------------------------------------- hub

@Composable
fun SettingsScreen(app: AppCtx) {
    val p = LocalPal.current
    val pr = app.library.prefs
    val st by app.engine.state.collectAsState()
    val tracks by app.library.tracks.collectAsState()
    val all by app.library.all.collectAsState()
    val scanning by app.library.scanning.collectAsState()
    val folders = remember(all) { all.map { it.folder }.filter { it.isNotEmpty() }.distinct().size }
    val go = { page: Int -> { app.nav.push(Page.Set(page)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("Settings", null, app, back = !app.wide) }
        item {
            Tile(Modifier.fillMaxWidth(), color = p.soft, pad = 12.dp, onClick = { app.nav.push(Page.Set(5)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                        MynaMark(34.dp, line = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val ready = app.platform.updater?.state?.collectAsState()?.value is Update.Ready
                        Txt("MYNA ${app.version}", 16, FontWeight.ExtraBold)
                        Txt(
                            if (scanning) "Scanning…" else "${tracks.size} song${if (tracks.size == 1) "" else "s"} · ${app.library.likes.tracks.size} liked · ${if (ready) "update ready" else "up to date"}",
                            12, FontWeight.SemiBold, p.sub,
                        )
                    }
                }
            }
        }
        item {
            Group(
                { HubRow(Ic.fade, "Playback", if (st.crossfadeSec == 0) "Crossfade off" else "Crossfade ${st.crossfadeSec} s", go(0)) },
                { HubRow(Ic.sliders, "Sound", soundLabel(pr), go(6)) },
                { HubRow(Ic.folder, "Library", if (!pr.phoneSongs.on) "${device.replaceFirstChar { it.uppercase() }} songs off" else "$folders folder${if (folders == 1) "" else "s"}", go(2)) },
            )
        }
        item {
            Group(
                { HubRow(Ic.image, "Appearance", "${THEMES[pr.theme.v]} · Animated ${ANIM[pr.animCovers.v]}", go(1)) },
                { HubRow(Ic.moon, "Sleep timer", sleepLabel(st.sleepLeftMs, st.sleepEndOfSong, st.sleepEndOfAlbum) ?: "Fade 30 s", go(3)) },
                { HubRow(Ic.heart, "Liked songs", "${app.library.likes.tracks.size}") { app.nav.push(Page.ListP("liked")) } },
            )
        }
        item {
            Group(
                { HubRow(Ic.chart, "Your stats", "${app.library.history.rows.size} plays", go(4)) },
                { HubRow(Ic.info, "About", app.version, go(5)) },
            )
        }
    }
}

private fun soundLabel(pr: Prefs): String {
    val parts = listOfNotNull(
        SPACES[pr.soundFx.v].first.takeIf { pr.soundFx.v != 0 }, EQS[pr.eqPreset.v].takeIf { pr.eqPreset.v != 0 },
        "Clear Bass +${pr.clearBass.v}".takeIf { pr.clearBass.v > 0 },
    )
    return if (parts.isEmpty()) "Off" else parts.joinToString(" · ")
}

private fun sleepLabel(left: Long?, endOfSong: Boolean, endOfAlbum: Boolean = false) = when {
    left != null -> "Stops in ${fmtTime(left)}"
    endOfSong -> "Stops after this song"
    endOfAlbum -> "Stops after this album"
    else -> null
}

/** One white tile holding several setting rows, with thin lines between them. */
@Composable
private fun Group(vararg rows: @Composable () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tile).padding(horizontal = 12.dp, vertical = 2.dp)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            row()
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, name: String, value: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(p.tray), contentAlignment = Alignment.Center) { Ico(icon, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Txt(name, 15, FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        // The value gives way first when space is short, so the name is never cut.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Txt(value, 12, FontWeight.Bold, p.sub) }
        Spacer(Modifier.width(6.dp))
        Ico(Ic.chev, 16.dp, p.sub)
    }
}

// ---------------------------------------------------------------- pages

@Composable
fun SettingsPage(app: AppCtx, which: Int) {
    val title = listOf("Playback", "Appearance", "Library", "Sleep timer", "Your stats", "About", "Sound")[which]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header(title, null, app, back = true) }
        when (which) {
            0 -> playback(app)
            1 -> look(app)
            2 -> librarySet(app)
            3 -> sleep(app)
            4 -> stats(app)
            6 -> sound(app)
            else -> about(app)
        }
    }
}

private fun LazyListScope.playback(app: AppCtx) {
    val pr = app.library.prefs
    item {
        val st by app.engine.state.collectAsState()
        val cf = st.crossfadeSec
        SliderTile("Crossfade", if (cf == 0) "Off" else "$cf s", cf.toFloat(), 12, ticks = true,
            caption = "Volume fade only: the next song fades in while this one fades out. 0 = off.") { app.engine.setCrossfade(it) }
    }
    item {
        val ms = listOf(500, 1000, 2000)
        Group(
            { ToggleRow("Gapless albums", "No fade between songs of the same album", pr.gapless) },
            { ChoiceRow("Skip fade", "When you tap Next or Back", listOf("0.5", "1 s", "2 s"), ms.indexOf(pr.skipFadeMs.v), inline = true) { pr.skipFadeMs.set(ms[it]) } },
            { NavRow("Sound", soundLabel(pr)) { app.nav.push(Page.Set(6)) } },
        )
    }
}

private fun LazyListScope.sound(app: AppCtx) {
    val pr = app.library.prefs
    val apply = { app.platform.applySound(pr.soundFx.v, pr.eqPreset.v, pr.clearBass.v) }
    item { SectionHead("Space") }
    SPACES.withIndex().chunked(2).forEach { row ->
        item {
            val p = LocalPal.current
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (i, s) ->
                    val on = pr.soundFx.v == i
                    Tile(Modifier.weight(1f), color = if (on) Lime else p.tile, pad = 14.dp, onClick = { pr.soundFx.set(i); apply() }) {
                        Txt(s.first, 16, FontWeight.ExtraBold, if (on) OnLime else p.ink)
                        Txt(s.second, 12, FontWeight.SemiBold, if (on) OnLime.copy(alpha = 0.7f) else p.sub, lines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    item { Group({ ChoiceRow("Equaliser", "Preset curve", EQS, pr.eqPreset.v) { pr.eqPreset.set(it); apply() } }) }
    item {
        SliderTile("Clear Bass", if (pr.clearBass.v == 0) "Off" else "+${pr.clearBass.v}", pr.clearBass.v.toFloat(), 10,
            caption = "Deeper bass without muddying the voice.", done = { apply() }) { pr.clearBass.set(it) }
    }
    if (app.platform.hasDolby) item {
        val p = LocalPal.current
        Tile(Modifier.fillMaxWidth(), color = p.block, onClick = { app.platform.openDolby() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Dolby Atmos", 16, FontWeight.ExtraBold, p.onBlock)
                    Txt("Opens the phone's Dolby settings. Set Space to Off for pure Atmos.", 12, FontWeight.SemiBold, p.onBlock.copy(alpha = 0.65f), lines = 3)
                }
                Ico(Ic.chev, 18.dp, p.onBlock)
            }
        }
    }
}

private fun LazyListScope.look(app: AppCtx) {
    val pr = app.library.prefs
    item {
        Group(
            { ChoiceRow("Theme", null, THEMES, pr.theme.v) { pr.theme.set(it) } },
            { ChoiceRow("Animated covers", "The cover slowly moves while a song plays", ANIM, pr.animCovers.v) { pr.animCovers.set(it) } },
        )
    }
    item {
        Group(
            { ToggleRow("Tint by cover", "Now Playing takes the cover's colours", pr.tint) },
            { ToggleRow("Download missing covers", "Official album art for songs with none", pr.downloadCovers) },
            { ToggleRow("Download missing lyrics", "From LRCLIB, synced when it has them", pr.lyricsDl) },
        )
    }
}

private fun LazyListScope.librarySet(app: AppCtx) {
    val pr = app.library.prefs
    val again = { app.library.refilter() }
    item {
        if (pr.phoneSongs.on) Group(
            { ToggleRow("Songs on this $device", "Off: MYNA plays streamed music only", pr.phoneSongs, again) },
            { ToggleRow("Hide short clips", "Leaves out recordings under 30 seconds", pr.hideShort, again) },
            { ToggleRow("Merge duplicates", "The same song in two places shows once", pr.merge, again) },
        ) else Group({ ToggleRow("Songs on this $device", "Off: MYNA plays streamed music only", pr.phoneSongs, again) })
    }
    if (!pr.phoneSongs.on) return
    item { SectionHead("Music folders") }
    item {
        val p = LocalPal.current
        val all by app.library.all.collectAsState()
        val folders = remember(all) { all.groupingBy { it.folder.ifEmpty { "Other" } }.eachCount().entries.sortedByDescending { it.value } }
        if (folders.isEmpty()) Group({ Txt("No music found on this $device yet.", 14, color = p.sub, modifier = Modifier.padding(vertical = 12.dp)) })
        else Group(*folders.map { (f, n) ->
            @Composable {
                val off = f in pr.excluded
                Row(Modifier.fillMaxWidth().clickable { pr.toggleFolder(f); again() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(p.tray), contentAlignment = Alignment.Center) {
                        Ico(Ic.folder, 18.dp, if (off) p.sub else p.ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(f.substringAfterLast('/'), 15, FontWeight.Bold, if (off) p.sub else p.ink)
                        Txt(if (off) "Left out · $n songs" else "$n songs", 12, FontWeight.SemiBold, p.sub)
                    }
                    MynaSwitch(!off) { pr.toggleFolder(f); again() }
                }
            }
        }.toTypedArray())
    }
    item {
        val scanning by app.library.scanning.collectAsState()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (app.platform.isDesktop) ActionTile(Ic.folder, "Change folder", false) { if (app.platform.pickFolder()) app.library.rescan() }
            ActionTile(Ic.refresh, if (scanning) "Scanning…" else "Rescan now", true) { app.library.rescan() }
        }
    }
    app.platform.problem?.let { item { Txt(it, 13, FontWeight.Bold, lines = 4) } }
}

private fun LazyListScope.sleep(app: AppCtx) {
    item {
        val p = LocalPal.current
        val st by app.engine.state.collectAsState()
        val label = sleepLabel(st.sleepLeftMs, st.sleepEndOfSong, st.sleepEndOfAlbum)
        Tile(Modifier.fillMaxWidth(), color = if (label != null) Lime else p.block, pad = 18.dp) {
            val fg = if (label != null) OnLime else p.onBlock
            Ico(Ic.moon, 24.dp, fg)
            Spacer(Modifier.height(8.dp))
            Txt(label ?: "Sleep timer is off", 22, FontWeight.ExtraBold, fg)
            Txt("The music fades out over the last 30 seconds, then stops.", 12, FontWeight.SemiBold, fg.copy(alpha = 0.7f), lines = 2)
            if (label != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(OnLime).clickable { app.engine.sleep(0) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Txt("Turn off", 14, FontWeight.Bold, Lime)
                }
            }
        }
    }
    val opts = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hour", 90 to "1 hr 30", -1 to "End of song", -2 to "End of album")
    opts.chunked(2).forEach { row ->
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (m, name) ->
                    Tile(Modifier.weight(1f), pad = 16.dp, onClick = { app.engine.sleep(m) }) { Txt(name, 16, FontWeight.ExtraBold) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.stats(app: AppCtx) {
    val rows = app.library.history.rows
    item {
        val p = LocalPal.current
        if (rows.isEmpty()) {
            Tile(Modifier.fillMaxWidth(), pad = 20.dp) {
                Txt("Nothing yet", 20, FontWeight.ExtraBold)
                Txt("A song counts once it has played 30 seconds.", 14, color = p.sub, lines = 2)
            }
            return@item
        }
        val minutes = rows.sumOf { it.ms } / 60_000
        val artist = rows.filter { it.artist != "Unknown artist" }.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }
        val song = rows.groupingBy { "${it.title}\n${it.artist}" }.eachCount().maxByOrNull { it.value }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.fillMaxWidth(), color = p.block, pad = 18.dp) {
                Txt("Minutes listened", 12, FontWeight.Bold, p.onBlock.copy(alpha = 0.7f))
                Txt("$minutes", 44, FontWeight.ExtraBold, Lime)
                Txt("${rows.size} plays", 13, FontWeight.Bold, p.onBlock.copy(alpha = 0.7f))
            }
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f).fillMaxHeight()) {
                    Txt("Top artist", 12, FontWeight.Bold, p.sub)
                    Txt(artist?.key ?: "–", 17, FontWeight.ExtraBold, lines = 2)
                    Txt("${artist?.value ?: 0} plays", 12, color = p.sub)
                }
                Tile(Modifier.weight(1f).fillMaxHeight(), color = p.soft) {
                    Txt("Top song", 12, FontWeight.Bold, p.sub)
                    Txt(song?.key?.substringBefore('\n') ?: "–", 17, FontWeight.ExtraBold, lines = 2)
                    Txt("${song?.value ?: 0} plays", 12, color = p.sub)
                }
            }
            MonthChart(app)
        }
    }
}

@Composable
private fun MonthChart(app: AppCtx) {
    val p = LocalPal.current
    val rows = app.library.history.rows
    val now = app.platform.monthOf(app.platform.nowMs())
    val months = (5 downTo 0).map { now - it }
    val mins = months.map { m -> rows.filter { it.at > 0 && app.platform.monthOf(it.at) == m }.sumOf { it.ms } / 60_000 }
    val top = (mins.maxOrNull() ?: 0).coerceAtLeast(1)
    Tile(Modifier.fillMaxWidth()) {
        Txt("By month", 12, FontWeight.Bold, p.sub)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            months.forEachIndexed { i, m ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Txt("${mins[i]}", 11, FontWeight.Bold, p.sub)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(0.75f * mins[i] / top + 0.02f)
                            .clip(RoundedCornerShape(8.dp)).background(if (i == 5) Lime else p.ink),
                    )
                    Spacer(Modifier.height(4.dp))
                    Txt(MONTHS[((m % 12) + 12) % 12], 11, color = p.sub)
                }
            }
        }
    }
}

private fun LazyListScope.about(app: AppCtx) {
    item {
        val p = LocalPal.current
        Tile(Modifier.fillMaxWidth(), pad = 20.dp) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                MynaMark(46.dp, line = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Txt("MYNA ${app.version}", 22, FontWeight.ExtraBold)
            Txt("Your music. Smooth fades. No ads, no accounts.", 14, color = p.sub, lines = 2)
        }
    }
    item { UpdateTile(app) }
    item {
        val p = LocalPal.current
        Tile(Modifier.fillMaxWidth()) {
            Txt("Where the music comes from", 15, FontWeight.Bold)
            Txt("Songs on this $device, and music streamed from public catalogues. Covers are official album art.", 13, color = p.sub, lines = 4)
        }
    }
}

// ---------------------------------------------------------------- pieces

/** Updates from GitHub: a tile in About, and a lime [banner] on Home while one is waiting. */
@Composable
internal fun UpdateTile(app: AppCtx, banner: Boolean = false) {
    val u = app.platform.updater ?: return
    val s by u.state.collectAsState()
    if (banner && (s == Update.Idle || s == Update.Checking || s == Update.UpToDate || s is Update.Failed)) return
    val p = LocalPal.current
    val (title, sub) = when (val x = s) {
        Update.Idle -> "Updates" to "Tap to check for a new version."
        Update.Checking -> "Checking for updates…" to "Looking on GitHub."
        Update.UpToDate -> "MYNA is up to date" to "You have the latest version, ${u.version}."
        is Update.Ready -> "MYNA ${x.version} is ready" to x.notes.joinToString(" · ").ifEmpty { "A new version is out." }
        is Update.NeedsPermission -> "Allow MYNA to install updates" to "Turn on “Allow from this source”, then come back."
        is Update.Downloading -> "Downloading ${x.version}… ${x.percent}%" to "Tap to stop."
        is Update.Installing -> "Installing ${x.version}" to "Confirm on the screen Android shows."
        is Update.Failed -> "The update didn't work" to x.reason
    }
    val action = when (s) {
        is Update.Ready -> "Update"
        is Update.NeedsPermission -> "Allow"
        Update.Idle, Update.UpToDate, is Update.Failed -> "Check"
        else -> null
    }
    val click = {
        when (s) {
            is Update.Ready -> u.install()
            is Update.NeedsPermission -> u.allowInstalls()
            is Update.Downloading -> u.cancel()
            Update.Checking, is Update.Installing -> {}
            else -> u.check()
        }
    }
    val ink = if (banner) OnLime else p.ink
    Tile(Modifier.fillMaxWidth(), color = if (banner) Lime else p.tile, onClick = click) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, 16, FontWeight.ExtraBold, ink)
                Txt(sub, 13, FontWeight.SemiBold, if (banner) OnLime.copy(alpha = 0.7f) else p.sub, lines = 3)
            }
            if (action != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.clip(CircleShape).background(if (banner) OnLime else Lime).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Txt(action, 14, FontWeight.ExtraBold, if (banner) Lime else OnLime)
                }
            }
        }
        (s as? Update.Downloading)?.let { d ->
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(d.percent / 100f).fillMaxHeight().background(ink))
            }
        }
    }
}

/** A setting with a big value on the right and a slider, e.g. Crossfade "6 s". [ticks] adds 0..max numbers. */
@Composable
private fun SliderTile(title: String, value: String, v: Float, max: Int, ticks: Boolean = false, caption: String, done: () -> Unit = {}, set: (Int) -> Unit) {
    val p = LocalPal.current
    Tile(Modifier.fillMaxWidth(), pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, 16, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Txt(value, 30, FontWeight.ExtraBold)
        }
        MynaSlider(v, { set(it.roundToInt()) }, range = 0f..max.toFloat(), steps = max - 1, done = done)
        if (ticks) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..max).forEach { n ->
                val on = n == v.roundToInt()
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) Lime else Color.Transparent).padding(horizontal = 3.dp)) {
                    Txt("$n", 10, FontWeight.ExtraBold, if (on) OnLime else p.sub)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt(caption, 12, FontWeight.SemiBold, p.sub, lines = 3)
    }
}

@Composable
private fun MynaSwitch(on: Boolean, flip: () -> Unit) {
    val p = LocalPal.current
    Switch(
        on, { flip() },
        colors = SwitchDefaults.colors(
            checkedTrackColor = p.ink, checkedThumbColor = Lime, checkedBorderColor = p.ink,
            uncheckedTrackColor = p.line, uncheckedThumbColor = Color.White, uncheckedBorderColor = p.line,
        ),
    )
}

@Composable
private fun RowText(name: String, hint: String?, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Column(modifier) {
        Txt(name, 15, FontWeight.Bold)
        hint?.let { Txt(it, 12, FontWeight.SemiBold, p.sub, lines = 3) }
    }
}

@Composable
private fun ToggleRow(name: String, hint: String, pref: Prefs.Pref, after: () -> Unit = {}) {
    val flip = { pref.set(if (pref.on) 0 else 1); after() }
    Row(Modifier.fillMaxWidth().clickable(onClick = flip).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(name, hint, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        MynaSwitch(pref.on, flip)
    }
}

@Composable
private fun NavRow(name: String, hint: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(name, hint, Modifier.weight(1f))
        Ico(Ic.chev, 16.dp, p.sub)
    }
}

/** A segmented control: beside the name when [inline], else full width underneath. */
@Composable
private fun ChoiceRow(name: String, hint: String?, options: List<String>, selected: Int, inline: Boolean = false, pick: (Int) -> Unit) {
    val p = LocalPal.current
    @Composable
    fun Seg(modifier: Modifier) {
        Row(modifier.clip(RoundedCornerShape(12.dp)).background(p.line).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Box(
                    (if (inline) Modifier else Modifier.weight(1f)).clip(RoundedCornerShape(9.dp)).background(if (on) p.tile else Color.Transparent)
                        .clickable { pick(i) }.padding(horizontal = if (inline) 10.dp else 2.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(o, 12, FontWeight.ExtraBold, if (on) p.ink else p.sub) }
            }
        }
    }
    if (inline) Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(name, hint, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Seg(Modifier)
    } else Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        RowText(name, hint)
        Spacer(Modifier.height(8.dp))
        Seg(Modifier.fillMaxWidth())
    }
}
