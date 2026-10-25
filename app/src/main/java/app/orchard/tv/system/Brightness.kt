package app.orchard.tv.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Panel brightness. TVs keep it in different places:
 *
 * - Xiaomi (MediaTek, China firmware): Settings.Global "picture_backlight", 0–100, applied by the
 *   firmware's TV menu service as soon as it changes. Writing it needs WRITE_SECURE_SETTINGS.
 * - TCL: its own TV service only; Control Center opens TCL's picture panel.
 * - Others: the standard Settings.System screen brightness, 0–255. Writing it needs the "modify
 *   system settings" app-op. Both are granted by tools/install.sh; without them Control Center
 *   opens the TV's own picture settings instead.
 */
object Brightness {
    private const val XIAOMI_BACKLIGHT = "picture_backlight"

    private fun usesXiaomiBacklight(context: Context) =
        Settings.Global.getString(context.contentResolver, XIAOMI_BACKLIGHT) != null

    private fun isTcl(context: Context) =
        runCatching { context.packageManager.getPackageInfo("com.tcl.settings", 0) }.isSuccess

    /** Current level as 0..1, or null when Aerial cannot change it on this TV. */
    fun level(context: Context): Float? {
        val r = context.contentResolver
        return if (usesXiaomiBacklight(context)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return null
            Settings.Global.getInt(r, XIAOMI_BACKLIGHT, 50) / 100f
        } else {
            // TCL drives its backlight through its own TV service and ignores the system setting
            // (checked on a TCL MT5879 set); Control Center opens TCL's picture panel instead.
            // Before Android 6.0 WRITE_SETTINGS is granted at install.
            if (isTcl(context) || Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(context)) return null
            Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        }
    }

    /** Steps brightness one notch up or down and returns the new level. */
    fun adjust(context: Context, direction: Int): Float? {
        val current = level(context) ?: return null
        val next = (current + direction * 0.05f).coerceIn(0f, 1f)
        val r = context.contentResolver
        runCatching {
            if (usesXiaomiBacklight(context)) {
                Settings.Global.putInt(r, XIAOMI_BACKLIGHT, Math.round(next * 100))
            } else {
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, Math.round(next * 255))
            }
        }
        return level(context)
    }
}
