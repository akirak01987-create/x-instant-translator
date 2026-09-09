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

/** DeepL APIを通知の翻訳エンジンとして使う場合の実装。端末からDeepLのサーバーへ直接HTTPSで
 * リクエストを送るだけの軽量な実装で、中継サーバーは持たない。 */
public final class DeepLTranslateClient {
    /** 無料プランのAPIキーは末尾が":fx"になっており、Free用のエンドポイントを使う必要がある。 */
    private static final String FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate";
    private static final String PRO_ENDPOINT = "https://api.deepl.com/v2/translate";
    private static final int TIMEOUT_MS = 15_000;

    private DeepLTranslateClient() {}

    public static class DeepLException extends Exception {
        public DeepLException(String message) {
            super(message);
        }
    }

    /** バックグラウンドスレッドから呼び出すこと(ネットワークI/Oを行う)。 */
    public static String translate(Context context, String text) throws DeepLException {
        String apiKey = Prefs.getDeepLApiKey(context);
        if (apiKey.isEmpty()) {
            throw new DeepLException("アプリ設定画面でDeepL APIキーを設定してください");
        }
        String endpoint = apiKey.trim().endsWith(":fx") ? FREE_ENDPOINT : PRO_ENDPOINT;

        HttpURLConnection conn = null;
        try {
            String body = "text=" + URLEncoder.encode(text, "UTF-8") + "&source_lang=EN&target_lang=JA";

            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = readAll(stream);

            if (code < 200 || code >= 300) {
                throw new DeepLException("DeepL APIエラー(HTTP " + code + "): " + shorten(responseText, 300));
            }

            JSONObject json = new JSONObject(responseText);
            JSONArray translations = json.getJSONArray("translations");
            if (translations.length() == 0) throw new DeepLException("応答が空でした: " + shorten(responseText, 300));
            return translations.getJSONObject(0).optString("text", "").trim();
        } catch (DeepLException e) {
            throw e;
        } catch (Exception e) {
            throw new DeepLException("通信中にエラーが発生しました: " + e);
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
