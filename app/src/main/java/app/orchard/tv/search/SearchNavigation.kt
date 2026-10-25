package app.orchard.tv.search

enum class SearchDirection { Left, Right, Up, Down }

/** Every remote direction has an explicit, currently mounted destination. */
internal class SearchFocusGraph(
    val results: List<String>, val hasMore: Boolean, val failed: Boolean,
    private val keyboardIndex: Int, private val selectedFilter: Int,
) {
    val nodes = listOf("input") + (0..35).map { "key:$it" } +
        listOf("delete", "clear", "submit", "chinese") + (0..4).map { "filter:$it" } + results +
        (if (hasMore) listOf("more") else emptyList()) + (if (failed) listOf("retry") else emptyList())
    val firstResult get() = results.firstOrNull() ?: "filter:${selectedFilter.coerceIn(0, 4)}"
    fun move(from: String, direction: SearchDirection): String {
        val key = keyboardIndex.coerceIn(0, 35)
        val actions = listOf("delete", "clear", "submit")
        val target = when {
            from.startsWith("key:") -> {
                val index = from.substringAfter(':').toIntOrNull()?.coerceIn(0, 35) ?: 0
                when (direction) {
                    SearchDirection.Left -> "key:${if (index % 6 > 0) index - 1 else index}"
                    SearchDirection.Right -> if (index % 6 < 5) "key:${index + 1}" else firstResult
                    SearchDirection.Up -> if (index >= 6) "key:${index - 6}" else "input"
                    SearchDirection.Down -> if (index < 30) "key:${index + 6}" else actions[(index % 6 / 2).coerceIn(0, 2)]
                }
            }
            from == "input" -> when (direction) {
                SearchDirection.Right, SearchDirection.Up -> firstResult
                SearchDirection.Down -> "key:0"
                SearchDirection.Left -> from
            }
            from in actions -> {
                val index = actions.indexOf(from)
                when (direction) {
                    SearchDirection.Left -> actions[(index - 1).coerceAtLeast(0)]
                    SearchDirection.Right -> if (index < 2) actions[index + 1] else firstResult
                    SearchDirection.Up -> "key:${30 + index * 2}"
                    SearchDirection.Down -> "chinese"
                }
            }
            from == "chinese" -> when (direction) {
                SearchDirection.Up -> "submit"
                SearchDirection.Right -> firstResult
                else -> from
            }
            from.startsWith("filter:") -> {
                val index = from.substringAfter(':').toIntOrNull()?.coerceIn(0, 4) ?: 0
                when (direction) {
                    SearchDirection.Left -> if (index > 0) "filter:${index - 1}" else "key:5"
                    SearchDirection.Right -> "filter:${(index + 1).coerceAtMost(4)}"
                    SearchDirection.Down -> results.firstOrNull() ?: if (failed) "retry" else "key:5"
                    SearchDirection.Up -> from
                }
            }
            from in results -> {
                val index = results.indexOf(from)
                when (direction) {
                    SearchDirection.Left -> if (index % 3 > 0) results[index - 1] else "key:$key"
                    SearchDirection.Right -> if (index % 3 < 2 && index < results.lastIndex) results[index + 1] else from
                    SearchDirection.Up -> if (index >= 3) results[index - 3] else "filter:${selectedFilter.coerceIn(0, 4)}"
                    SearchDirection.Down -> if (index / 3 < results.lastIndex / 3) results[(index + 3).coerceAtMost(results.lastIndex)] else if (hasMore) "more" else from
                }
            }
            from == "more" || from == "retry" -> when (direction) {
                SearchDirection.Up -> results.lastOrNull() ?: "filter:${selectedFilter.coerceIn(0, 4)}"
                SearchDirection.Left -> "chinese"
                else -> from
            }
            else -> "key:0"
        }
        return target.takeIf { it in nodes } ?: "key:0"
    }
}
