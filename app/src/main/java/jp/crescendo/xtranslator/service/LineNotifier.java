package jp.crescendo.xtranslator.service;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import jp.crescendo.xtranslator.data.Prefs;

/** LINE公式アカウント(Messaging API)への配信。個々の友だちのユーザーIDを管理する必要がない
 * ブロードキャスト配信(その公式アカウントを友だち追加した全員へ一斉送信)を使う。事前にLINE
 * Developersコンソールでチャンネルアクセストークンを発行し、アプリ設定画面に保存しておく必要がある。 */
public final class LineNotifier {
    private static final String TAG = "LineNotifier";
    private static final String BROADCAST_URL = "https://api.line.me/v2/bot/message/broadcast";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_MESSAGE_LENGTH = 4900; // LINEのテキストメッセージ上限(5000文字)に余裕を持たせる

    private LineNotifier() {}

    /** バックグラウンドスレッドから呼び出すこと(ネットワークI/Oを行う)。トークン未設定や
     * 通信失敗時も例外は投げず、ログにだけ残して他の通知処理に影響しないようにする。 */
    public static void broadcast(Context context, String message) {
        String token = Prefs.getLineChannelAccessToken(context);
        if (token.isEmpty() || message == null || message.isEmpty()) return;

        HttpURLConnection conn = null;
        try {
            JSONObject text = new JSONObject();
            text.put("type", "text");
            text.put("text", message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message);
            JSONArray messages = new JSONArray();
            messages.put(text);
            JSONObject body = new JSONObject();
            body.put("messages", messages);

            URL url = new URL(BROADCAST_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "LINE配信に失敗しました: HTTP " + code);
            }
        } catch (Exception e) {
            Log.w(TAG, "LINE配信中に例外が発生しました", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
