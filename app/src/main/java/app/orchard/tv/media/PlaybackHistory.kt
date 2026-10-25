package app.orchard.tv.media

/** Programme/episode identity stays stable across progress and artwork updates. */
internal fun historyKey(item: RecentItem): String = item.packageName + "\n" +
    (item.mediaId.takeIf { it.isNotBlank() } ?: (item.title + "\n" + item.subtitle))

/** OEM-capable apps have one authoritative stream; generic session cards must not duplicate it. */
internal fun normalisePlaybackHistory(items: List<RecentItem>): List<RecentItem> {
    val durablePackages = items.filter { it.resumeInfo.isNotBlank() }.map { it.packageName }.toSet()
    return items.filter { it.resumeInfo.isNotBlank() || it.packageName !in durablePackages }
        .sortedByDescending { it.updatedAt }.distinctBy(::historyKey).take(100)
}

/** Keep separate programmes/episodes while preferring an app's durable history over its media session. */
internal fun mergeRecent(items: List<RecentItem>, incoming: RecentItem): List<RecentItem> {
    if (incoming.resumeInfo.isBlank() && items.any { it.packageName == incoming.packageName && it.resumeInfo.isNotBlank() })
        return normalisePlaybackHistory(items)
    return normalisePlaybackHistory(items.filterNot { historyKey(it) == historyKey(incoming) } + incoming)
}

/** MediaSession positions are snapshots, not ticking counters. */
internal fun estimatedPosition(position: Long, duration: Long, playing: Boolean, speed: Float, elapsedMs: Long): Long {
    val elapsed = if (playing && speed.isFinite()) elapsedMs.coerceAtLeast(0) * speed.toDouble() else 0.0
    val result = (position.coerceAtLeast(0).toDouble() + elapsed).coerceAtLeast(0.0).toLong()
    return if (duration > 0) result.coerceAtMost(duration) else result
}

/** Prefer durable programme parameters over a newer discovery timestamp for the same title. */
internal fun shouldAcceptSonyRecord(old: RecentItem, incoming: RecentItem): Boolean =
    if (old.resumeInfo.isNotBlank()) incoming.updatedAt > old.updatedAt ||
        (incoming.updatedAt == old.updatedAt && incoming.resumeInfo == old.resumeInfo &&
            (incoming.artwork != old.artwork || incoming.appLabel != old.appLabel))
    else incoming.updatedAt > old.updatedAt || old.title == incoming.title
