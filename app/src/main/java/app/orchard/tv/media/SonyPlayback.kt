package app.orchard.tv.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Own compatibility implementation. No Sony/Dangbei binary code is included in Aerial. */
internal object SonyPlayback {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val bridgePackage = "com.dangbei.TVHomeLauncher"
    private const val permission = "app.orchard.tv.permission.PLAYBACK_BRIDGE"
    private fun trusted(context: Context): Boolean =
        context.packageManager.checkSignatures(context.packageName, bridgePackage) == PackageManager.SIGNATURE_MATCH

    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
            runCatching { appContext?.unbindService(this) }
            main.postDelayed({ if (RecentPlayback.enabled) appContext?.let { refresh(it) } }, 1500)
        }
        override fun onBindingDied(name: ComponentName) {
            bound = false
            runCatching { appContext?.unbindService(this) }
            main.postDelayed({ if (RecentPlayback.enabled) appContext?.let { refresh(it) } }, 1500)
        }
    }
    private var appContext: Context? = null

    fun refresh(context: Context) {
        if (!trusted(context)) return
        val recording = RecentPlayback.enabled
        appContext = context.applicationContext
        if (recording && !bound) bound = runCatching {
            context.applicationContext.bindService(Intent().setComponent(ComponentName(bridgePackage,
                "app.orchard.sonybridge.ProbeService")), connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }.getOrDefault(false)
        if (!recording && bound) {
            runCatching { context.applicationContext.unbindService(connection) }
            bound = false
        }
        worker.execute {
            runCatching { context.contentResolver.call(Uri.parse("content://app.orchard.sonybridge.records"), "configure", null,
                android.os.Bundle().apply { putBoolean("enabled", recording) }) }
            if (!recording) return@execute
            val raw = runCatching {
                context.contentResolver.query(Uri.parse("content://app.orchard.sonybridge.records/latest"), null, null, null, null)?.use {
                    buildList { while (it.moveToNext()) add(it.getString(0)) }
                }
            }.getOrNull()
            raw?.forEach { record -> main.post { accept(context, record) } }
        }
    }

    fun clear(context: Context) {
        if (!trusted(context)) return
        worker.execute { runCatching { context.contentResolver.call(Uri.parse("content://app.orchard.sonybridge.records"), "clear", null, null) } }
    }

    fun accept(context: Context, raw: String) {
        if (!RecentPlayback.enabled || !trusted(context) || raw.length > 32768) return
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val record = parseSonyRecord(data.keys().asSequence().associateWith { data.opt(it) }) ?: return
        val resume = raw
        val art = record.artworkUrl.takeIf { it.isNotBlank() }?.let {
            java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } + ".jpg"
        }
        val label = app.orchard.tv.apps.VerifiedAppNames.label(context, record.packageName)
        val subtitle = record.episodeName.takeIf { it.isNotBlank() }?.let { if (it.all(Char::isDigit)) context.getString(app.orchard.tv.R.string.recent_episode, it) else it }.orEmpty()
        val item = RecentItem(record.packageName, label, record.title, subtitle, record.positionMs, record.durationMs,
            record.receivedAt, art, record.mediaIdentity, true, resume)
        val imported = RecentPlayback.acceptSonyRecord(item)
        if (!imported && RecentPlayback.items.none { it.packageName == item.packageName && it.mediaId == item.mediaId && it.updatedAt == item.updatedAt }) return
        if (imported) Log.i("AerialPlayback", "Imported OEM playback record; package=" + record.packageName)
        if (art != null && RecentPlayback.artworkFile(item)?.exists() != true) worker.execute {
            val bitmap = runCatching {
                // Only known platform image hosts are accepted; credentials and redirects are excluded.
                val url = URL(record.artworkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
                try {
                    if (connection.responseCode != 200) return@runCatching null
                    val bytes = connection.inputStream.use { it.readBytesBounded(2 * 1024 * 1024) } ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return@runCatching null
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 960 || bounds.outHeight / inSampleSize > 960) inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } finally { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) main.post { RecentPlayback.acceptSonyArtwork(item, art, bitmap) }
        }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer); if (count < 0) break
            if (out.size() + count > limit) return null
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    fun resumeIntent(item: RecentItem): Intent? {
        if (item.resumeInfo.isBlank()) return null
        val data = runCatching { JSONObject(item.resumeInfo) }.getOrNull() ?: return null
        val record = parseSonyRecord(data.keys().asSequence().associateWith { data.opt(it) }) ?: return null
        if (record.packageName != item.packageName || record.mediaIdentity != item.mediaId) return null
        return when (record.provider) {
            PlaybackProvider.IQIYI -> {
                val playInfo = JSONObject().put("playType", "history").put("videoId", record.videoId)
                    .put("episodeId", record.episodeId).put("history", record.positionMs).put("customer", "iqiyi").put("chnId", record.channelId)
                Intent(record.packageName + ".action.ACTION_PLAYVIDEO").setPackage(record.packageName)
                    .addCategory(Intent.CATEGORY_DEFAULT).putExtra("playInfo", playInfo.toString())
            }
            PlaybackProvider.TENCENT -> {
                val uri = Uri.Builder().scheme("tenvideo2").authority("").appendQueryParameter("action", "7")
                    .appendQueryParameter("cover_id", record.videoId)
                if (record.videoVId.isNotBlank()) uri.appendQueryParameter("video_id", record.videoVId)
                else record.episodeId.toIntOrNull()?.let { uri.appendQueryParameter("episode_idx", (it - 1).coerceAtLeast(0).toString()) }
                Intent("com.tencent.qqlivetv.open", uri.build()).setPackage(record.packageName)
                    .addCategory(Intent.CATEGORY_DEFAULT).putExtra("cmdInfo", record.cmdInfo)
            }
            PlaybackProvider.YOUKU -> Intent(Intent.ACTION_VIEW, Uri.parse(record.resumeUri)).setPackage(record.packageName)
            PlaybackProvider.MANGO -> {
                val uri = Uri.Builder().scheme("mgtvapp").authority("vod").path("/player")
                    .appendQueryParameter("partId", record.episodeId).appendQueryParameter("clipId", record.videoId)
                    .appendQueryParameter("playTime", (record.positionMs / 1000).toString()).appendQueryParameter("fullScreen", "1")
                Intent(Intent.ACTION_VIEW, uri.build()).setPackage(record.packageName)
            }
        }
    }
}

class SonyPlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.orchard.tv.SONY_PLAYBACK") return
        RecentPlayback.init(context)
        intent.getStringExtra("record")?.let { SonyPlayback.accept(context, it) }
    }
}
