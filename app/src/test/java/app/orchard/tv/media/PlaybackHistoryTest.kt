package app.orchard.tv.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryTest {
    private fun item(pkg: String, time: Long, title: String = pkg) =
        RecentItem(pkg, pkg, title, "", 10, 100, time, null)

    @Test fun retainsDifferentProgrammesFromTheSameAppAndUpdatesExistingProgress() {
        val first = item("app", 1, "Film A").copy(mediaId = "film-a", resumeInfo = "route-a")
        val second = item("app", 2, "Film B").copy(mediaId = "film-b", resumeInfo = "route-b")
        val history = mergeRecent(listOf(first), second)
        assertEquals(listOf("Film B", "Film A"), history.map { it.title })
        val updated = mergeRecent(history, first.copy(updatedAt = 3, position = 75))
        assertEquals(2, updated.size)
        assertEquals(75L, updated.first().position)
        assertEquals("route-a", updated.first().resumeInfo)
    }

    @Test fun boundsHistoryToOneHundredWithoutCollapsingDifferentAppsOrEpisodes() {
        val history = (1..100).map { item("app", it.toLong()).copy(mediaId = "episode-$it") }
        val result = mergeRecent(history, item("another-app", 101).copy(mediaId = "episode-100"))
        assertEquals(100, result.size)
        assertEquals("another-app", result.first().packageName)
        assertEquals(false, result.any { it.mediaId == "episode-1" })
        assertEquals(2, result.count { it.mediaId == "episode-100" })
    }

    @Test fun discoveringPausedSessionDoesNotMoveItAboveNewPlayback() {
        val result = mergeRecent(listOf(item("new", 200), item("paused", 100)), item("paused", 100).copy(position = 50))
        assertEquals(listOf("new", "paused"), result.map { it.packageName })
        assertEquals(50L, result.last().position)
    }

    @Test fun estimatesPlayingPositionAtPlaybackSpeed() {
        assertEquals(4_000L, estimatedPosition(1_000, 10_000, true, 1.5f, 2_000))
        assertEquals(1_000L, estimatedPosition(1_000, 10_000, false, 1f, 2_000))
    }

    @Test fun boundsProgressAndHandlesUnknownDurationAndInvalidClock() {
        assertEquals(10_000L, estimatedPosition(9_000, 10_000, true, 1f, 5_000))
        assertEquals(14_000L, estimatedPosition(9_000, 0, true, 1f, 5_000))
        assertEquals(0L, estimatedPosition(-1, 0, false, 1f, 100))
        assertEquals(1_000L, estimatedPosition(1_000, 0, true, 1f, -100))
        assertEquals(1_000L, estimatedPosition(1_000, 0, true, Float.NaN, 100))
    }
    @Test fun upgradesSameProgrammeToDurableResumeWithoutOverwritingNewerDifferentContent() {
        val incoming = item("iqiyi", 100, "Same title").copy(resumeInfo = "validated programme")
        assertEquals(true, shouldAcceptSonyRecord(item("iqiyi", 200, "Same title"), incoming))
        assertEquals(false, shouldAcceptSonyRecord(item("iqiyi", 200, "Different title"), incoming))
        assertEquals(false, shouldAcceptSonyRecord(incoming, incoming))
    }
    @Test fun refreshesPosterAndAliasWithoutReorderingAnUnchangedProgramme() {
        val old = item("app", 100).copy(resumeInfo = "validated programme")
        assertEquals(true, shouldAcceptSonyRecord(old, old.copy(artwork = "poster.jpg")))
        assertEquals(true, shouldAcceptSonyRecord(old, old.copy(appLabel = "Display alias")))
        assertEquals(false, shouldAcceptSonyRecord(old, old))
        assertEquals(false, shouldAcceptSonyRecord(old, old.copy(updatedAt = 99, artwork = "poster.jpg")))
    }

    @Test fun replacesAnActorSessionCardWithTheAuthoritativeEpisodeRecord() {
        val session = item("iqiyi", 200, "Drama").copy(mediaId = "session-title", subtitle = "Actor")
        val episode = item("iqiyi", 190, "Drama").copy(mediaId = "episode-5", subtitle = "Episode 5", resumeInfo = "route-5")
        assertEquals(listOf(episode), mergeRecent(listOf(session), episode))
        assertEquals(listOf(episode), mergeRecent(listOf(episode), session))
        assertEquals(listOf(episode), mergeRecent(listOf(episode), session.copy(mediaId = "episode-5")))
    }

    @Test fun normalisesSavedDuplicatesWithoutDroppingDifferentEpisodesOrOtherApps() {
        val actor = item("iqiyi", 300, "Drama").copy(mediaId = "generic", subtitle = "Actor")
        val episode1 = item("iqiyi", 100, "Drama").copy(mediaId = "episode-1", subtitle = "Episode 1", resumeInfo = "route-1")
        val episode5 = item("iqiyi", 200, "Drama").copy(mediaId = "episode-5", subtitle = "Episode 5", resumeInfo = "route-5")
        val music = item("music", 250, "Song").copy(subtitle = "Singer")
        val result = normalisePlaybackHistory(listOf(actor, episode1, episode5, music))
        assertEquals(listOf(music, episode5, episode1), result)
        assertEquals(result, normalisePlaybackHistory(result))
    }

    @Test fun preservesGenericSessionsForAppsWithoutAnAuthoritativeRecord() {
        val first = item("music", 100, "Song 1").copy(mediaId = "song-1")
        val second = item("music", 200, "Song 2").copy(mediaId = "song-2")
        assertEquals(listOf(second, first), mergeRecent(listOf(first), second))
    }
}
