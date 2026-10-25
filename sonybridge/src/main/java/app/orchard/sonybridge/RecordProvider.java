package app.orchard.sonybridge;
import android.content.*;
import android.database.*;
import android.net.Uri;
public class RecordProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"record"});
        String record = getContext().getSharedPreferences("probe", 0).getString("last", "");
        try {
            org.json.JSONArray history = new org.json.JSONArray(getContext().getSharedPreferences("probe", 0).getString("history", "[]"));
            for (int i = 0; i < history.length(); i++) cursor.addRow(new Object[]{history.getJSONObject(i).toString()});
            org.json.JSONObject records = new org.json.JSONObject(getContext().getSharedPreferences("probe", 0).getString("records", "{}"));
            java.util.Iterator<String> keys = records.keys();
            if (history.length() == 0) while (keys.hasNext()) cursor.addRow(new Object[]{records.getJSONObject(keys.next()).toString()});
            if (cursor.getCount() == 0 && !record.isEmpty()) cursor.addRow(new Object[]{record});
        } catch (org.json.JSONException ignored) { }
        return cursor;
    }
    @Override public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        getContext().enforceCallingOrSelfPermission("app.orchard.tv.permission.PLAYBACK_BRIDGE", "Only the paired launcher may configure recording");
        android.content.SharedPreferences.Editor editor = getContext().getSharedPreferences("probe", 0).edit();
        if ("configure".equals(method) && extras != null) {
            boolean enabled = extras.getBoolean("enabled", false);
            editor.putBoolean("recording", enabled);
            if (!enabled) editor.remove("pendingTencent");
            editor.apply();
            if (enabled) RecoveryReceiver.ensureRunning(getContext());
            else getContext().stopService(new Intent(getContext(), ProbeService.class));
        } else if ("clear".equals(method)) {
            editor.remove("history").remove("records").remove("last").remove("pendingTencent").remove("lastError").apply();
        } else throw new UnsupportedOperationException();
        return android.os.Bundle.EMPTY;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.aerial.playback"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
