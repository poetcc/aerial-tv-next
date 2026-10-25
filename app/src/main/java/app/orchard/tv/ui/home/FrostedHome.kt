package app.orchard.tv.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.orchard.tv.wallpaper.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private fun Context.hostActivity(): Activity? {
    var candidate = this
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate
        val next = candidate.baseContext
        if (next === candidate) break
        candidate = next
    }
    return candidate as? Activity
}

/** Prepare once before opening the panel so glass and content appear in the same frame. */
internal suspend fun captureFrostedHome(home: View): ImageBitmap? {
    if (home.width <= 0 || home.height <= 0) return null
    val width = (home.width / 6).coerceAtLeast(1)
    val height = (home.height / 6).coerceAtLeast(1)
    val window = home.context.hostActivity()?.window
    val snapshot = if (Build.VERSION.SDK_INT >= 26 && window != null) {
        suspendCancellableCoroutine<Bitmap?> { continuation ->
            val destination = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, destination, { result ->
                    if (!continuation.isActive) destination.recycle()
                    else if (result == PixelCopy.SUCCESS) continuation.resume(destination)
                    else { destination.recycle(); continuation.resume(null) }
                }, Handler(Looper.getMainLooper()))
            } catch (_: RuntimeException) {
                destination.recycle()
                if (continuation.isActive) continuation.resume(null)
            }
        }
    } else {
        // Old TVs lack PixelCopy. Software view capture is confined to the launcher's own content.
        runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.scale(width.toFloat() / home.width, height.toFloat() / home.height)
                home.draw(canvas)
            }
        }.getOrNull()
    } ?: return null
    return withContext(Dispatchers.Default) {
        val pixels = IntArray(width * height)
        snapshot.getPixels(pixels, 0, width, 0, 0, width, height)
        // Three box passes approximate a Gaussian kernel; downsampling keeps TV CPU work bounded.
        repeat(3) { Images.boxBlur(pixels, width, height, 3) }
        snapshot.setPixels(pixels, 0, width, 0, 0, width, height)
        snapshot.asImageBitmap()
    }
}
