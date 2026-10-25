package app.orchard.sonybridge;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
public class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent service = new Intent(this, ProbeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        TextView text = new TextView(this);
        text.setText(R.string.status); text.setTextSize(24); text.setPadding(50, 60, 50, 30);
        setContentView(text);
    }
}
