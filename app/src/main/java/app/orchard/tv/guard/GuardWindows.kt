package app.orchard.tv.guard

/** System/IME popups must not erase the last real foreground activity during wake. */
internal fun rememberGuardWindow(
    previous: Pair<String, String>?, pkg: String, cls: String, fullScreen: Boolean,
    stockHomes: Map<String, Set<String>>, transientPackages: Set<String>, ownPackage: String,
): Pair<String, String>? {
    if (pkg in transientPackages) return previous
    val stockHome = cls in (stockHomes[pkg] ?: emptySet())
    return if (fullScreen || stockHome || previous == null || pkg == ownPackage) pkg to cls else previous
}

internal fun stockHomeInFront(window: Pair<String, String>?, stockHomes: Map<String, Set<String>>): Boolean =
    window == null || window.second in (stockHomes[window.first] ?: emptySet())
