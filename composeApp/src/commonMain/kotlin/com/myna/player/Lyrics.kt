package com.myna.player

/** A song's lyrics. Synced lyrics have a start time per line; plain ones have -1. */
class Lyrics(val lines: List<Pair<Long, String>>, val synced: Boolean) {
    /** The line playing at [ms], or -1. */
    fun at(ms: Long): Int = if (!synced) -1 else lines.indexOfLast { it.first <= ms }

    companion object {
        val NONE = Lyrics(emptyList(), false)
        private val TAG = Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")
        private val cache = mutableMapOf<String, Lyrics>()

        /** Reads .lrc text ("[01:02.50] line"); text without times becomes plain lyrics. */
        fun parse(text: String): Lyrics {
            val timed = ArrayList<Pair<Long, String>>()
            for (l in text.lines()) {
                val tags = TAG.findAll(l).toList()
                if (tags.isEmpty()) continue
                val words = l.substring(tags.last().range.last + 1).trim()
                for (m in tags) timed += (m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong()) to words
            }
            if (timed.isNotEmpty()) return Lyrics(timed.sortedBy { it.first }.map { it.first to it.second.ifBlank { "♪" } }, true)
            return Lyrics(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { -1L to it }, false)
        }

        /** Lyrics for [t], downloaded once per song. NONE when there are none or downloads are off. */
        suspend fun forTrack(platform: Platform, t: Track, download: Boolean): Lyrics {
            cache[t.id]?.let { return it }
            if (!download) return NONE
            val title = t.title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifEmpty { t.title }
            val artist = t.artist.substringBefore(',').substringBefore('&').trim()
            val text = runCatching { platform.lyrics(title, artist, t.durationMs / 1000) }.getOrNull()
            if (text == null) return NONE
            return parse(text).also { cache[t.id] = it }
        }
    }
}
