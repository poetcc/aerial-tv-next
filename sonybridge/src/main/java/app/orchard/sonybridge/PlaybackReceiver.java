package app.orchard.sonybridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class PlaybackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { PlaybackStore.accept(context, intent); }
}
