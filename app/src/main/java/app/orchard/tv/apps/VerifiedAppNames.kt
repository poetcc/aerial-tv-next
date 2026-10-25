package app.orchard.tv.apps

import android.content.Context
import android.content.pm.PackageManager
import app.orchard.tv.R
import org.json.JSONArray
import java.security.MessageDigest

/** User-facing aliases apply only to the signed application identities verified during setup. */
object VerifiedAppNames {
    private data class Edition(val version: Int, val certificate: String, val title: Int)
    private var editions: Map<String, Edition>? = null
    private val cache = mutableMapOf<String, Pair<Long, Int?>>()
    @Synchronized private fun title(context: Context, pkg: String): Int? {
        val registry = editions ?: runCatching {
            val rows = JSONArray(context.assets.open("sony_editions.json").bufferedReader().use { it.readText() })
            (0 until rows.length()).associate { index ->
                val row = rows.getJSONObject(index)
                val name = when (row.getString("platform")) {
                    "iqiyi" -> R.string.app_alias_iqiyi
                    "tencent" -> R.string.app_alias_tencent
                    "youku" -> R.string.app_alias_youku
                    else -> R.string.app_alias_mango
                }
                row.getString("package") to Edition(row.getInt("version"), row.getString("certificate"), name)
            }
        }.getOrDefault(emptyMap()).also { editions = it }
        val edition = registry[pkg] ?: return null
        @Suppress("DEPRECATION")
        val info = runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES) }.getOrNull() ?: return null
        cache[pkg]?.takeIf { it.first == info.lastUpdateTime }?.let { return it.second }
        @Suppress("DEPRECATION")
        val verified = info.versionCode >= edition.version && info.signatures?.any { signature ->
            val hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            hash == edition.certificate
        } == true
        val result = edition.title.takeIf { verified }; cache[pkg] = info.lastUpdateTime to result
        return result
    }
    fun isVerified(context: Context, pkg: String): Boolean = title(context, pkg) != null

    fun label(context: Context, pkg: String, fallback: String? = null): String = title(context, pkg)?.let { context.getString(it) }
        ?: fallback ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}
