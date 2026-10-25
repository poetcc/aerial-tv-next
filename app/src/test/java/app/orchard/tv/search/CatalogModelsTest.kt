package app.orchard.tv.search
import org.junit.Assert.*
import org.junit.Test

class CatalogModelsTest {
    private val samples: List<Map<String, Any?>> = listOf(
        mapOf("id" to 1569067, "title" to "三体", "pic" to "http://zmimg-sony.qun7.com/asset_lpicv/2023-08-10/d6/64d476a8dbd512.92653129.jpg", "year" to 2023, "extra" to mapOf("score" to 9.1, "tag" to "VIP", "tagColor" to "#896644", "drm" to "30集全"), "jumpConfig" to mapOf("type" to 1, "link" to "", "param" to mapOf("startType" to 0, "intentType" to 2, "actionObject" to mapOf("actionString" to "com.tencent.qqlivetv.open", "actionUri" to "tenvideo2://?pull_from=101000&action=1&cover_id=mzc002007knmh3g&stay_flag=0&home_flag=0&is_child_mode=1"), "packageName" to "com.ktcp.tvvideo", "category" to emptyList<Any?>(), "flags" to 0, "args" to emptyList<Any?>()), "appInfo" to mapOf("kw" to 7668, "type" to "appid")), "playsource" to "com.ktcp.tvvideo"),
        mapOf("id" to 3682660, "title" to "三体与刘慈欣", "pic" to "http://zmimg-sony.qun7.com/asset_lpicv/2024-06-19/d0/6671eea0911a68.30800560.jpg", "year" to 2015, "extra" to mapOf("score" to 7, "tag" to "酷喵", "tagColor" to "#555555", "drm" to "1集全"), "jumpConfig" to mapOf("type" to 1, "link" to "", "param" to mapOf("startType" to 0, "intentType" to 2, "actionObject" to mapOf("actionString" to "android.intent.action.VIEW", "actionUri" to "ykott://tv/detail?url=tv/v3/show/detail?id=eadd7cd556d64ef9b08a&isBackYingHome=false&fullscreen=false&from=dangbei&from_out=5baf33bf030f422d_dsearch___jiemu_eadd7cd556d64ef9b08a_newtv"), "packageName" to "com.cibn.tv", "category" to emptyList<Any?>(), "flags" to 0, "args" to emptyList<Any?>()), "appInfo" to mapOf("kw" to 7806, "type" to "appid")), "playsource" to "com.cibn.tv"),
        mapOf("id" to 3314658, "title" to "三体原著解读——从文明的角度理解三体", "pic" to "http://zmimg-sony.qun7.com/asset_lpicv/2023-01-16/cb/63c4afec8f3ff3.21129420.jpg", "year" to 2024, "extra" to mapOf("tag" to "奇异果", "tagColor" to "#555555", "drm" to "59集全"), "jumpConfig" to mapOf("type" to 1, "link" to "", "param" to mapOf("startType" to 0, "intentType" to 1, "actionObject" to mapOf("actionString" to "com.gitvdemo.video.action.ACTION_DETAIL"), "packageName" to "com.gitvdemo.video", "category" to emptyList<Any?>(), "flags" to 268468224, "args" to listOf(mapOf("key" to "playInfo", "value" to "{\"chnId\":12,\"videoId\":4987566428917701,\"episodeId\":1070590596073700,\"customer\":\"dangbei\"}", "vtype" to "string"))), "appInfo" to mapOf("kw" to 7822, "type" to "appid")), "playsource" to "com.gitvdemo.video"),
        mapOf("id" to 3627881, "title" to "乘风踏浪", "pic" to "http://zmimg-sony.qun7.com/asset_lpicv/2024-03-27/fb/6603d26231ce06.66278982.jpg", "year" to 2024, "extra" to mapOf("score" to 8, "tag" to "芒果", "tagColor" to "#555555", "drm" to "37集全"), "jumpConfig" to mapOf("type" to 1, "link" to "", "param" to mapOf("startType" to 0, "intentType" to 2, "actionObject" to mapOf("actionString" to "android.intent.action.VIEW", "actionUri" to "mgtvapp://vod/player?clipId=637526&from=com.hunantv.market&actionSourceId=SONY&backToHomePage=0"), "packageName" to "com.hunantv.market", "category" to emptyList<Any?>(), "flags" to 0, "args" to emptyList<Any?>())), "playsource" to "com.hunantv.market"),
    )
    private fun jump(pkg: String, action: String, uri: String = "", args: List<Map<String, String>> = emptyList()) =
        mapOf("type" to 1, "param" to mapOf("packageName" to pkg, "actionObject" to mapOf("actionString" to action, "actionUri" to uri), "args" to args))
    private fun page(rows: List<Map<String, Any?>>, total: Int = rows.size, page: Int = 1, pages: Int = 1, recommended: Boolean = false): Map<String, Any?> =
        mapOf("code" to 0, "data" to mapOf((if (recommended) "items" else "list") to listOf(mapOf("items" to rows)), "total" to total, "page" to page, "totalPage" to pages))

    @Test fun actualAnonymousResponsesMapToAllFourSelectedEditions() {
        val parsed = parseCatalogPage(page(samples), false)
        assertEquals(CatalogPlatform.entries.toSet(), parsed.hits.map { it.route.platform }.toSet())
        assertEquals(setOf("com.gitvvideo.sony", "com.cibn.tv.sony", "com.ktcp.tvvideo", "com.hunantv.market"), parsed.hits.map { it.route.platform.packageName }.toSet())
        assertTrue(parsed.hits.all { it.poster.startsWith("https://") })
    }
    @Test fun iqiyiPreservesOriginalStringAndLongIdentifiers() {
        val raw = """{"videoId":9999888877776666555,"episodeId":"9876543210123456789","chnId":2,"customer":"dangbei"}"""
        val result = parseCatalogRoute(jump("com.gitvdemo.video", "com.gitvdemo.video.action.ACTION_DETAIL", args = listOf(mapOf("key" to "playInfo", "vtype" to "string", "value" to raw))), "com.gitvdemo.video")!!
        assertEquals(raw, result.extras["playInfo"])
        assertEquals("com.gitvvideo.sony.action.ACTION_DETAIL", result.action)
    }
    @Test fun youkuUsesItsOwnRouterWithAlphanumericIds() {
        val raw = "ykott://tv/detail?url=tv/v3/show/detail?id=eadd7cd556d64ef9b08a&isBackYingHome=false&fullscreen=false&from=dangbei&from_out=search_origin"
        val result = parseCatalogRoute(jump("com.cibn.tv", "android.intent.action.VIEW", raw), "com.cibn.tv")!!
        assertTrue(result.uri.startsWith("sonycibntv://yingshi_detail?"))
        assertTrue(result.uri.contains("id=eadd7cd556d64ef9b08a"))
        assertTrue(result.uri.contains("isBackYingHome=false"))
        assertEquals(raw, result.extras["detail_uri"])
        assertEquals("search_origin", result.extras["from_out"])
    }
    @Test fun rejectsMismatchedAppsAndUnexpectedActionsOrRoutes() {
        assertNull(parseCatalogRoute(jump("com.cibn.tv", "android.intent.action.VIEW", "ykott://tv/detail?url=https://other.example/?id=123"), "com.cibn.tv"))
        assertNull(parseCatalogRoute(jump("com.hunantv.market", "android.intent.action.VIEW", "mgtvapp://vod/player?clipId=637526"), "com.ktcp.tvvideo"))
        assertNull(parseCatalogRoute(jump("com.ktcp.tvvideo", "com.tencent.qqlivetv.open", "tenvideo2://?action=13&cover_id=film"), "com.ktcp.tvvideo"))
        assertNull(parseCatalogRoute(jump("com.hunantv.market", "android.intent.action.VIEW", "mgtvapp://vod/player?clipId=not-numeric"), "com.hunantv.market"))
        assertNull(parseCatalogRoute(jump("unrelated.application", "android.intent.action.VIEW", "https://example.com"), ""))
    }
    @Test fun ignoresFallbackRecommendationsWhenThereAreNoMatches() {
        val zeroByFilter = mapOf("code" to 0, "data" to mapOf("filter" to listOf(mapOf("id" to 0, "num" to 0)), "list" to listOf(mapOf("items" to samples))))
        assertTrue(parseCatalogPage(zeroByFilter, false).hits.isEmpty())
        assertTrue(parseCatalogPage(page(samples, total = 0), false).hits.isEmpty())
        assertEquals(4, parseCatalogPage(page(samples, recommended = true), true).hits.size)
    }
    @Test fun preservesDifferentPlatformEditionsAndPageInformation() {
        val rows = samples.map { it + mapOf("id" to 42, "title" to "Same title") }
        val parsed = parseCatalogPage(page(rows + rows, total = 82, page = 2, pages = 5), false)
        assertEquals(4, parsed.hits.size)
        assertEquals(82, parsed.total)
        assertEquals(2, parsed.page)
        assertEquals(5, parsed.pages)
    }
    @Test fun onlyLoadsPublicArtworkFromExpectedImageHosts() {
        assertEquals("https://zmimg-sony.qun7.com/art.jpg", catalogPoster("http://zmimg-sony.qun7.com/art.jpg"))
        listOf("https://localhost/art.jpg", "http://192.168.0.1/art.jpg", "https://qun7.com.evil.example/art.jpg", "https://user:pass@zmimg-sony.qun7.com/art.jpg", "https://zmimg-sony.qun7.com:8080/art.jpg", "file:///private/art.jpg").forEach { assertEquals("", catalogPoster(it)) }
    }
}
