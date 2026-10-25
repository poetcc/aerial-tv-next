package app.orchard.sonybridge;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
public class ProbeService extends Service {
    private final PlaybackReceiver receiver = new PlaybackReceiver();
    // The legacy overload is used only below API 33, where export flags are unavailable.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("playback_probe", getString(R.string.channel), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "playback_probe") : new Notification.Builder(this);
        startForeground(1, builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.notification)).build());
        IntentFilter filter = new IntentFilter();
        for (String action : PlaybackStore.ACTIONS) filter.addAction(action);
        TencentCipher.refresh(this);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED); else registerReceiver(receiver, filter);
        android.util.Log.i("AerialSonyBridge", "Receiver ready; compatibilityPackage=" + getPackageName());
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return new Binder(); }
    @Override public void onDestroy() { unregisterReceiver(receiver); super.onDestroy(); }
    @Override protected void dump(FileDescriptor fd, PrintWriter out, String[] args) {
        out.println("Aerial local playback probe (not Sony software)");
        out.println("seen=" + getSharedPreferences("probe", 0).getInt("seen", 0));
        out.println("lastAction=" + getSharedPreferences("probe", 0).getString("lastAction", ""));
        out.println("received=" + getSharedPreferences("probe", 0).getInt("received", 0));
        out.println("lastError=" + getSharedPreferences("probe", 0).getString("lastError", ""));
        out.println("records=" + getSharedPreferences("probe", 0).getString("records", "{}"));
        out.println("last=" + getSharedPreferences("probe", 0).getString("last", "{}"));
    }
}
