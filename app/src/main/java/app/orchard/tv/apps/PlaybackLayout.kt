package app.orchard.tv.apps

internal object PlaybackLayout {
    val replacements = linkedMapOf(
        "com.gitvvideo.sony" to "com.gitvdemo.video",
        "com.cibn.tv.sony" to "com.cibn.tv",
        "com.ktcp.tvvideo" to "com.ktcp.video",
        "com.hunantv.market" to "com.hunantv.license",
    )
}

/** Only verified installed replacements may hide their original counterpart. */
internal fun playbackLayout(order: List<String>, hidden: Set<String>, verified: Set<String>, hideHelper: Boolean): Pair<List<String>, Set<String>> {
    val promoted = PlaybackLayout.replacements.keys.filter { it in verified && it in order }
    val originals = promoted.mapNotNull(PlaybackLayout.replacements::get)
    val nextHidden = (hidden + originals + if (hideHelper) setOf("com.dangbei.TVHomeLauncher") else emptySet()) - promoted.toSet()
    return (promoted + order.filter { it !in promoted }) to nextHidden
}

/** Brand-independent TV app families; labels cover OEM package names absent from the known aliases. */
internal fun playbackEditionFor(packageName: String, label: String): String? {
    val pkg = packageName.lowercase(java.util.Locale.ROOT)
    val name = label.lowercase(java.util.Locale.ROOT).filterNot { it.isWhitespace() }
    return when {
        pkg.startsWith("com.gitvvideo") || pkg.startsWith("com.gitvdemo") || pkg.startsWith("com.qiyi.video") ||
            listOf("爱奇艺", "奇异果", "iqiyi").any(name::contains) -> "com.gitvvideo.sony"
        pkg == "com.cibn.tv" || pkg.startsWith("com.cibn.tv.") || pkg.startsWith("com.youku.") ||
            listOf("优酷", "酷喵", "youku").any(name::contains) -> "com.cibn.tv.sony"
        pkg.startsWith("com.ktcp.video") || pkg.startsWith("com.ktcp.tvvideo") ||
            listOf("腾讯视频", "云视听极光", "newtv极光").any(name::contains) -> "com.ktcp.tvvideo"
        pkg.startsWith("com.hunantv.") || pkg.startsWith("com.mgtv.") ||
            listOf("芒果tv", "芒果视频", "mangotv").any(name::contains) -> "com.hunantv.market"
        else -> null
    }
}

/** Only the explicitly verified edition of each platform appears, independent of TV manufacturer. */
internal fun showPlaybackApp(packageName: String, label: String, verified: Set<String>): Boolean {
    if (packageName == "com.dangbei.TVHomeLauncher") return false
    val expected = playbackEditionFor(packageName, label) ?: return true
    return packageName == expected && packageName in verified
}
