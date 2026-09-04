package jp.crescendo.xtranslator.service;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jp.crescendo.xtranslator.data.Prefs;

/** Gemini API(Google AI Studio発行のAPIキー)へ通知履歴の一部を渡して分析させる。
 * 端末からGoogleのサーバーへ直接HTTPSでリクエストを送るだけの軽量な実装で、
 * 中継サーバーは持たない。 */
public final class GeminiAnalyzer {
    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int TIMEOUT_MS = 30_000;
    /** 1回のリクエストに含める投稿の最大件数。多すぎるとリクエストが重くなり、
     * 無料枠のトークン上限にも達しやすくなるため頭打ちにする。 */
    public static final int MAX_POSTS = 40;

    private GeminiAnalyzer() {}

    public static class GeminiException extends Exception {
        public GeminiException(String message) {
            super(message);
        }
    }

    /** バックグラウンドスレッドから呼び出すこと(ネットワークI/Oを行う)。 */
    public static String analyzeUsdJpy(Context context, List<String> posts) throws GeminiException {
        String apiKey = Prefs.getGeminiApiKey(context);
        if (apiKey.isEmpty()) {
            throw new GeminiException("アプリ設定画面でGemini APIキーを設定してください");
        }
        if (posts.isEmpty()) {
            throw new GeminiException("分析対象の投稿がありません(表示中の履歴が空です)");
        }

        String prompt = buildPrompt(posts);
        String model = Prefs.getGeminiModel(context);

        try {
            JSONObject part = new JSONObject().put("text", prompt);
            JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
            JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));

            URL url = new URL(String.format(ENDPOINT_TEMPLATE, model, apiKey));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseText = readAll(stream);

                if (code < 200 || code >= 300) {
                    throw new GeminiException("Gemini APIエラー(HTTP " + code + "): " + shorten(responseText, 300));
                }
                return parseResponse(responseText);
            } finally {
                conn.disconnect();
            }
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException("通信中にエラーが発生しました: " + e);
        }
    }

    private static String buildPrompt(List<String> posts) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下はX(旧Twitter)の経済速報アカウントから届いた直近の投稿(日本語訳)の一覧です。");
        sb.append("これらの中からドル円(USD/JPY)相場に影響しそうな情報を中心に、日本語で簡潔に分析・要約してください。");
        sb.append("ドル円に関連する投稿が無ければ、その旨を述べたうえで全体の傾向を短くまとめてください。\n\n");
        int count = Math.min(posts.size(), MAX_POSTS);
        for (int i = 0; i < count; i++) {
            sb.append("・").append(posts.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static String parseResponse(String responseText) throws GeminiException {
        try {
            JSONObject json = new JSONObject(responseText);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new GeminiException("応答が空でした: " + shorten(responseText, 300));
            }
            JSONObject firstContent = candidates.getJSONObject(0).optJSONObject("content");
            if (firstContent == null) {
                throw new GeminiException("応答の形式が想定と異なります: " + shorten(responseText, 300));
            }
            JSONArray parts = firstContent.optJSONArray("parts");
            if (parts == null) return "";
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                result.append(parts.getJSONObject(i).optString("text", ""));
            }
            return result.toString().trim();
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException("応答の解析に失敗しました: " + e);
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
