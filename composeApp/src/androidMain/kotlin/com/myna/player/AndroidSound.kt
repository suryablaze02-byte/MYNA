package com.myna.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.ln

/**
 * MYNA's own sound effects, like a Walkman: a space (Studio, Stage, Big Hall, Headset), an equaliser
 * preset and Clear Bass. Both decks share one audio session, so the effects carry through a crossfade.
 */
class AndroidSound(private val ctx: Context) {
    val session: Int = ctx.getSystemService(AudioManager::class.java).generateAudioSessionId()
    private val eq = runCatching { Equalizer(0, session) }.getOrNull()
    private val bass = runCatching { BassBoost(0, session) }.getOrNull()
    private val wide = runCatching { Virtualizer(0, session) }.getOrNull()
    /** The room and width, applied inside each deck by [SpaceProcessor]. */
    val space = Space()
    private val decks = mutableSetOf<ExoPlayer>()

    fun attach(p: ExoPlayer) { decks += p; runCatching { p.setPreferredAudioDevice(preferred) } }

    /** The speaker or headphones picked in "Play on"; null lets Android choose. */
    var preferred: android.media.AudioDeviceInfo? = null
        set(v) { field = v; decks.forEach { runCatching { it.setPreferredAudioDevice(v) } } }
    fun detach(p: ExoPlayer) { decks -= p }

    fun apply(space: Int, preset: Int, clearBass: Int) {
        runCatching {
            eq?.let { e ->
                val curve = CURVES.getOrElse(preset) { CURVES[0] }
                val lo = e.bandLevelRange[0].toInt(); val hi = e.bandLevelRange[1].toInt()
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000.0
                    e.setBandLevel(b.toShort(), (gainAt(curve, hz) * 100).toInt().coerceIn(lo, hi).toShort())
                }
                e.enabled = preset != 0
            }
        }
        runCatching { bass?.apply { if (strengthSupported) setStrength((clearBass * 100).toShort()); enabled = clearBass > 0 } }
        // wet, room size, damping, pre-delay ms, width.
        // Studio: a small, close, bright room. Stage: live, a medium hall in front of you.
        // Big Hall: a large concert hall with a long tail. Headset: very wide, almost no room.
        val v = when (space) {
            1 -> floatArrayOf(0.10f, 0.30f, 0.65f, 4f, 1.15f)
            2 -> floatArrayOf(0.20f, 0.65f, 0.40f, 18f, 1.35f)
            3 -> floatArrayOf(0.30f, 0.90f, 0.20f, 40f, 1.45f)
            4 -> floatArrayOf(0.05f, 0.15f, 0.70f, 0f, 1.75f)
            else -> floatArrayOf(0f, 0.5f, 0.5f, 0f, 1f)
        }
        this.space.apply { room = v[1]; damp = v[2]; preMs = v[3]; width = v[4]; wet = v[0] }
        // Android's own virtualiser only helps in headphones, so only Headset uses it.
        runCatching { wide?.apply { if (space == 4 && strengthSupported) setStrength(1000); enabled = space == 4 } }
    }

    /** The phone's Dolby Atmos app, if it has one. */
    private fun dolby(): Intent? = DOLBY_APPS.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }

    val hasDolby get() = dolby() != null

    /** Opens Dolby Atmos; false when the phone has none. */
    fun openDolby(): Boolean = dolby()?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } ?: false

    companion object {
        private val DOLBY_APPS = listOf(
            "com.motorola.dolby.dolbyui", "com.dolby.daxappui", "com.dolby.dax2appUI", "com.dolby.atmos",
            "com.dolby.ds1appUI", "com.oneplus.sound.tuner", "com.samsung.android.soundassistant",
        )

        /** Equaliser presets as (Hz, dB) points: Flat, Bass, Vocal, Bright, Warm. */
        private val CURVES = listOf(
            listOf(60.0 to 0.0, 14000.0 to 0.0),
            listOf(60.0 to 6.0, 150.0 to 4.5, 400.0 to 1.0, 1000.0 to 0.0, 14000.0 to 0.0),
            listOf(60.0 to -2.0, 230.0 to -1.0, 900.0 to 3.0, 3600.0 to 4.0, 14000.0 to 1.0),
            listOf(60.0 to 0.0, 900.0 to 0.0, 3600.0 to 3.0, 14000.0 to 6.0),
            listOf(60.0 to 3.0, 230.0 to 2.0, 900.0 to 0.0, 3600.0 to -1.0, 14000.0 to -2.5),
        )

        /** The curve's gain at [hz], straight lines between points on a log-frequency scale. */
        private fun gainAt(pts: List<Pair<Double, Double>>, hz: Double): Double {
            if (hz <= pts.first().first) return pts.first().second
            if (hz >= pts.last().first) return pts.last().second
            val i = pts.indexOfLast { it.first <= hz }
            val (f0, g0) = pts[i]; val (f1, g1) = pts[i + 1]
            return g0 + (g1 - g0) * (ln(hz / f0) / ln(f1 / f0))
        }
    }
}
