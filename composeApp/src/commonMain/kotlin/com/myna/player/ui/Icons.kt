package com.myna.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The same icon set as the mockups (24-unit grid). Filled for transport, 2-unit strokes for the rest. */
object Ic {
    val play = fill("M7 4.5v15l13-7.5z")
    val pause = fill("M6 4.5h4v15H6zM14 4.5h4v15h-4z")
    val next = fill("M5 5v14l10-7zM16 5h3v14h-3z")
    val prev = fill("M19 5v14L9 12zM5 5h3v14H5z")
    val shuffle = stroke("M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5")
    val repeat = stroke("m17 2 4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")
    val gear = stroke("M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0zM12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1")
    val search = stroke("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0zm2 9-3.5-3.5")
    val home = stroke("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val lib = stroke("M4 4v16M9 4v16M14 4l6 16")
    val queue = stroke("M3 6h13M3 12h13M3 18h9M18 14v7l4-3.5z")
    val back = stroke("m15 18-6-6 6-6")
    val chev = stroke("m9 18 6-6-6-6")
    val down = stroke("m6 9 6 6 6-6")
    val folder = stroke("M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val refresh = stroke("M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5")
    val x = stroke("M18 6 6 18M6 6l12 12")
    val compass = stroke("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0zM15.5 8.5l-2 5-5 2 2-5z")
    val heart = stroke("M12 20.5s-8-4.9-8-10.6A4.4 4.4 0 0 1 12 7.3a4.4 4.4 0 0 1 8 2.6c0 5.7-8 10.6-8 10.6z")
    val heartOn = fill("M12 20.5s-8-4.9-8-10.6A4.4 4.4 0 0 1 12 7.3a4.4 4.4 0 0 1 8 2.6c0 5.7-8 10.6-8 10.6z")
    val image = stroke("M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM21 15l-5-5L5 21M10 8.5a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0z")
    val lyr = stroke("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2zM8 9h8M8 13h5")
    val cast = stroke("M2 16.1A5 5 0 0 1 5.9 20M2 12.05A9 9 0 0 1 9.95 20M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6")
    val more = fill("M5 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2zM12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2zM19 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z")
    val plus = stroke("M12 5v14M5 12h14")
    val sparkle = stroke("M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6")
    val clock = stroke("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0zM12 7v5l3 2")
    val phone = stroke("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2")
    val bt = stroke("M7 7l10 10-5 5V2l5 5L7 17")
    val headphones = stroke("M3 18v-6a9 9 0 0 1 18 0v6M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z")
    val vol = stroke("M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14")
    val check = stroke("M20 6 9 17l-5-5")
    val moon = stroke("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val chart = stroke("M4 20V10M10 20V4M16 20v-7M22 20H2")
    val palette = stroke("M12 3a9 9 0 1 0 0 18c1 0 1.5-.8 1.5-1.6 0-1.1-.9-1.4-.9-2.4 0-.8.6-1.5 1.6-1.5H17a4 4 0 0 0 4-4c0-4.7-4-8.5-9-8.5zM7.5 11.5h.01M10 7.5h.01M15 7.5h.01")
    val info = stroke("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0zM12 11v6M12 7.5h.01")
    val sliders = stroke("M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0M16 4v4M10 10v4M18 16v4")
    val fade = stroke("M3 18c6 0 6-12 12-12h6M3 6h6c6 0 6 12 12 12")

    private fun builder() = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    private fun fill(d: String) = builder().addPath(addPathNodes(d), fill = SolidColor(Color.Black)).build()
    private fun stroke(d: String) = builder().addPath(
        addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}
