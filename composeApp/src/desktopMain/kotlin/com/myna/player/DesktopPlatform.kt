package com.myna.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.myna.player.online.WebOnline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.JFileChooser

/** MYNA on Windows: songs from the Music folder, settings in %APPDATA%\MYNA, FFmpeg decks. */
class DesktopPlatform : Platform {
    override val isDesktop = true

    private val home = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MYNA").apply { mkdirs() }
    private val file = File(home, "settings.properties")
    private val props = Properties().apply { if (file.exists()) runCatching { file.reader(Charsets.UTF_8).use { load(it) } } }

    private fun save() = synchronized(props) {
        runCatching {
            val tmp = File(home, "settings.tmp")
            tmp.writer(Charsets.UTF_8).use { props.store(it, null) }
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    override fun getInt(key: String, def: Int) = props.getProperty(key)?.toIntOrNull() ?: def
    override fun putInt(key: String, value: Int) { props.setProperty(key, value.toString()); save() }
    override fun getStr(key: String, def: String): String = props.getProperty(key) ?: def
    override fun putStr(key: String, value: String) { props.setProperty(key, value); save() }

    private val folder get() = File(getStr("folder", File(System.getProperty("user.home"), "Music").path))
    override fun sourceLabel(): String = folder.path

    override fun pickFolder(): Boolean {
        val c = JFileChooser(folder).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose your music folder"
        }
        if (c.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return false
        putStr("folder", c.selectedFile.path)
        return true
    }

    override suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        folder.walkTopDown().onEnter { !it.isHidden }
            .filter { it.isFile && it.extension.lowercase() in AUDIO }
            .map { read(it) }
            .filter { it.durationMs == 0L || it.durationMs > 5000 }
            .toList()
    }

    private fun read(f: File): Track {
        val a = runCatching { AudioFileIO.read(f) }.getOrNull()
        val tag = a?.tag
        fun t(k: FieldKey) = runCatching { tag?.getFirst(k) }.getOrNull()?.trim().orEmpty()
        return Track(
            id = f.path,
            title = t(FieldKey.TITLE).ifEmpty { f.nameWithoutExtension },
            artist = t(FieldKey.ARTIST).ifEmpty { "Unknown artist" },
            album = t(FieldKey.ALBUM).ifEmpty { "Unknown album" },
            durationMs = (a?.audioHeader?.preciseTrackLength ?: 0.0).times(1000).toLong(),
            uri = f.path,
            artRef = f.path,
            trackNo = t(FieldKey.TRACK).substringBefore('/').toIntOrNull() ?: 0,
            addedAt = f.lastModified() / 1000,
            folder = f.parent.orEmpty(),
            year = t(FieldKey.YEAR).take(4).toIntOrNull() ?: 0,
        )
    }

    override suspend fun art(track: Track): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (track.online) {
                val url = track.artRef.ifEmpty { null } ?: WebOnline.officialArt(track.title, track.artist)
                return@runCatching url?.let(WebOnline::bytes)?.let(::decode)
                    ?: track.id.takeIf { it.startsWith("yt:") }?.let { youTubeArt(it.removePrefix("yt:")) }
            }
            val f = File(track.artRef)
            val own = runCatching { AudioFileIO.read(f).tag?.firstArtwork?.binaryData }.getOrNull()
                ?: listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png").map { File(f.parentFile, it) }.firstOrNull { it.isFile }?.readBytes()
            own?.let(::decode) ?: if (getInt("downloadCovers", 1) == 1 && track.artist != "Unknown artist")
                WebOnline.officialArt(track.title, track.artist)?.let(WebOnline::bytes)?.let(::decode) else null
        }.getOrNull()
    }

    private fun decode(b: ByteArray): ImageBitmap = org.jetbrains.skia.Image.makeFromEncoded(b).toComposeImageBitmap()

    /** The YouTube video's own picture, cut to the square in its middle (where the cover sits). */
    private fun youTubeArt(videoId: String): ImageBitmap? {
        val b = WebOnline.bytes(WebOnline.youTubeThumb(videoId))?.let { ImageIO.read(it.inputStream()) } ?: return null
        val side = minOf(b.width, b.height * 3 / 4)
        return b.getSubimage((b.width - side) / 2, (b.height - side) / 2, side, side).toComposeImageBitmap()
    }

    private val space = Space()
    @Volatile private var master = getInt("volume", 100) / 100f
    override fun newDeck(): Deck = FfDeck(space) { master }
    override fun applySound(space: Int, eq: Int, clearBass: Int) = this.space.set(space)
    override fun volume() = master
    override fun setVolume(v: Float) { master = v.coerceIn(0f, 1f); putInt("volume", (master * 100).toInt()) }
    override fun openOutput() { runCatching { java.awt.Desktop.getDesktop().browse(URI("ms-settings:sound")) } }

    override suspend fun lyrics(title: String, artist: String, durationSec: Long) =
        withContext(Dispatchers.IO) { WebOnline.lyrics(title, artist, durationSec) }
    override val online: Online = WebOnline()
    override fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    override fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    override fun nowMs() = System.currentTimeMillis()
    override fun monthOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

    companion object {
        private val logFile = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MYNA/log.txt")

        /** Problems go to %APPDATA%\MYNA\log.txt, for bug reports. */
        fun log(msg: String) = synchronized(logFile) {
            runCatching {
                if (logFile.length() > 1_000_000) logFile.delete()
                logFile.appendText("${Date()}  $msg\n")
            }
            System.err.println("MYNA: $msg")
        }

        private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "aiff", "aif", "wma", "alac")
    }
}
