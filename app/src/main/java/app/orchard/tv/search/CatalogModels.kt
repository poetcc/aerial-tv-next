package app.orchard.tv.search

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

enum class CatalogPlatform(val packageName: String) {
    IQIYI("com.gitvvideo.sony"), YOUKU("com.cibn.tv.sony"), TENCENT("com.ktcp.tvvideo"), MANGO("com.hunantv.market");
    companion object {
        fun of(pkg: String): CatalogPlatform? = when (pkg) {
            "com.gitvdemo.video", "com.gitvvideo.sony" -> IQIYI
            "com.cibn.tv", "com.cibn.tv.sony" -> YOUKU
            "com.ktcp.video", "com.ktcp.tvvideo" -> TENCENT
            "com.hunantv.license", "com.hunantv.market" -> MANGO
            else -> null
        }
    }
}

data class CatalogRoute(val platform: CatalogPlatform, val action: String, val uri: String = "", val extras: Map<String, String> = emptyMap())
data class CatalogHit(val id: String, val title: String, val year: String, val status: String, val tag: String, val poster: String, val route: CatalogRoute) {
    val key: String get() = route.platform.name + ":" + id
}
data class CatalogPage(val hits: List<CatalogHit>, val total: Int, val page: Int, val pages: Int, val recommendation: Boolean)

@Suppress("UNCHECKED_CAST")
internal fun Any?.catalogObject(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()
private fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()
private fun Map<String, Any?>.number(key: String, fallback: Int = 0) = text(key).toIntOrNull() ?: fallback
private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').take(40).associate {
    val parts = it.split('=', limit = 2)
    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
}
private fun escaped(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
private val identifier = Regex("[A-Za-z0-9_=-]{1,128}")
private fun uri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
    value.length <= 4096 && it.rawFragment == null && it.rawUserInfo == null && value.none { c -> c.code < 32 }
}

/** Build bounded, package-scoped routes rather than executing server-supplied Intent descriptions. */
internal fun parseCatalogRoute(jump: Map<String, Any?>, source: String): CatalogRoute? {
    val param = jump["param"].catalogObject()
    val platform = CatalogPlatform.of(param.text("packageName")) ?: return null
    if (source.isNotBlank() && CatalogPlatform.of(source) != platform || jump.number("type") != 1) return null
    val action = param["actionObject"].catalogObject()
    val raw = action.text("actionUri")
    return when (platform) {
        CatalogPlatform.IQIYI -> {
            if (action.text("actionString") !in setOf("com.gitvdemo.video.action.ACTION_DETAIL", "com.gitvvideo.sony.action.ACTION_DETAIL")) return null
            val args = (param["args"] as? List<*>).orEmpty().map { it.catalogObject() }
            val info = args.firstOrNull { it.text("key") == "playInfo" && it.text("vtype") == "string" }?.text("value") ?: return null
            if (info.length > 8192 || !info.trim().startsWith('{') || !info.trim().endsWith('}')) return null
            fun hasId(keys: String) = Regex("\"(?:$keys)\"\\s*:\\s*\"?[0-9]{1,30}\"?(?=\\s*[,}])").containsMatchIn(info)
            if (!hasId("videoId|vrsAlbumId") || !hasId("episodeId|vrsTvId") || !hasId("chnId|vrsChnId")) return null
            CatalogRoute(platform, "com.gitvvideo.sony.action.ACTION_DETAIL", extras = mapOf("playInfo" to info))
        }
        CatalogPlatform.TENCENT -> {
            val parsed = uri(raw) ?: return null
            val q = runCatching { query(parsed) }.getOrNull() ?: return null
            if (action.text("actionString") != "com.tencent.qqlivetv.open" || parsed.scheme != "tenvideo2" || !parsed.rawAuthority.isNullOrEmpty() ||
                q["action"] != "1" || q["cover_id"]?.matches(identifier) != true) return null
            CatalogRoute(platform, "com.tencent.qqlivetv.open", raw)
        }
        CatalogPlatform.MANGO -> {
            val parsed = uri(raw) ?: return null
            val q = runCatching { query(parsed) }.getOrNull() ?: return null
            if (action.text("actionString") != "android.intent.action.VIEW" || parsed.scheme != "mgtvapp" || parsed.rawAuthority != "vod" || parsed.path != "/player" ||
                q["clipId"]?.matches(Regex("[0-9]{1,20}")) != true) return null
            CatalogRoute(platform, "android.intent.action.VIEW", raw)
        }
        CatalogPlatform.YOUKU -> {
            val parsed = uri(raw) ?: return null
            val q = runCatching { query(parsed) }.getOrNull() ?: return null
            if (action.text("actionString") != "android.intent.action.VIEW" || parsed.scheme != "ykott" || parsed.rawAuthority != "tv" || parsed.path != "/detail") return null
            val nested = q["url"].orEmpty()
            val id = Regex("^tv/v3/show/detail\\?id=([A-Za-z0-9_=-]{1,128})(?:&.*)?$").matchEntire(nested)?.groupValues?.get(1) ?: return null
            val origin = (q["from_out"] ?: q["from"] ?: q["from_app"]).orEmpty().take(256)
            val target = linkedMapOf("isfull" to (q["fullscreen"] ?: "false"), "id" to id,
                "isBackLastActivity" to (q["fullback"] ?: "false"), "isFarMic" to (q["isFarMic"] ?: "false"),
                "isBackYingHome" to (q["isBackYingHome"] ?: "true"), "from_out" to origin)
            (q["subItem"] ?: q["sub_item"])?.take(256)?.let { target["subItem"] = it }
            CatalogRoute(platform, "android.intent.action.VIEW", "sonycibntv://yingshi_detail?" + target.entries.joinToString("&") { escaped(it.key) + "=" + escaped(it.value) },
                mapOf("detail_uri" to raw, "from_out" to origin))
        }
    }
}

internal fun catalogPoster(value: String): String {
    val upgraded = if (value.startsWith("http://")) "https://" + value.removePrefix("http://") else value
    val parsed = uri(upgraded) ?: return ""
    val domains = setOf("qun7.com", "dangbei.net", "qpic.cn", "gtimg.cn", "gitv.tv", "iqiyipic.com", "ykimg.com", "hitv.com", "mgtv.com", "cibntv.net")
    return upgraded.takeIf { parsed.scheme == "https" && parsed.port in setOf(-1, 443) && domains.any { parsed.host.orEmpty().endsWith(".$it") } }.orEmpty()
}

internal fun parseCatalogPage(root: Map<String, Any?>, recommendation: Boolean): CatalogPage {
    require(root.number("code") == 0) { "Catalogue unavailable" }
    val data = root["data"].catalogObject()
    val filters = (data["filter"] as? List<*>).orEmpty().map { it.catalogObject() }
    val declaredTotal = data["total"]?.toString()?.toIntOrNull() ?: filters.firstOrNull { it.number("id", -1) == 0 }?.number("num")
    val groups = (data[if (recommendation) "items" else "list"] as? List<*>).orEmpty()
    val hits = if (!recommendation && declaredTotal == 0) emptyList() else groups.flatMap { group ->
        (group.catalogObject()["items"] as? List<*>).orEmpty().mapNotNull { row ->
            val item = row.catalogObject()
            val route = parseCatalogRoute(item["jumpConfig"].catalogObject(), item.text("playsource")) ?: return@mapNotNull null
            val title = item.text("title").replace(Regex("<[^>]*>"), "").trim().take(160)
            val id = item.text("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) } ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            val extra = item["extra"].catalogObject()
            CatalogHit(id, title, item.text("year").take(4), extra.text("drm").take(60), extra.text("tag").take(30),
                catalogPoster(item.text("pic")), route)
        }
    }.distinctBy { it.key }.take(80)
    return CatalogPage(hits, (declaredTotal ?: hits.size).coerceIn(0, 1_000_000), data.number("page", 1).coerceAtLeast(1),
        data.number("totalPage", 1).coerceIn(1, 10_000), recommendation)
}
