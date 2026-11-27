package com.myna.player

import kotlin.math.roundToInt
import android.media.AudioManager
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.myna.player.online.AndroidOnline
import com.myna.player.online.OnlineMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Holds the player and library for the whole process, so music keeps going when the screen is closed. */
class MynaApplication : Application() {
    lateinit var platform: AndroidPlatform
    lateinit var engine: Engine
    lateinit var library: Library
    lateinit var controls: MediaControls

    override fun onCreate() {
        super.onCreate()
        val scope = MainScope()
        platform = AndroidPlatform(this)
        val prefs = Prefs(platform)
        engine = Engine(platform, scope, prefs)
        library = Library(platform, scope, prefs)
        engine.onPlayed = { library.history.add(it); library.recent.push(it) }
        platform.applySound(prefs.soundFx.v, prefs.eqPreset.v, prefs.clearBass.v)
        controls = MediaControls(this, engine, platform, scope)
    }
}

class AndroidPlatform(private val ctx: Context) : Platform {
    override val isDesktop = false
    private val prefs = ctx.getSharedPreferences("myna", Context.MODE_PRIVATE)

    override suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.YEAR,
        )
        val out = ArrayList<Track>()
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artist = c.getString(2)?.takeUnless { it == "<unknown>" } ?: "Unknown artist"
                out += Track(
                    id = id.toString(),
                    title = c.getString(1) ?: "Unknown",
                    artist = artist,
                    album = c.getString(3) ?: "Unknown album",
                    durationMs = c.getLong(5),
                    uri = uri.toString(),
                    artRef = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), c.getLong(4)).toString(),
                    trackNo = c.getInt(6) % 1000,
                    addedAt = c.getLong(7),
                    folder = c.getString(8)?.substringBeforeLast('/')?.removePrefix("/storage/emulated/0/").orEmpty(),
                    year = c.getInt(9),
                )
            }
        }
        out
    }

    override suspend fun art(track: Track): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (track.online) {
                val url = track.artRef.ifEmpty { null } ?: AndroidOnline.officialArt(track.title, track.artist)
                return@runCatching (url?.let(AndroidOnline::image) ?: track.id.takeIf { it.startsWith("yt:") }?.let { AndroidOnline.youTubeArt(it.removePrefix("yt:")) })?.asImageBitmap()
            }
            val bmp = if (Build.VERSION.SDK_INT >= 29) {
                ctx.contentResolver.loadThumbnail(Uri.parse(track.uri), Size(512, 512), null)
            } else {
                ctx.contentResolver.openInputStream(Uri.parse(track.artRef))?.use(BitmapFactory::decodeStream)
            }
            // No cover in the file: look the album's official one up, when Settings allow it.
            bmp?.asImageBitmap() ?: if (getInt("downloadCovers", 1) == 1 && track.artist != "Unknown artist")
                AndroidOnline.officialArt(track.title, track.artist)?.let(AndroidOnline::image)?.asImageBitmap() else null
        }.getOrNull()
    }

    private val sound = AndroidSound(ctx)
    override fun newDeck(): Deck = ExoDeck(ctx, sound)
    override fun applySound(space: Int, eq: Int, clearBass: Int) = sound.apply(space, eq, clearBass)
    override val hasDolby get() = sound.hasDolby
    override fun openDolby() = sound.openDolby()
    override suspend fun lyrics(title: String, artist: String, durationSec: Long): String? = withContext(Dispatchers.IO) {
        fun get(url: String): String? = runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("User-Agent", "MYNA music player"); c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
        fun pick(o: org.json.JSONObject): String? = listOf("syncedLyrics", "plainLyrics")
            .firstNotNullOfOrNull { k -> if (o.isNull(k)) null else o.optString(k).ifBlank { null } }
        fun q(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        get("https://lrclib.net/api/get?track_name=${q(title)}&artist_name=${q(artist)}&duration=$durationSec")
            ?.let { runCatching { pick(org.json.JSONObject(it)) }.getOrNull() }
            ?: get("https://lrclib.net/api/search?q=" + q("$title $artist"))?.let { body ->
                runCatching { val a = org.json.JSONArray(body); (0 until a.length()).firstNotNullOfOrNull { pick(a.getJSONObject(it)) } }.getOrNull()
            }
    }
    override fun openOutput() {
        // Android 14+ has a system output switcher; the old settings panel is gone there.
        val shown = if (android.os.Build.VERSION.SDK_INT >= 34) runCatching { android.media.MediaRouter2.getInstance(ctx).showSystemOutputSwitcher() }.getOrDefault(false)
        else runCatching {
            ctx.startActivity(Intent("com.android.settings.panel.action.MEDIA_OUTPUT").putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!shown) runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private val am = ctx.getSystemService(AudioManager::class.java)
    private fun devices() = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { d ->
        val kind = when (d.type) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 0
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27 -> 1 // 26, 27: Bluetooth LE headset / speaker
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 2
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> 3
            else -> return@mapNotNull null
        }
        val name = when (kind) { 0 -> "This phone"; 2 -> "Headphones"; else -> d.productName?.toString().orEmpty().ifBlank { if (kind == 1) "Bluetooth" else "USB audio" } }
        d to OutDevice(d.id, name, kind)
    }.distinctBy { it.second.kind to it.second.name }
    override fun outputs() = devices().map { it.second }
    override fun currentOutput(): Int {
        val all = outputs()
        sound.preferred?.let { p -> all.firstOrNull { it.id == p.id }?.let { return it.id } }
        // Android plays on the newest headphones when nothing is picked.
        return (all.firstOrNull { it.kind == 1 } ?: all.firstOrNull { it.kind != 0 } ?: all.firstOrNull())?.id ?: 0
    }
    override fun setOutput(id: Int) { sound.preferred = devices().firstOrNull { it.second.id == id }?.first }
    override fun volume() = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    override fun setVolume(v: Float) = am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
    override fun clock(ms: Long): String = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(ms))
    override fun getInt(key: String, def: Int) = prefs.getInt(key, def)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    override fun sourceLabel() = "All music on this phone"

    override fun getStr(key: String, def: String) = prefs.getString(key, def) ?: def
    override fun putStr(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override val online: Online = AndroidOnline()
    override fun nowMs() = System.currentTimeMillis()
    override fun monthOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

    override fun onWifi(): Boolean {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }

}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoDeck(ctx: Context, private val sound: AndroidSound) : Deck {
    private val p = ExoPlayer.Builder(ctx, object : androidx.media3.exoplayer.DefaultRenderersFactory(ctx) {
        // MYNA's sound spaces run inside the player, so they work on every phone and speaker.
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(SpaceProcessor(sound.space))).build()
    })
        .setMediaSourceFactory(DefaultMediaSourceFactory(OnlineMedia.dataSourceFactory(ctx)))
        .build().apply {
        // Two players run at once during a fade, so neither may take audio focus from the other.
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        setWakeMode(C.WAKE_MODE_LOCAL)
        // Both decks play into one session, where MYNA's sound effects sit.
        setAudioSessionId(sound.session)
        sound.attach(this)
    }

    override fun load(uri: String, startMs: Long) {
        p.setMediaItem(MediaItem.fromUri(uri), startMs)
        p.prepare()
    }
    override fun play() = p.play()
    override fun pause() = p.pause()
    override fun seek(ms: Long) = p.seekTo(ms)
    override var volume: Float
        get() = p.volume
        set(v) { p.volume = v }
    override val positionMs get() = p.currentPosition
    override val durationMs get() = p.duration.let { if (it == C.TIME_UNSET) -1L else it }
    // A song that can't play (offline, not on YouTube) counts as ended, so the queue moves on.
    override val ended get() = p.playbackState == Player.STATE_ENDED || p.playerError != null
    override fun release() { sound.detach(p); p.release() }
}
