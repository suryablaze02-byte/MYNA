package com.myna.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from MYNA's GitHub releases: finds the latest release and its APK, downloads it
 * straight into Android's package installer and lets the user confirm. Ported from FireTheDJ.
 * Only copies signed with the same key can update this way, as Android requires.
 */
class AndroidUpdates(context: Context) : Updater {
    private class Release(val version: String, val notes: List<String>, val apkUrl: String, val size: Long)

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("updates", 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<Update>(Update.Idle)
    override val state: StateFlow<Update> = _state
    private var release: Release? = null
    private var download: Job? = null

    override val version: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "1.0"

    init {
        current = this
        // On launch: at most once a day.
        if (System.currentTimeMillis() - prefs.getLong("lastChecked", 0) > DAY_MS) check()
        else prefs.getString("latest", null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }
            ?.takeIf { newer(it.version, version) }?.let { release = it; _state.value = Update.Ready(it.version, it.notes) }
    }

    override fun check() {
        if (_state.value is Update.Downloading || _state.value is Update.Installing) return
        _state.value = Update.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
            prefs.edit().putLong("lastChecked", System.currentTimeMillis()).apply()
            _state.value = result.fold(
                onSuccess = { (r, json) ->
                    prefs.edit().putString("latest", json).apply()
                    release = r
                    when {
                        r == null -> Update.Failed("The latest release has no app to download.")
                        newer(r.version, version) -> Update.Ready(r.version, r.notes)
                        else -> Update.UpToDate
                    }
                },
                onFailure = { Log.w("MYNA", "update check failed", it); Update.Failed("Couldn't reach GitHub. Check the connection and try again.") },
            )
        }
    }

    private fun canInstall() = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

    /** Carries on once the install permission is on (called when MYNA comes back to the front). */
    fun resume() { if (_state.value is Update.NeedsPermission && canInstall()) install() }

    override fun allowInstalls() {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
        runCatching { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun cancel() {
        download?.cancel()
        release?.let { _state.value = Update.Ready(it.version, it.notes) }
    }

    override fun install() {
        val r = release ?: return
        if (!canInstall()) { _state.value = Update.NeedsPermission(r.version); return }
        download?.cancel()
        _state.value = Update.Downloading(r.version, 0)
        download = scope.launch {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                if (r.size > 0) setSize(r.size)
            }
            val id = installer.createSession(params)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    installer.openSession(id).use { session ->
                        val conn = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                        }
                        conn.inputStream.use { input ->
                            session.openWrite("MYNA.apk", 0, r.size.takeIf { it > 0 } ?: -1).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var read = 0L
                                while (isActive) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    read += n
                                    if (r.size > 0) _state.value = Update.Downloading(r.version, (read * 100 / r.size).toInt())
                                }
                                session.fsync(out)
                            }
                        }
                        if (!isActive) return@runCatching false
                        // Mutable: the installer adds the status and, if needed, its confirm screen.
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                        session.commit(PendingIntent.getBroadcast(app, id, Intent(app, UpdateInstallReceiver::class.java), flags).intentSender)
                        true
                    }
                }.onFailure { Log.w("MYNA", "update download failed", it) }.getOrDefault(false)
            }
            if (ok) _state.value = Update.Installing(r.version)
            else {
                runCatching { installer.abandonSession(id) }
                if (_state.value is Update.Downloading) _state.value = Update.Failed("The download didn't finish. Try again.")
            }
        }
    }

    /** The installer's verdict, from [UpdateInstallReceiver]. */
    internal fun onInstallResult(status: Int, message: String?) {
        val r = release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> Update.UpToDate
            PackageInstaller.STATUS_FAILURE_ABORTED -> r?.let { Update.Ready(it.version, it.notes) } ?: Update.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Update.Failed("This copy of MYNA wasn't installed from GitHub, so Android won't let it update itself. Uninstall it, then install the latest version from GitHub.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> Update.Failed("Not enough storage to install the update.")
            else -> Update.Failed("Android couldn't install the update${message?.let { ": $it" } ?: "."}")
        }
    }

    private fun fetchLatest(): Pair<Release?, String> {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return parse(JSONObject(body)) to body
    }

    private fun parse(json: JSONObject): Release? {
        val v = json.getString("tag_name").removePrefix("v")
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").startsWith("MYNA") && it.getString("name").endsWith(".apk") } ?: return null
        return Release(v, notes(json.optString("body")), apk.getString("browser_download_url"), apk.optLong("size"))
    }

    companion object {
        private const val REPO = "suryablaze02-byte/MYNA"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        internal var current: AndroidUpdates? = null

        /** "What's new": the release notes' bullet points, without their Markdown. */
        internal fun notes(body: String): List<String> = body.lines().map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).replace("**", "").replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").substringBefore(": ").trim() }
            .filter { it.isNotBlank() }.take(4)

        /** Whether version [a] is newer than [b] ("1.10.0" > "1.9.2"). */
        internal fun newer(a: String, b: String): Boolean {
            val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}

/** Hears back from Android's installer: shows its "Update this app?" screen, or passes the result on. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION") (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        AndroidUpdates.current?.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
