package jp.crescendo.xtranslator.service;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jp.crescendo.xtranslator.data.Prefs;

/** Google Cloud Translation API(v2, Basic)を通知の翻訳エンジンとして使う場合の実装。
 * 端末からGoogleのサーバーへ直接HTTPSでリクエストを送るだけの軽量な実装で、
 * 中継サーバーは持たない。 */
public final class GoogleTranslateClient {
    private static final String ENDPOINT = "https://translation.googleapis.com/language/translate2";
    private static final int TIMEOUT_MS = 15_000;

    private GoogleTranslateClient() {}

    public static class GoogleTranslateException extends Exception {
        public GoogleTranslateException(String message) {
            super(message);
        }
    }

    /** バックグラウンドスレッドから呼び出すこと(ネットワークI/Oを行う)。 */
    public static String translate(Context context, String text) throws GoogleTranslateException {
        String apiKey = Prefs.getGoogleTranslateApiKey(context);
        if (apiKey.isEmpty()) {
            throw new GoogleTranslateException("アプリ設定画面でGoogle Cloud Translation APIキーを設定してください");
        }

        HttpURLConnection conn = null;
        try {
            String body = "q=" + URLEncoder.encode(text, "UTF-8")
                    + "&source=en&target=ja&format=text&key=" + URLEncoder.encode(apiKey, "UTF-8");

            URL url = new URL(ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = readAll(stream);

            if (code < 200 || code >= 300) {
                throw new GoogleTranslateException("Google Translate APIエラー(HTTP " + code + "): " + shorten(responseText, 300));
            }

            JSONObject json = new JSONObject(responseText);
            JSONArray translations = json.getJSONObject("data").getJSONArray("translations");
            if (translations.length() == 0) throw new GoogleTranslateException("応答が空でした: " + shorten(responseText, 300));
            return translations.getJSONObject(0).optString("translatedText", "").trim();
        } catch (GoogleTranslateException e) {
            throw e;
        } catch (Exception e) {
            throw new GoogleTranslateException("通信中にエラーが発生しました: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
