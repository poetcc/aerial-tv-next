package app.orchard.tv.search

import java.text.Normalizer
import java.util.Locale

private fun searchText(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

/** Match quality first; equally relevant platforms take turns, independent of server position. */
internal fun fairCatalogResults(hits: List<CatalogHit>, keyword: String): List<CatalogHit> {
    val query = searchText(keyword)
    if (query.isEmpty()) return emptyList()
    fun relevance(hit: CatalogHit): Int {
        val title = searchText(hit.title)
        return when {
            title == query -> 0
            title.startsWith(query) -> 1
            title.contains(query) -> 2
            else -> 3 // Initial-letter matches are supplied by the catalogue, not guessed locally.
        }
    }
    val platforms = CatalogPlatform.entries
    val offset = (query.hashCode().toLong() and 0x7fffffffL).rem(platforms.size).toInt()
    val order = platforms.drop(offset) + platforms.take(offset)
    return hits.distinctBy { it.key }.groupBy(::relevance).toSortedMap().values.flatMap { tier ->
        val buckets = tier.groupBy { it.route.platform }.mapValues { (_, rows) ->
            rows.sortedWith(compareBy<CatalogHit> { searchText(it.title) }.thenByDescending { it.year.toIntOrNull() ?: 0 }.thenBy { it.id })
        }
        buildList {
            repeat(buckets.values.maxOfOrNull { it.size } ?: 0) { index ->
                order.forEach { platform -> buckets[platform]?.getOrNull(index)?.let(::add) }
            }
        }
    }
}
