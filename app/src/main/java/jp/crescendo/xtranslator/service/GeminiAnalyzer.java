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

    /** デフォルトの質問(ユーザーが質問欄を空欄にした場合に使う)。 */
    public static final String DEFAULT_QUESTION =
            "ドル円(USD/JPY)相場に関連しそうな内容を中心に、日本語で簡潔に分析・要約してください。"
                    + "関連する投稿が無ければ、その旨を述べたうえで全体の傾向を短くまとめてください。";

    /** よく使う質問のテンプレート(値動きの理由を深掘りする3点セット)。AI分析画面のテンプレボタン用。 */
    public static final String TEMPLATE_QUESTION_MOVE_REASON =
            "今動き出した理由は？\nその理由でドル円はどうなるの？\nなぜこのような動きになってるのか説明してください。";

    /** バックグラウンドスレッドから呼び出すこと(ネットワークI/Oを行う)。questionが空ならDEFAULT_QUESTIONを使う。 */
    public static String analyze(Context context, List<String> posts, String question) throws GeminiException {
        if (posts.isEmpty()) {
            throw new GeminiException("分析対象の投稿がありません(選択した時間範囲に投稿がありません)");
        }
        String q = (question == null || question.trim().isEmpty()) ? DEFAULT_QUESTION : question.trim();
        return callGenerateContent(context, buildPrompt(posts, q));
    }

    /** 通知の翻訳エンジンとしてGeminiを使う場合に呼び出す。バックグラウンドスレッドから
     * 呼び出すこと(ネットワークI/Oを行う)。翻訳結果の文章だけを返すよう明示的に指示する。 */
    public static String translate(Context context, String text) throws GeminiException {
        String prompt = "以下の英文を自然な日本語に翻訳してください。説明・前置き・引用符は付けず、"
                + "翻訳結果の文章だけを出力してください。\n\n" + text;
        return callGenerateContent(context, prompt);
    }

    private static String callGenerateContent(Context context, String prompt) throws GeminiException {
        String apiKey = Prefs.getGeminiApiKey(context);
        if (apiKey.isEmpty()) {
            throw new GeminiException("アプリ設定画面でGemini APIキーを設定してください");
        }
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

    private static String buildPrompt(List<String> posts, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下はX(旧Twitter)の経済速報アカウントから届いた投稿(日本語訳)の一覧です(古い順)。\n\n");
        int count = Math.min(posts.size(), MAX_POSTS);
        int start = Math.max(0, posts.size() - count); // 件数が多い場合は直近側を優先する
        for (int i = start; i < posts.size(); i++) {
            sb.append("・").append(posts.get(i)).append("\n");
        }
        sb.append("\n上記の投稿を踏まえて、次の質問に日本語で答えてください。\n").append(question);
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
