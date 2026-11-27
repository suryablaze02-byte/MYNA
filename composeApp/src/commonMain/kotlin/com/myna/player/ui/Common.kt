package com.myna.player.ui

import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.myna.player.ArtCache
import com.myna.player.Track
import com.myna.player.online
import com.myna.player.fmtTime

val LocalArt = compositionLocalOf<ArtCache?> { null }

@Composable
fun Txt(
    s: String, size: Int = 14, weight: FontWeight = FontWeight.Normal, color: Color = LocalPal.current.ink,
    lines: Int = 1, modifier: Modifier = Modifier,
) = Text(
    s, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = lines, overflow = TextOverflow.Ellipsis,
    letterSpacing = if (size >= 22) (-0.03).em else 0.em, lineHeight = (size * 1.25).sp,
)

/** A Bento tile: rounded, flat, optional click. */
@Composable
fun Tile(
    modifier: Modifier = Modifier, color: Color = LocalPal.current.tile, radius: Dp = 20.dp, pad: Dp = 14.dp,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(radius)).background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(pad),
        content = content,
    )
}

@Composable
fun Ico(v: ImageVector, size: Dp = 22.dp, tint: Color = LocalPal.current.ink, modifier: Modifier = Modifier) =
    Icon(v, spoken[v], modifier.size(size), tint = tint)

/** What a screen reader says for the icons that are buttons on their own (no text beside them). */
private val spoken by lazy {
    mapOf(
        Ic.play to "Play", Ic.pause to "Pause", Ic.next to "Next", Ic.prev to "Previous", Ic.shuffle to "Shuffle", Ic.repeat to "Repeat",
        Ic.gear to "Settings", Ic.back to "Back", Ic.down to "Close", Ic.x to "Clear", Ic.more to "More options",
        Ic.heart to "Like", Ic.heartOn to "Liked", Ic.refresh to "Refresh",
    )
}

@Composable
fun RoundBtn(v: ImageVector, size: Dp, bg: Color, fg: Color, iconSize: Dp = size * 0.45f, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Ico(v, iconSize, fg)
    }
}

@Composable
fun IconBtn(v: ImageVector, tint: Color = LocalPal.current.ink, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Ico(v, 22.dp, tint)
    }
}

/** Album cover from the file, or a soft tile with the myna mark while there is none. */
@Composable
fun Cover(track: Track?, modifier: Modifier, radius: Dp = 12.dp) {
    val cache = LocalArt.current
    val img by produceState<ImageBitmap?>(null, track?.let { if (it.online) it.id else it.albumKey }) { value = track?.let { cache?.get(it) } }
    val p = LocalPal.current
    Box(modifier.clip(RoundedCornerShape(radius)).background(p.line), contentAlignment = Alignment.Center) {
        val bmp = img
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else MynaMark(32.dp, line = p.sub, accent = p.sub)
    }
}

@Composable
fun BoxScope.CoverFill(track: Track?) = Cover(track, Modifier.matchParentSize(), 0.dp)

@Composable
fun SectionHead(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, 18, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (action != null) Txt(action, 13, FontWeight.Bold, LocalPal.current.sub, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
    }
}

/**
 * A song row as in the mockup. The playing one is a black row with moving bars.
 * With [trail] (the ⋯ menu) the line under the title is "artist · length"; without it the length sits on the right.
 */
@Composable
fun SongRow(
    t: Track, current: Boolean, number: Int? = null, cover: Boolean = number == null,
    trail: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    val p = LocalPal.current
    val fg = if (current) p.onBlock else p.ink
    val sub = if (current) p.onBlock.copy(alpha = 0.75f) else p.sub
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (current) p.block else p.tile)
            .clickable(onClick = onClick).padding(start = 8.dp, end = if (trail != null) 0.dp else 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) Txt(if (number > 0) "$number" else "–", 14, FontWeight.ExtraBold, sub, modifier = Modifier.width(30.dp).padding(start = 6.dp))
        if (cover) { Cover(t, Modifier.size(46.dp), 10.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) { EqBars(fg); Spacer(Modifier.width(6.dp)) }
                Txt(t.title, 15, FontWeight.ExtraBold, fg)
            }
            Txt(if (trail != null) "${t.artist} · ${fmtTime(t.durationMs)}" else t.artist, 13, FontWeight.SemiBold, sub)
        }
        if (trail != null) trail()
        else { Spacer(Modifier.width(8.dp)); Txt(fmtTime(t.durationMs), 12, FontWeight.Bold, sub, modifier = Modifier.padding(end = 6.dp)) }
    }
}

/** Three small bars that bounce while a song plays. */
@Composable
fun EqBars(color: Color) {
    val inf = rememberInfiniteTransition()
    Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(0, 180, 360).forEach { d ->
            val h by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, d, LinearEasing), RepeatMode.Reverse))
            Box(Modifier.width(3.dp).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.block else p.tile).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Txt(text, 14, FontWeight.Bold, if (on) p.onBlock else p.ink) }
}

/** A wide action tile like "Play" / "Shuffle". */
@Composable
fun RowScope.ActionTile(v: ImageVector, text: String, accent: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (accent) Lime else p.tile)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(v, 18.dp, if (accent) OnLime else p.ink)
        Spacer(Modifier.width(8.dp))
        Txt(text, 15, FontWeight.ExtraBold, if (accent) OnLime else p.ink)
    }
}

/** The mockup's slider: a thin rounded track and a round knob. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MynaSlider(
    value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, done: () -> Unit = {},
) {
    val p = LocalPal.current
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Slider(
        value, onChange, modifier, onValueChangeFinished = done, valueRange = range, steps = steps,
        thumb = { Box(Modifier.size(22.dp).clip(CircleShape).background(p.ink)) },
        track = {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.ink))
            }
        },
    )
}
