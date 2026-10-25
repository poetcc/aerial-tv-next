package app.orchard.sonybridge;
import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
final class TencentCipher {
    private static final String[] ENCRYPTED = {"videoId", "videoVId", "videoName", "videoImgUrl", "horizontalImgUrl", "episodeId", "episodeName", "srcApp", "cmdInfo", "package_name"};
    static void refresh(Context context) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                // Public launcher configuration only. No device identifiers, account, viewing history or cookies are sent.
                connection = (HttpURLConnection) new URL("https://sonytyapi.v5tv.com/v1/core/config/").openConnection();
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() != 200) return;
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192];
                try (java.io.InputStream in = connection.getInputStream()) {
                    int count; while ((count = in.read(buffer)) >= 0) { if (bytes.size() + count > 524288) return; bytes.write(buffer, 0, count); }
                }
                String key = new JSONObject(bytes.toString("UTF-8")).getJSONObject("data").optString("tencent_history_key");
                int length = key.getBytes(StandardCharsets.UTF_8).length;
                if (length != 16 && length != 24 && length != 32) return;
                context.getSharedPreferences("probe", 0).edit().putString("tencentKey", key).apply();
                PlaybackStore.retryTencent(context);
            } catch (Exception ignored) { } finally { if (connection != null) connection.disconnect(); }
        }, "PublicPlaybackConfig").start();
    }
    static boolean decode(Context context, JSONObject record) {
        String key = context.getSharedPreferences("probe", 0).getString("tencentKey", "");
        if (key.isEmpty()) return false;
        int type = record.optInt("encryptType", 0);
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] iv = type == 1 ? Base64.decode(record.optString("encryptIv"), Base64.DEFAULT) : null;
            if (type != 0 && (type != 1 || iv.length < 12 || iv.length > 16)) return false;
            JSONObject decoded = new JSONObject(record.toString());
            for (String field : ENCRYPTED) {
                String input = record.optString(field); if (input.isEmpty()) continue;
                Cipher cipher = Cipher.getInstance(type == 0 ? "AES/ECB/PKCS5Padding" : "AES/GCM/NoPadding");
                if (type == 0) cipher.init(Cipher.DECRYPT_MODE, keySpec); else cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
                decoded.put(field, new String(cipher.doFinal(Base64.decode(input, Base64.DEFAULT)), StandardCharsets.UTF_8));
            }
            for (String field : ENCRYPTED) if (decoded.has(field)) record.put(field, decoded.get(field));
            record.put("decoded", true); return true;
        } catch (Exception ignored) { return false; }
    }
}
