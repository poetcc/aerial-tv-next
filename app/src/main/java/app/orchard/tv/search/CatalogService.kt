package app.orchard.tv.search

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import app.orchard.tv.R
import app.orchard.tv.apps.VerifiedAppNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

internal object CatalogService {
    private const val host = "https://sonytyapi.v5tv.com"
    suspend fun search(keyword: String, page: Int): CatalogPage = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext CatalogPage(emptyList(), 0, 1, 1, false)
        val path = "/v1/search/detail/?keyword=" + URLEncoder.encode(keyword.take(40), "UTF-8") + "&page=${page.coerceIn(1, 100)}&no_encrypt=1"
        val connection = URL(host + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000; connection.readTimeout = 7000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            check(connection.responseCode == 200) { "Catalogue HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.boundedBytes(2 * 1024 * 1024) }
            val result = parseCatalogPage(jsonValue(JSONObject(bytes.toString(Charsets.UTF_8))).catalogObject(), false)
            Log.i("AerialSearch", "Loaded catalogue; rows=${result.hits.size} page=${result.page} pages=${result.pages}")
            result
        } finally { connection.disconnect() }
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.opt(it)) }
        is JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
        JSONObject.NULL -> null
        else -> value
    }

    fun open(context: Context, item: CatalogHit) {
        val route = item.route
        if (!VerifiedAppNames.isVerified(context, route.platform.packageName)) {
            Toast.makeText(context, R.string.search_app_missing, Toast.LENGTH_LONG).show(); return
        }
        val intent = Intent(route.action).setPackage(route.platform.packageName).addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (route.uri.isNotBlank()) intent.data = Uri.parse(route.uri)
        route.extras.forEach { (name, value) -> intent.putExtra(name, value) }
        try {
            context.startActivity(intent)
            Log.i("AerialSearch", "Opened platform=" + route.platform.name)
        } catch (failure: RuntimeException) {
            Log.w("AerialSearch", "Programme route unavailable for " + route.platform.name, failure)
            Toast.makeText(context, R.string.search_open_failed, Toast.LENGTH_LONG).show()
        }
    }
}

private fun java.io.InputStream.boundedBytes(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer); if (count < 0) break
        check(out.size() + count <= limit) { "Catalogue response too large" }
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

internal object CatalogImages {
    suspend fun load(context: Context, source: String): Bitmap? = withContext(Dispatchers.IO) {
        val url = catalogPoster(source).takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching {
            val dir = File(context.cacheDir, "catalog_art").apply { mkdirs() }
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(dir, "$key.jpg")
            val bytes = if (file.exists()) file.readBytes() else {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
                try {
                    if (connection.responseCode != 200) return@runCatching null
                    connection.inputStream.use { it.boundedBytes(2 * 1024 * 1024) }
                } finally { connection.disconnect() }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize.coerceAtLeast(1) > 480 || bounds.outHeight / inSampleSize.coerceAtLeast(1) > 480)
                    inSampleSize = inSampleSize.coerceAtLeast(1) * 2
            }
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (image != null && !file.exists()) {
                file.writeBytes(bytes)
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
            }
            image
        }.getOrNull()
    }
}
