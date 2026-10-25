package app.orchard.sonybridge;
import android.content.*;
import android.os.Build;
/** Boot/package recovery complements START_STICKY and the paired launcher's important binding. */
public class RecoveryReceiver extends BroadcastReceiver {
    static void ensureRunning(Context context) {
        if (!context.getSharedPreferences("probe", 0).getBoolean("recording", true)) return;
        Intent service = new Intent(context, ProbeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (RuntimeException restricted) {
            android.util.Log.w("AerialSonyBridge", "Service start restricted; paired launcher binding will retry");
        }
    }
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) ensureRunning(context);
    }
}
