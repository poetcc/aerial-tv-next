package app.orchard.tv.apps

import org.junit.Assert.*
import org.junit.Test

class PlaybackLayoutTest {
    @Test fun promotesVerifiedEditionsInRequestedOrderAndPreservesOtherApps() {
        val editions = PlaybackLayout.replacements.keys.toList()
        val originals = PlaybackLayout.replacements.values.toList()
        val order = originals + listOf("other-a", "other-b") + editions.reversed()
        val result = playbackLayout(order, setOf("already-hidden", editions.first()), editions.toSet(), true)
        assertEquals(editions, result.first.take(4))
        assertEquals(listOf("other-a", "other-b"), result.first.filter { it.startsWith("other-") })
        assertTrue(result.second.containsAll(originals + listOf("already-hidden", "com.dangbei.TVHomeLauncher")))
        assertFalse(result.second.any { it in editions })
    }
    @Test fun doesNotHideAnOriginalWithoutItsVerifiedInstalledReplacement() {
        val original = "com.gitvdemo.video"
        val result = playbackLayout(listOf(original), emptySet(), setOf("com.gitvvideo.sony"), true)
        assertFalse(original in result.second)
        assertEquals(listOf(original), result.first)
    }

    @Test fun hidesDifferentManufacturersPackagesUsingPlatformNames() {
        val verified = PlaybackLayout.replacements.keys
        for ((pkg, label) in listOf(
            "oem.brand.iqiyi" to "爱奇艺 TV版", "other.qiyiguo" to "银河奇异果",
            "oem.brand.youku" to "CIBN酷喵影视", "oem.brand.tencent" to "云视听极光",
            "other.newtv" to "NewTV 极光", "oem.brand.mango" to "芒果 TV",
        )) assertFalse(showPlaybackApp(pkg, label, verified))
        for (pkg in verified) assertTrue(showPlaybackApp(pkg, "Official channel", verified))
        assertFalse(showPlaybackApp("com.dangbei.TVHomeLauncher", "Aerial helper", verified))
    }

    @Test fun hidesKnownPackageEvenIfRenamedAndRejectsAnUnverifiedEdition() {
        assertFalse(showPlaybackApp("com.ktcp.video.sony", "Renamed by OEM", PlaybackLayout.replacements.keys))
        assertFalse(showPlaybackApp("com.gitvvideo.sony", "爱奇艺TV", emptySet()))
        assertFalse(showPlaybackApp("com.cibn.tv", "任意标题", emptySet()))
        assertTrue(showPlaybackApp("com.xiaomi.mitv.tvhome", "小米桌面", emptySet()))
        assertTrue(showPlaybackApp("com.xiaodianshi.tv.yst", "云视听小电视", emptySet()))
        assertTrue(showPlaybackApp("com.tencent.games", "腾讯游戏", emptySet()))
    }
}
