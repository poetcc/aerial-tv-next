package app.orchard.tv.media

import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.orchard.tv.R
import app.orchard.tv.guard.RevivalListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class RecentItem(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
    val artwork: String?,
    val mediaId: String = "",
    val canReturn: Boolean = false,
    val resumeInfo: String = "",
)

/** Generic, opt-in MediaSession capture. No app-specific APIs or private app storage. */
object RecentPlayback {
    private lateinit var context: Context
    private val main = Handler(Looper.getMainLooper())
    private val disk = Executors.newSingleThreadExecutor()
    private val controllers = linkedMapOf<String, MediaController>()
    private val callbacks = linkedMapOf<String, MediaController.Callback>()
    // A PendingIntent can outlive the player's process/session, but never survives our own restart.
    private val entries = linkedMapOf<String, Pair<String, PendingIntent>>()
    private val notificationEntries = linkedMapOf<String, Pair<MediaSession.Token, PendingIntent>>()
    private var listening = false
    private var initialized = false
    private var revision = 0
    private var cleared = false
    private val pendingArtwork = linkedMapOf<String, Bitmap>()
    private var saveScheduled = false
    private val save = Runnable {
        saveScheduled = false
        val artwork = pendingArtwork.toMap()
        pendingArtwork.clear()
        writeSnapshot(items, artwork)
    }
    private val manager get() = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component get() = ComponentName(context, RevivalListener::class.java)
    private val prefs get() = context.getSharedPreferences("recent_playback", Context.MODE_PRIVATE)
    private val artDir get() = File(context.filesDir, "recent_playback")

    var items by mutableStateOf<List<RecentItem>>(emptyList())
        private set
    var enabled by mutableStateOf(false)
        private set
    var accessGranted by mutableStateOf(false)
        private set
    var sessionAccessFailed by mutableStateOf(false)
        private set
    var artworkVersion by mutableStateOf(0)
        private set

    fun init(ctx: Context) {
        if (initialized) return
        context = ctx.applicationContext
        initialized = true
        enabled = prefs.getBoolean("enabled", false)
        val initialRevision = revision
        disk.execute {
            val saved = runCatching {
                val array = JSONArray(prefs.getString("items", "[]"))
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    RecentItem(o.getString("package"), o.getString("app"), o.getString("title"),
                        o.optString("subtitle"), o.optLong("position"), o.optLong("duration"),
                        o.optLong("updated"), o.optString("art").takeIf { it.matches(Regex("[0-9a-f]+\\.jpg")) }, o.optString("mediaId"), resumeInfo = o.optString("resumeInfo")).let { it.copy(appLabel = app.orchard.tv.apps.VerifiedAppNames.label(context, it.packageName, it.appLabel)) }
                }.take(100)
            }.getOrDefault(emptyList())
            main.post {
                if (!cleared) {
                    val restored = if (revision == initialRevision) saved else
                        items + saved.filter { restored -> items.none { historyKey(it) == historyKey(restored) } }
                    items = normalisePlaybackHistory(restored)
                    updateAvailability()
                    if (items.size < restored.size) {
                        Log.i("AerialPlayback", "Consolidated session/history duplicates: " + (restored.size - items.size))
                        writeSnapshot(items, emptyMap())
                    }
                }
            }
        }
    }

    fun refresh(ctx: Context) {
        init(ctx)
        accessGranted = if (Build.VERSION.SDK_INT >= 27) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
        }
        SonyPlayback.refresh(context)
        sessionAccessFailed = false
        if (!enabled || !accessGranted) { disconnect(); return }
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
                listening = true
            }
            updateSessions(manager.getActiveSessions(component))
            RevivalListener.refreshMediaEntries()
            Log.i("AerialPlayback", diagnostics())
        } catch (e: SecurityException) {
            // Notification approval and usable media access are separate states.
            sessionAccessFailed = true
            Log.w("AerialPlayback", "Media access rejected after listener approval", e)
            disconnect()
        }
    }

    fun diagnostics(): String = "recording=$enabled listenerApproved=$accessGranted " +
        "mediaAccessFailed=$sessionAccessFailed observing=$listening " +
        "sessions=${controllers.size} records=${items.size} sonyRecords=${items.count { it.resumeInfo.isNotBlank() }}"

    fun setRecordingEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("enabled", value).apply()
        refresh(context)
    }

    fun clear() {
        // Stop recording too, so an active player cannot immediately recreate cleared history.
        setRecordingEnabled(false)
        revision++
        cleared = true
        SonyPlayback.clear(context)
        prefs.edit().putLong("sony_cleared_at", System.currentTimeMillis()).apply()
        main.removeCallbacks(save)
        saveScheduled = false
        pendingArtwork.clear()
        items = emptyList()
        prefs.edit().remove("items").apply()
        disk.execute {
            prefs.edit().remove("items").commit()
            artDir.listFiles()?.forEach { it.delete() }
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { updateSessions(it.orEmpty()) }

    private fun updateSessions(sessions: List<MediaController>) {
        if (!enabled || !accessGranted) return
        val incoming = sessions.filter { it.packageName != context.packageName }.associateBy { it.packageName }
        controllers.keys.toList().forEach { pkg ->
            if (incoming[pkg]?.sessionToken != controllers[pkg]?.sessionToken) removeController(pkg)
        }
        incoming.forEach { (pkg, controller) ->
            if (pkg !in controllers) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = capture(controller)
                    override fun onPlaybackStateChanged(state: PlaybackState?) = capture(controller)
                    override fun onSessionDestroyed() {
                        if (controllers[pkg]?.sessionToken == controller.sessionToken) removeController(pkg)
                    }
                }
                controllers[pkg] = controller
                callbacks[pkg] = callback
                controller.registerCallback(callback, main)
            }
            capture(controllers.getValue(pkg))
        }
        updateAvailability()
    }

    private fun removeController(pkg: String) {
        callbacks.remove(pkg)?.let { controllers[pkg]?.unregisterCallback(it) }
        controllers.remove(pkg)
        updateAvailability()
    }

    fun disconnect() {
        if (!initialized) return
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        listening = false
        controllers.keys.toList().forEach(::removeController)
        entries.clear()
        notificationEntries.clear()
        updateAvailability()
    }

    private fun updateAvailability() {
        items = items.map { item -> item.copy(canReturn = SonyPlayback.resumeIntent(item) != null || entries[item.packageName]?.first == contentKey(item)) }
    }

    private fun contentKey(item: RecentItem) = "${item.mediaId}\n${item.title}\n${item.subtitle}"

    fun acceptMediaEntry(pkg: String, token: MediaSession.Token, entry: PendingIntent) {
        if (!initialized || !enabled || !accessGranted) return
        val controller = controllers[pkg] ?: return
        if (controller.sessionToken != token) return
        notificationEntries[pkg] = token to entry
        capture(controller)
    }

    private fun capture(controller: MediaController) {
        if (!enabled || !accessGranted) return
        if (controllers[controller.packageName]?.sessionToken != controller.sessionToken) return
        val metadata = controller.metadata ?: return
        val description = metadata.description
        val title = description.title?.toString()?.trim().orEmpty()
        if (title.isBlank()) return
        val pkg = controller.packageName
        // Only launchable apps can be useful on the shelf.
        if (launchIntent(pkg) == null) return
        val old = items.firstOrNull { it.packageName == pkg }
        if (items.any { it.packageName == pkg && it.resumeInfo.isNotBlank() }) return
        val state = controller.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        // A paused/stopped session may be stale; do not let discovery reorder it above new playback.
        if (old == null && !playing && state?.state != PlaybackState.STATE_PAUSED) return
        val subtitle = description.subtitle?.toString().orEmpty()
        val mediaId = description.mediaId.orEmpty()
        val sameTitle = old?.title == title && old.subtitle == subtitle && old.mediaId == mediaId
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            ?: if (sameTitle) old!!.duration else 0
        val position = estimatedPosition(
            state?.position?.takeIf { it >= 0 } ?: if (sameTitle) old!!.position else 0,
            duration, playing, state?.playbackSpeed ?: 0f,
            if (state != null && state.lastPositionUpdateTime > 0) SystemClock.elapsedRealtime() - state.lastPositionUpdateTime else 0,
        )
        val label = app.orchard.tv.apps.VerifiedAppNames.label(context, pkg)
        val bitmap = description.iconBitmap
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val artName = bitmap?.let { artworkName(pkg, "$mediaId\n$title\n$subtitle") } ?: old?.artwork?.takeIf { sameTitle }
        val identity = "$mediaId\n$title\n$subtitle"
        if (entries[pkg]?.first != identity) entries.remove(pkg)
        val entry = controller.sessionActivity
            ?: notificationEntries[pkg]?.takeIf { it.first == controller.sessionToken }?.second
        entry?.let { entries[pkg] = identity to it }
        val item = RecentItem(pkg, label, title, subtitle, position, duration,
            if (playing || !sameTitle) System.currentTimeMillis() else old?.updatedAt ?: System.currentTimeMillis(), artName, mediaId, entries[pkg] != null)
        if (item == old) return
        revision++
        items = mergeRecent(items, item)
        entries.keys.retainAll(items.map { it.packageName }.toSet())
        notificationEntries.keys.retainAll(items.map { it.packageName }.toSet())
        if (bitmap != null && artName != null) pendingArtwork[artName] = bitmap
        pendingArtwork.keys.retainAll(items.mapNotNull { it.artwork }.toSet())
        if (!saveScheduled) {
            saveScheduled = true
            main.postDelayed(save, 750)
        }
    }

    private fun writeSnapshot(snapshot: List<RecentItem>, artwork: Map<String, Bitmap>) {
        // Metadata and progress stay local. PendingIntents/session tokens are deliberately not serialized.
        disk.execute {
            artDir.mkdirs()
            artwork.forEach { (artName, bitmap) -> if (!File(artDir, artName).exists()) runCatching {
                val ratio = minOf(1f, 480f / maxOf(bitmap.width, bitmap.height))
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
                File(artDir, artName).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                main.post { artworkVersion++ }
            } }
            val json = JSONArray()
            snapshot.forEach { r -> json.put(JSONObject().put("package", r.packageName).put("app", r.appLabel)
                .put("title", r.title).put("subtitle", r.subtitle).put("position", r.position)
                .put("duration", r.duration).put("updated", r.updatedAt).put("art", r.artwork.orEmpty()).put("mediaId", r.mediaId).put("resumeInfo", r.resumeInfo)) }
            prefs.edit().putString("items", json.toString()).commit()
            val keep = snapshot.mapNotNull { it.artwork }.toSet()
            artDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    internal fun acceptSonyRecord(item: RecentItem): Boolean {
        if (!enabled || item.updatedAt <= prefs.getLong("sony_cleared_at", 0)) return false
        val old = items.firstOrNull { historyKey(it) == historyKey(item) }
        // An OEM record can upgrade the same programme captured earlier via MediaSession.
        if (old != null && !shouldAcceptSonyRecord(old, item)) return false
        if (launchIntent(item.packageName) == null) return false
        revision++
        items = mergeRecent(items, item)
        main.removeCallbacks(save)
        saveScheduled = false
        writeSnapshot(items, pendingArtwork.toMap())
        pendingArtwork.clear()
        return true
    }

    internal fun acceptSonyArtwork(item: RecentItem, name: String, bitmap: Bitmap) {
        if (items.none { it.packageName == item.packageName && it.artwork == name }) return
        writeSnapshot(items, mapOf(name to bitmap))
    }

    private fun artworkName(pkg: String, title: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("$pkg\n$title".toByteArray())
            .joinToString("") { "%02x".format(it) } + ".jpg"

    fun artworkFile(item: RecentItem): File? = item.artwork?.let { File(artDir, it) }

    private fun launchIntent(pkg: String): Intent? = context.packageManager.getLeanbackLaunchIntentForPackage(pkg)
        ?: context.packageManager.getLaunchIntentForPackage(pkg)

    /** Called only from an explicit remote click in the foreground activity. */
    fun open(activity: android.app.Activity, item: RecentItem) {
        SonyPlayback.resumeIntent(item)?.let { resume ->
            try { activity.startActivity(resume); return } catch (_: Exception) { }
        }
        val entry = entries[item.packageName]?.takeIf { it.first == contentKey(item) }?.second
        if (entry != null) {
            try {
                entry.send()
                return
            } catch (_: PendingIntent.CanceledException) {
                entries.remove(item.packageName)
                items = items.map { if (it.packageName == item.packageName) it.copy(canReturn = false) else it }
            } catch (_: SecurityException) {
                entries.remove(item.packageName)
                updateAvailability()
            }
        }
        try {
            val intent = launchIntent(item.packageName) ?: throw IllegalStateException()
            activity.startActivity(intent)
            Toast.makeText(activity, R.string.recent_opened_app, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
