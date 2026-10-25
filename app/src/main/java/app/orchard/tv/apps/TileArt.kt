package app.orchard.tv.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.annotation.RequiresApi
import android.os.Build
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.orchard.tv.wallpaper.Images
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What a tile draws. tvOS icons are layered so they can shift against each other when focused;
 * Android's adaptive icons happen to be layered the same way, so those keep their two layers here.
 *
 * - [back] always fills the tile: the app's TV banner, the adaptive icon's background layer, or a
 *   plate in the icon's own color.
 * - [fore] is drawn centered on top, [foreFraction] of the tile height tall, and moves with parallax.
 */
class TileArt(
    val back: ImageBitmap,
    val fore: ImageBitmap?,
    val foreFraction: Float,
    val tint: Color,
    /**
     * The whole home backdrop for this app, baked small: its blurred artwork as ambient light plus
     * the shading that keeps the clock and grid legible. Scaled up to full screen it costs the GPU
     * one textured draw per frame, which matters on TV chips.
     */
    val backdrop: ImageBitmap,
)

object TileArtFactory {
    // Rendered at the tile's focused size on a 1080p panel, so the lifted tile is still pixel-sharp.
    const val W = 352
    const val H = 198

    private const val GRAPHITE_TOP = 0xFF3A3A3C.toInt()
    private const val GRAPHITE_BOTTOM = 0xFF242426.toInt()

    fun fromBanner(banner: Drawable): TileArt {
        val back = createBitmap(W, H)
        Canvas(back).apply {
            drawColor(GRAPHITE_BOTTOM)
            drawCover(banner, W, H)
        }
        return finish(back, null, 0f)
    }

    fun fromIcon(icon: Drawable): TileArt {
        if (Build.VERSION.SDK_INT >= 26 && icon is AdaptiveIconDrawable && icon.foreground != null) adaptive(icon)?.let { return it }
        return legacy(icon)
    }

    /** Null when the foreground ignores the adaptive safe zone (a legacy icon wrapped as adaptive). */
    @RequiresApi(26)
    private fun adaptive(icon: AdaptiveIconDrawable): TileArt? {
        // The foreground's 108-unit square keeps the logo inside its middle 66 units, so a
        // square as tall as the tile shows the logo at roughly the size tvOS uses.
        val size = H
        val fore = createBitmap(size, size)
        icon.foreground.setBounds(0, 0, size, size)
        icon.foreground.draw(Canvas(fore))
        if (opaqueExtent(fore) > 0.7f) {
            fore.recycle()
            return null
        }
        val back = createBitmap(W, H)
        Canvas(back).apply {
            drawColor(GRAPHITE_BOTTOM)
            icon.background?.let { bg ->
                // The background layer is a square; cover the wide tile with it.
                bg.setBounds(0, (H - W) / 2, W, (H - W) / 2 + W)
                bg.draw(this)
            }
        }
        return finish(back, fore, 1f)
    }

    /** How much of the bitmap's width or height its visible pixels span, 0..1. */
    private fun opaqueExtent(b: Bitmap): Float {
        val n = b.width
        var min = n; var max = -1
        val step = 2
        for (y in 0 until n step step) for (x in 0 until n step step) {
            if (android.graphics.Color.alpha(b.getPixel(x, y)) > 24) {
                min = minOf(min, x, y); max = maxOf(max, x, y)
            }
        }
        return if (max < 0) 0f else (max - min) / n.toFloat()
    }

    private fun legacy(icon: Drawable): TileArt {
        // A legacy icon. It sits, at the size tvOS gives a logo, on a frosted plate made from
        // itself: the icon blown up past the tile's edges, blurred and dimmed. Full-square icons
        // (many China-market system apps expect the launcher to mask them) get rounded corners.
        val sample = 192
        val iconBitmap = createBitmap(sample, sample)
        icon.setBounds(0, 0, sample, sample)
        icon.draw(Canvas(iconBitmap))
        val fore = if (opaqueCorners(iconBitmap)) roundCorners(iconBitmap, 0.225f) else iconBitmap

        val pw = 48
        val ph = 27
        val plate = createBitmap(pw, ph)
        Canvas(plate).apply {
            drawColor(plateFromTint(dominant(iconBitmap)))
            val inset = (sample * 0.2f).roundToInt()
            drawBitmap(iconBitmap, android.graphics.Rect(inset, inset + sample / 8, sample - inset, sample - inset - sample / 8), android.graphics.Rect(0, 0, pw, ph), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val px = IntArray(pw * ph)
        plate.getPixels(px, 0, pw, 0, 0, pw, ph)
        repeat(3) { Images.boxBlur(px, pw, ph, 3) }
        plate.setPixels(px, 0, pw, 0, 0, pw, ph)
        val back = createBitmap(W, H)
        val frost = ColorMatrix().apply {
            setSaturation(1.3f)
            postConcat(ColorMatrix().apply { setScale(0.7f, 0.7f, 0.7f, 1f) })
        }
        Canvas(back).drawBitmap(plate, null, android.graphics.Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(frost) })
        plate.recycle()
        if (fore !== iconBitmap) iconBitmap.recycle()
        return finish(back, fore, 0.6f)
    }

    private fun opaqueCorners(b: Bitmap): Boolean {
        val i = (b.width * 0.03f).roundToInt()
        val e = b.width - 1 - i
        return listOf(b.getPixel(i, i), b.getPixel(e, i), b.getPixel(i, e), b.getPixel(e, e))
            .all { android.graphics.Color.alpha(it) > 200 }
    }

    private fun roundCorners(src: Bitmap, radiusFraction: Float): Bitmap {
        val out = createBitmap(src.width, src.height)
        val r = src.width * radiusFraction
        Canvas(out).drawRoundRect(
            0f, 0f, src.width.toFloat(), src.height.toFloat(), r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) },
        )
        return out
    }

    private fun finish(back: Bitmap, fore: Bitmap?, foreFraction: Float): TileArt {
        val composed = if (fore == null) back else back.copy(Bitmap.Config.ARGB_8888, true).also {
            val size = (H * foreFraction).roundToInt()
            val left = (W - size) / 2
            val top = (H - size) / 2
            Canvas(it).drawBitmap(fore, null, android.graphics.Rect(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val tint = dominant(composed)
        val backdrop = backdropOf(composed)
        if (composed !== back) composed.recycle()
        return TileArt(
            back = back.asImageBitmap(),
            fore = fore?.asImageBitmap(),
            foreFraction = foreFraction,
            tint = Color(tint),
            backdrop = backdrop.asImageBitmap(),
        )
    }

    private fun Canvas.drawCover(d: Drawable, w: Int, h: Int) {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = max(w / iw.toFloat(), h / ih.toFloat())
        val dw = (iw * scale).roundToInt()
        val dh = (ih * scale).roundToInt()
        val l = (w - dw) / 2
        val t = (h - dh) / 2
        d.setBounds(l, t, l + dw, t + dh)
        d.draw(this)
    }

    /** Saturation-weighted mean color: the hue a person would name when describing the tile. */
    private fun dominant(b: Bitmap): Int {
        val small = b.scale(24, 14)
        var r = 0.0; var g = 0.0; var bl = 0.0; var total = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 128) continue
            android.graphics.Color.colorToHSV(c, hsv)
            val wgt = hsv[1] * hsv[2] * hsv[2] + 0.04
            r += android.graphics.Color.red(c) * wgt
            g += android.graphics.Color.green(c) * wgt
            bl += android.graphics.Color.blue(c) * wgt
            total += wgt
        }
        if (small !== b) small.recycle()
        if (total == 0.0) return GRAPHITE_TOP
        return android.graphics.Color.rgb((r / total).roundToInt(), (g / total).roundToInt(), (bl / total).roundToInt())
    }

    /** A plate behind a logo that has no background of its own: the logo's hue, deep and calm. */
    private fun plateFromTint(tint: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(tint, hsl)
        if (hsl[1] < 0.18f) return GRAPHITE_TOP
        hsl[1] = hsl[1].coerceAtMost(0.55f)
        hsl[2] = 0.24f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun backdropOf(src: Bitmap): Bitmap {
        val w = 64
        val h = 36
        val small = src.scale(w, h)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { Images.boxBlur(px, w, h, 4) }
        val blurred = createBitmap(w, h)
        blurred.setPixels(px, 0, w, 0, 0, w, h)
        // Lift saturation and pull brightness down: ambient light, not a picture. Pale artwork
        // (white banners) would spread a grey haze, so it gets dimmed much further.
        val hsv = FloatArray(3)
        var sat = 0f; var value = 0f
        for (c in px) { android.graphics.Color.colorToHSV(c, hsv); sat += hsv[1]; value += hsv[2] }
        sat /= px.size; value /= px.size
        val gain = if (sat < 0.2f) 0.82f - 0.5f * value * (1f - sat / 0.2f) else 0.82f
        val cm = ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(ColorMatrix().apply { setScale(gain, gain, gain, 1f) })
        }
        val bw = 192
        val bh = 108
        val out = createBitmap(bw, bh)
        Canvas(out).apply {
            drawBitmap(
                blurred, null, android.graphics.Rect(0, 0, bw, bh),
                Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) },
            )
            // Light kept up top where the shelf is, near-black under the grid.
            drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, bh.toFloat(),
                    intArrayOf(black(0.10f), black(0.32f), black(0.80f), black(0.94f)),
                    floatArrayOf(0f, 0.42f, 0.72f, 1f), Shader.TileMode.CLAMP,
                )
            })
            // The left edge shaded so the clock reads over bright artwork.
            drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, bw * 0.55f, 0f, black(0.38f), 0, Shader.TileMode.CLAMP)
            })
        }
        small.recycle(); blurred.recycle()
        return out
    }

    private fun black(alpha: Float) = android.graphics.Color.argb((alpha * 255).roundToInt(), 0, 0, 0)
}
