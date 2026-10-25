package app.orchard.sonybridge;
import android.content.*;
import android.os.Bundle;
import android.util.Log;
import org.json.*;
final class PlaybackStore {
    static final String[] ACTIONS = {"com.gitvvideo.sony.action.ACTION_ADD_PLAYRECORD", "com.gitvdemo.video.action.ACTION_ADD_PLAYRECORD", "com.tv.history.add", "com.tv.history.secure.add", "com.tv.ali.history.add", "com.dangbei.history.add", "com.dangbei.humminglauncher.sdk.mango.history.ADD"};
    private static final String[] FIELDS = {"videoId", "videoVId", "videoName", "videoImgUrl", "horizontalImgUrl", "videoHImgUrl", "videoHorImgUrl", "episodeId", "episodeName", "episodeCount", "currentPosition", "position", "duration", "srcApp", "srcapp", "cmdstring", "cmdInfo", "customer", "playType", "chnId", "package_name", "encryptType", "encryptIv", "uri", "showStrId", "appname", "vid", "epid", "cid", "isFromLocal"};
    static synchronized void accept(Context context, Intent intent) {
        if (!context.getSharedPreferences("probe", 0).getBoolean("recording", true)) return;
        String action = intent.getAction();
        if (!java.util.Arrays.asList(ACTIONS).contains(action)) return;
        try {
            SharedPreferences probe = context.getSharedPreferences("probe", 0);
            probe.edit().putInt("seen", probe.getInt("seen", 0) + 1).putString("lastAction", action).apply();
            JSONObject record = new JSONObject().put("action", action).put("receivedAt", System.currentTimeMillis());
            Bundle extras = intent.getExtras(); if (extras == null) return;
            for (String field : FIELDS) {
                Object value = extras.get(field);
                if (value instanceof String && ((String) value).length() <= 16384 || value instanceof Number || value instanceof Boolean) record.put(field, value);
                else if (value instanceof byte[] && ((byte[]) value).length <= 32) record.put(field, android.util.Base64.encodeToString((byte[]) value, android.util.Base64.NO_WRAP));
            }
            if (action.equals("com.tv.history.secure.add")) {
                if (!TencentCipher.decode(context, record)) {
                    probe.edit().putString("pendingTencent", record.toString()).putString("lastError", "Tencent encrypted record needs compatible public configuration/IV").apply(); return;
                }
            }
            store(context, record);
        } catch (RuntimeException | JSONException failure) { Log.w("AerialSonyBridge", "Rejected malformed playback record", failure); }
    }
    static synchronized void retryTencent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("probe", 0);
        if (!prefs.getBoolean("recording", true)) return;
        try {
            String raw = prefs.getString("pendingTencent", ""); if (raw.isEmpty()) return;
            JSONObject record = new JSONObject(raw);
            if (TencentCipher.decode(context, record)) { store(context, record); prefs.edit().remove("pendingTencent").remove("lastError").apply(); }
        } catch (JSONException ignored) { }
    }
    private static void store(Context context, JSONObject record) throws JSONException {
        if (record.optString("videoName").trim().isEmpty()) return;
        String source = source(record); if (source.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("probe", 0);
        JSONObject records = new JSONObject(prefs.getString("records", "{}"));
        JSONObject old = records.optJSONObject(source);
        JSONObject comparable = new JSONObject(record.toString()); comparable.remove("receivedAt");
        if (old != null) {
            JSONObject previous = new JSONObject(old.toString()); previous.remove("receivedAt");
            if (previous.toString().equals(comparable.toString()) && record.optLong("receivedAt") - old.optLong("receivedAt") < 1000) return;
        }
        JSONArray history = new JSONArray(prefs.getString("history", "[]"));
        // Upgrade old per-app storage without losing any previous entries.
        if (history.length() == 0) {
            java.util.Iterator<String> keys = records.keys();
            while (keys.hasNext()) history.put(records.getJSONObject(keys.next()));
        }
        records.put(source, record);
        java.util.List<JSONObject> retained = new java.util.ArrayList<>();
        retained.add(record);
        String key = identity(record);
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.getJSONObject(i);
            if (!identity(item).equals(key)) retained.add(item);
        }
        java.util.Collections.sort(retained, (a, b) -> Long.compare(b.optLong("receivedAt"), a.optLong("receivedAt")));
        JSONArray bounded = new JSONArray();
        for (int i = 0; i < Math.min(100, retained.size()); i++) bounded.put(retained.get(i));
        int count = prefs.getInt("received", 0) + 1;
        prefs.edit().putInt("received", count).putString("last", record.toString()).putString("records", records.toString()).putString("history", bounded.toString()).apply();
        Intent forward = new Intent("app.orchard.tv.SONY_PLAYBACK").setPackage("app.orchard.tv").putExtra("record", record.toString());
        context.sendBroadcast(forward, "app.orchard.tv.permission.PLAYBACK_BRIDGE");
        Log.i("AerialSonyBridge", "Received playback record; package=" + source + " count=" + count);
    }
    private static String identity(JSONObject record) {
        String id = record.optString("videoVId");
        if (id.isEmpty()) id = record.optString("episodeId", record.optString("vid"));
        return source(record) + "\n" + record.optString("videoId") + "\n" + id;
    }
    private static String source(JSONObject record) {
        String action = record.optString("action");
        if (action.startsWith("com.gitvvideo.sony.action.")) return "com.gitvvideo.sony";
        if (action.startsWith("com.gitvdemo.video.action.")) return "com.gitvdemo.video";
        if (action.startsWith("com.tv.history.")) {
            String pkg = record.optString("package_name"); return pkg.equals("com.ktcp.tvvideo") || pkg.equals("com.ktcp.video") ? pkg : "";
        }
        if (action.equals("com.tv.ali.history.add")) {
            String pkg = record.optString("srcapp"); return pkg.equals("com.cibn.tv.sony") || pkg.equals("com.cibn.tv") ? pkg : "";
        }
        if (action.equals("com.dangbei.history.add") && record.optString("appname").equals("com.dangbei.mgtv")) return "com.hunantv.market";
        if (action.equals("com.dangbei.humminglauncher.sdk.mango.history.ADD")) return "com.hunantv.market";
        return "";
    }
}
