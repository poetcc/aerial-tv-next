package app.orchard.tv.search

import org.junit.Assert.*
import org.junit.Test

class SearchBehaviourTest {
    private fun hit(id: String, platform: CatalogPlatform, title: String = "三体", year: String = "2023") =
        CatalogHit(id, title, year, "", "", "", CatalogRoute(platform, "test"))

    @Test fun allDirectionsStayOnAnExistingControlAcrossEmptyPartialAndLongResults() {
        for (size in listOf(0, 1, 2, 3, 4, 6, 7, 80)) {
            for (more in listOf(false, true)) for (failed in listOf(false, true)) {
                val graph = SearchFocusGraph((0 until size).map { "hit:$it" }, more, failed, 35, 4)
                graph.nodes.forEach { node -> SearchDirection.entries.forEach { direction ->
                    assertTrue("$size $node $direction", graph.move(node, direction) in graph.nodes)
                } }
            }
        }
    }
    @Test fun resultsHaveRoutesBackToTheKeyboardAndFilters() {
        val graph = SearchFocusGraph((0..6).map { "hit:$it" }, true, false, 17, 2)
        assertEquals("key:17", graph.move("hit:3", SearchDirection.Left))
        assertEquals("filter:2", graph.move("hit:1", SearchDirection.Up))
        assertEquals("more", graph.move("hit:6", SearchDirection.Down))
        assertEquals("hit:6", graph.move("more", SearchDirection.Up))
        assertEquals("hit:6", graph.move("hit:5", SearchDirection.Down))
    }
    @Test fun emptyResultsNeverSendFocusToAnUnmountedResult() {
        val graph = SearchFocusGraph(emptyList(), false, false, 0, 0)
        assertEquals("filter:0", graph.move("key:5", SearchDirection.Right))
        assertEquals("key:5", graph.move("filter:0", SearchDirection.Down))
        assertEquals("input", graph.move("key:0", SearchDirection.Up))
        assertEquals("key:0", graph.move("input", SearchDirection.Down))
    }
    @Test fun exactTitleBeatsOtherTitlesRegardlessOfPlatform() {
        val hits = listOf(hit("t", CatalogPlatform.TENCENT, "三体解读"), hit("i", CatalogPlatform.IQIYI), hit("m", CatalogPlatform.MANGO, "讲述三体"))
        assertEquals("i", fairCatalogResults(hits, "三体").first().id)
    }
    @Test fun equalMatchesAlternatePlatformsWithoutUsingServerOrder() {
        val hits = CatalogPlatform.entries.flatMap { platform -> (0..2).map { hit("$it", platform) } }
        val first = fairCatalogResults(hits, "三体")
        assertEquals(first.map { it.key }, fairCatalogResults(hits.reversed(), "三体").map { it.key })
        first.chunked(4).forEach { round -> assertEquals(CatalogPlatform.entries.toSet(), round.map { it.route.platform }.toSet()) }
    }
    @Test fun emptyQueryHasNoRecommendationsAndMatchingIsCaseInsensitive() {
        assertTrue(fairCatalogResults(listOf(hit("t", CatalogPlatform.TENCENT)), "  ").isEmpty())
        val hits = listOf(hit("t", CatalogPlatform.TENCENT, "Hi5"), hit("i", CatalogPlatform.IQIYI, "HI"))
        assertEquals("i", fairCatalogResults(hits, "hi").first().id)
    }
}
