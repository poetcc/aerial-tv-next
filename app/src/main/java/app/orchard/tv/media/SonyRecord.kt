package app.orchard.tv.media

import java.net.URI
import java.net.URLDecoder

internal enum class PlaybackProvider { IQIYI, TENCENT, YOUKU, MANGO }
internal data class SonyRecord(
    val packageName: String, val title: String, val episodeName: String,
    val videoId: String, val episodeId: String, val channelId: Int,
    val positionMs: Long, val durationMs: Long, val receivedAt: Long, val artworkUrl: String,
    val provider: PlaybackProvider = PlaybackProvider.IQIYI, val resumeUri: String = "", val videoVId: String = "", val cmdInfo: String = "",
) { val mediaIdentity: String get() = if (provider == PlaybackProvider.TENCENT) videoVId.ifBlank { "$videoId/$episodeId" } else episodeId }

/** Fixed per-platform schemas. Source package and launch destination must agree. */
internal fun parseSonyRecord(fields: Map<String, Any?>): SonyRecord? {
    fun text(key: String) = (fields[key] as? String)?.trim().orEmpty()
    fun number(key: String) = (fields[key] as? Number)?.toLong() ?: text(key).toLongOrNull() ?: 0L
    fun id(value: String) = value.takeIf { it.matches(Regex("[A-Za-z0-9_=:-]{1,160}")) }
    fun numeric(value: String) = value.takeIf { it.matches(Regex("[0-9]{1,24}")) }
    val action = text("action")
    var pkg: String; val provider: PlaybackProvider; val factor: Long
    var videoId = text("videoId"); var episodeId = text("episodeId"); var route = ""
    var position = number("currentPosition"); val channel = number("chnId").toInt()
    when {
        action in setOf("com.gitvvideo.sony.action.ACTION_ADD_PLAYRECORD", "com.gitvdemo.video.action.ACTION_ADD_PLAYRECORD") -> {
            provider = PlaybackProvider.IQIYI; factor = 1000; pkg = action.substringBefore(".action.")
            videoId = numeric(videoId) ?: return null; episodeId = numeric(episodeId) ?: return null
            if (channel !in 1..10000) return null
        }
        action in setOf("com.tv.history.add", "com.tv.history.secure.add") -> {
            provider = PlaybackProvider.TENCENT; factor = 1; pkg = text("package_name")
            if (pkg !in setOf("com.ktcp.video", "com.ktcp.tvvideo") || fields["isFromLocal"] == false) return null
            if (action.endsWith("secure.add") && fields["decoded"] != true) return null
            videoId = id(videoId) ?: return null
            if (id(text("videoVId")) == null && numeric(episodeId) == null) return null
        }
        action == "com.tv.ali.history.add" -> {
            provider = PlaybackProvider.YOUKU; factor = 1; pkg = text("srcapp")
            if (pkg !in setOf("com.cibn.tv", "com.cibn.tv.sony")) return null
            val uri = runCatching { URI(text("uri")) }.getOrNull() ?: return null
            val scheme = if (pkg == "com.cibn.tv.sony") "sonycibntv" else "cibntv_yingshi"
            if (uri.scheme != scheme || uri.rawAuthority != "yingshi_detail" || uri.userInfo != null || uri.port != -1 || uri.path !in setOf("", "/")) return null
            val query = runCatching { uri.rawQuery.orEmpty().split('&').associate { part ->
                val kv = part.split('=', limit = 2); URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
            } }.getOrNull() ?: return null
            if (query.keys.any { it !in setOf("from", "id") } || query["from"] != "dhistory" || numeric(query["id"].orEmpty()) == null) return null
            route = uri.toString().takeIf { it.length <= 2048 } ?: return null
            videoId = id(text("showStrId")) ?: query.getValue("id")
            episodeId = id(episodeId) ?: query.getValue("id")
        }
        action == "com.dangbei.history.add" && text("appname") == "com.dangbei.mgtv" -> {
            provider = PlaybackProvider.MANGO; factor = 1; pkg = "com.hunantv.market"
            videoId = numeric(videoId) ?: return null; episodeId = numeric(episodeId) ?: return null
        }
        action == "com.dangbei.humminglauncher.sdk.mango.history.ADD" -> {
            provider = PlaybackProvider.MANGO; factor = 1; pkg = "com.hunantv.market"
            videoId = numeric(text("vid")) ?: return null; episodeId = numeric(text("epid")) ?: return null
            position = number("position")
        }
        else -> return null
    }
    val title = text("videoName").takeIf { it.isNotBlank() && it.length <= 256 } ?: return null
    val timestamp = number("receivedAt").takeIf { it > 0 } ?: return null
    val limit = 604800000L / factor
    val duration = number("duration").coerceIn(0, limit) * factor
    val boundedPosition = (position.coerceIn(0, limit) * factor).let { if (duration > 0) it.coerceAtMost(duration) else it }
    val domains = when (provider) {
        PlaybackProvider.IQIYI -> setOf("gitv.tv", "iqiyipic.com", "qiyipic.com")
        PlaybackProvider.TENCENT -> setOf("qpic.cn", "gtimg.cn", "qq.com")
        PlaybackProvider.YOUKU -> setOf("ykimg.com", "alicdn.com", "aliapp.org", "cibntv.net")
        PlaybackProvider.MANGO -> setOf("mgtv.com", "hunantv.com", "hitv.com")
    }
    val artwork = listOf("videoHImgUrl", "videoHorImgUrl", "horizontalImgUrl", "videoImgUrl").asSequence().map { text(it) }.mapNotNull { value ->
        runCatching {
            val upgraded = if (value.startsWith("http://")) "https://" + value.removePrefix("http://") else value
            val uri = URI(upgraded); val host = uri.host.orEmpty().lowercase()
            upgraded.takeIf { upgraded.length <= 2048 && uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) && domains.any { host.endsWith(".$it") } }
        }.getOrNull()
    }.firstOrNull().orEmpty()
    return SonyRecord(pkg, title, text("episodeName").take(64), videoId, episodeId, channel,
        boundedPosition, duration, timestamp, artwork, provider, route, id(text("videoVId")).orEmpty(), text("cmdInfo").take(512))
}
