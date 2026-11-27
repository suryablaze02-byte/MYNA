package com.myna.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Logo M "Monoline": one stroke draws the myna's head; beak and eye in the accent (100-unit grid). */
fun DrawScope.drawMark(line: Color, accent: Color) {
    val s = size.minDimension / 100f
    translate((size.width - 100 * s) / 2, (size.height - 100 * s) / 2) {
        val head = Path().apply {
            moveTo(22 * s, 84 * s)
            cubicTo(16 * s, 52 * s, 30 * s, 28 * s, 54 * s, 28 * s)
            cubicTo(70 * s, 28 * s, 78 * s, 40 * s, 78 * s, 52 * s)
            cubicTo(78 * s, 68 * s, 64 * s, 78 * s, 48 * s, 76 * s)
        }
        drawPath(head, line, style = Stroke(9 * s, cap = StrokeCap.Round))
        val beak = Path().apply { moveTo(80 * s, 45 * s); lineTo(93 * s, 52 * s); lineTo(80 * s, 59 * s); close() }
        drawPath(beak, accent)
        drawPath(beak, accent, style = Stroke(4 * s, join = StrokeJoin.Round))
        drawCircle(accent, 3.8f * s, Offset(60 * s, 46 * s))
    }
}

@Composable
fun MynaMark(size: Dp, line: Color = LocalPal.current.ink, accent: Color = Lime, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawMark(line, accent) }
}

@Composable
fun Wordmark(size: Dp = 28.dp) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        MynaMark(size)
        Spacer(Modifier.width(6.dp))
        Text("myna", color = p.ink, fontSize = (size.value * 0.85f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em)
        Text(".", color = if (p.dark) Lime else Color(0xFF7FA600), fontSize = (size.value * 0.85f).sp, fontWeight = FontWeight.ExtraBold)
    }
}
