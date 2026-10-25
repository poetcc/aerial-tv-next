package app.orchard.tv.media

import org.junit.Assert.*
import org.junit.Test

class SonyRecordTest {
    private fun record() = mapOf<String, Any>(
        "action" to "com.gitvvideo.sony.action.ACTION_ADD_PLAYRECORD",
        "videoName" to "Test programme", "videoId" to "1234567890123456", "episodeId" to "2345678901234567",
        "episodeName" to "1", "chnId" to 2, "currentPosition" to 102, "duration" to 1070,
        "receivedAt" to 1700000000000L, "videoImgUrl" to "https://pic6.inter.ptqy.gitv.tv/image/poster.jpg",
    )

    @Test fun convertsObservedSecondsAndRetainsStableEpisodeIdentity() {
        val parsed = parseSonyRecord(record())!!
        assertEquals(102000L, parsed.positionMs)
        assertEquals(1070000L, parsed.durationMs)
        assertEquals("2345678901234567", parsed.episodeId)
        assertEquals("com.gitvvideo.sony", parsed.packageName)
    }

    @Test fun rejectsOtherAppsAndMissingOrMalformedPlaybackIdentity() {
        assertNull(parseSonyRecord(record() + ("action" to "other.app.action.ACTION_ADD_PLAYRECORD")))
        assertNull(parseSonyRecord(record() - "episodeId"))
        assertNull(parseSonyRecord(record() + ("videoId" to "intent://untrusted")))
        assertNull(parseSonyRecord(record() + ("chnId" to 0)))
        assertNull(parseSonyRecord(record() + ("videoName" to " ")))
    }

    @Test fun boundsUntrustedProgressBeforeConvertingToMilliseconds() {
        assertEquals(1070000L, parseSonyRecord(record() + ("currentPosition" to Long.MAX_VALUE))!!.positionMs)
        assertEquals(0L, parseSonyRecord(record() + ("currentPosition" to -5))!!.positionMs)
    }

    @Test fun excludesLocalNetworkAndDeceptiveArtworkHosts() {
        for (url in listOf("https://192.168.0.1/a.jpg", "https://pic.gitv.tv.attacker.example/a.jpg", "https://user:pass@pic.gitv.tv/a.jpg")) {
            assertEquals("", parseSonyRecord(record() + ("videoImgUrl" to url))!!.artworkUrl)
        }
    }

    @Test fun upgradesKnownPosterHostsToHttps() {
        assertEquals("https://pic6.inter.ptqy.gitv.tv/a.jpg", parseSonyRecord(record() + ("videoImgUrl" to "http://pic6.inter.ptqy.gitv.tv/a.jpg"))!!.artworkUrl)
    }

    @Test fun tencentUsesMillisecondProgressAndActualVideoIdentity() {
        val input = record() + mapOf("action" to "com.tv.history.secure.add", "decoded" to true,
            "package_name" to "com.ktcp.tvvideo", "videoId" to "cover_123", "videoVId" to "v123abc", "episodeId" to "2", "currentPosition" to 102000, "duration" to 1070000)
        val parsed = parseSonyRecord(input)!!
        assertEquals("v123abc", parsed.mediaIdentity)
        assertEquals(102000L, parsed.positionMs)
        assertEquals("com.ktcp.tvvideo", parsed.packageName)
        assertNull(parseSonyRecord(input + ("decoded" to false)))
        assertNull(parseSonyRecord(input + ("package_name" to "unrelated.app")))
    }

    @Test fun youkuKeepsMatchingSourcePackageAndBroadcastRoute() {
        val input = record() + mapOf("action" to "com.tv.ali.history.add", "srcapp" to "com.cibn.tv.sony",
            "videoHImgUrl" to "https://m.cp12.ott.cibntv.net/poster.jpg", "showStrId" to "show_123", "uri" to "sonycibntv://yingshi_detail/?from=dhistory&id=123", "currentPosition" to 123000)
        val parsed = parseSonyRecord(input)!!
        assertEquals("com.cibn.tv.sony", parsed.packageName)
        assertEquals("https://m.cp12.ott.cibntv.net/poster.jpg", parsed.artworkUrl)
        assertEquals("sonycibntv://yingshi_detail/?from=dhistory&id=123", parsed.resumeUri)
        assertNull(parseSonyRecord(input + ("srcapp" to "com.cibn.tv")))
        assertNull(parseSonyRecord(input + ("uri" to "intent://untrusted")))
    }

    @Test fun mangoUsesTheNewChannelBroadcastAndRejectsOtherDangbeiApps() {
        val input = record() + mapOf("action" to "com.dangbei.history.add", "appname" to "com.dangbei.mgtv", "videoImgUrl" to "https://3img.hitv.com/preview/poster.jpg", "currentPosition" to 102000, "duration" to 1070000)
        val parsed = parseSonyRecord(input)!!
        assertEquals("com.hunantv.market", parsed.packageName)
        assertEquals("https://3img.hitv.com/preview/poster.jpg", parsed.artworkUrl)
        assertEquals("", parseSonyRecord(input + ("videoImgUrl" to "https://3img.hitv.com.attacker.example/poster.jpg"))!!.artworkUrl)
        assertEquals(102000L, parsed.positionMs)
        assertNull(parseSonyRecord(input + ("appname" to "com.dangbei.other")))
    }
}
