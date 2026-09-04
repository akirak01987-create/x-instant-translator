package jp.crescendo.xtranslator.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public final class Prefs {
    private static final String FILE = "app_prefs";
    private static final String KEY_RETENTION_MINUTES = "history_retention_minutes";
    private static final String KEY_HIDE_ORIGINAL_NOTIFICATION = "hide_original_notification";
    private static final String KEY_HISTORY_TEXT_SIZE_LEVEL = "history_text_size_level";
    private static final String KEY_SOUND_SLOT_URI_PREFIX = "sound_slot_uri_";
    private static final String KEY_LINE_CHANNEL_ACCESS_TOKEN = "line_channel_access_token";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_GEMINI_MODEL = "gemini_model";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    public static final int DEFAULT_RETENTION_MINUTES = 1440; // 1日
    public static final int MIN_RETENTION_MINUTES = 1;
    public static final int MAX_RETENTION_MINUTES = 525_600; // 365日

    /** 通知履歴の文字サイズ倍率。5段階(小〜大)。タブレットなど画面が大きい端末で標準サイズでは
     * 文字が小さすぎる場合に、履歴画面だけ拡大できるようにする。 */
    public static final float[] HISTORY_TEXT_SIZE_SCALES = {0.85f, 0.92f, 1.0f, 1.15f, 1.3f};
    public static final int DEFAULT_HISTORY_TEXT_SIZE_LEVEL = 2; // 標準

    private Prefs() {}

    /** trueなら、翻訳のポップアップを出す際にX側の元通知を自動的に消して二重表示を防ぐ。 */
    public static boolean isHideOriginalNotificationEnabled(Context context) {
        return prefs(context).getBoolean(KEY_HIDE_ORIGINAL_NOTIFICATION, false);
    }

    public static void setHideOriginalNotificationEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_HIDE_ORIGINAL_NOTIFICATION, value).apply();
    }

    public static int getRetentionMinutes(Context context) {
        return prefs(context).getInt(KEY_RETENTION_MINUTES, DEFAULT_RETENTION_MINUTES);
    }

    public static void setRetentionMinutes(Context context, int value) {
        int clamped = Math.max(MIN_RETENTION_MINUTES, Math.min(MAX_RETENTION_MINUTES, value));
        prefs(context).edit().putInt(KEY_RETENTION_MINUTES, clamped).apply();
    }

    public static long getRetentionCutoffMillis(Context context) {
        long minutes = getRetentionMinutes(context);
        return System.currentTimeMillis() - minutes * 60L * 1000L;
    }

    public static int getHistoryTextSizeLevel(Context context) {
        int level = prefs(context).getInt(KEY_HISTORY_TEXT_SIZE_LEVEL, DEFAULT_HISTORY_TEXT_SIZE_LEVEL);
        return Math.max(0, Math.min(HISTORY_TEXT_SIZE_SCALES.length - 1, level));
    }

    public static void setHistoryTextSizeLevel(Context context, int level) {
        int clamped = Math.max(0, Math.min(HISTORY_TEXT_SIZE_SCALES.length - 1, level));
        prefs(context).edit().putInt(KEY_HISTORY_TEXT_SIZE_LEVEL, clamped).apply();
    }

    public static float getHistoryTextScale(Context context) {
        return HISTORY_TEXT_SIZE_SCALES[getHistoryTextSizeLevel(context)];
    }

    /** スロットに端末の通知音一覧から選ばれた既定以外の音が割り当てられている場合、そのURIを返す。
     * 未設定(既定のまま)ならnull。 */
    public static Uri getSoundSlotUri(Context context, int index) {
        String raw = prefs(context).getString(KEY_SOUND_SLOT_URI_PREFIX + index, null);
        return raw == null ? null : Uri.parse(raw);
    }

    public static void setSoundSlotUri(Context context, int index, Uri uri) {
        prefs(context).edit().putString(KEY_SOUND_SLOT_URI_PREFIX + index, uri == null ? null : uri.toString()).apply();
    }

    /** LINE公式アカウント(Messaging API)のチャンネルアクセストークン。未設定なら空文字。 */
    public static String getLineChannelAccessToken(Context context) {
        return prefs(context).getString(KEY_LINE_CHANNEL_ACCESS_TOKEN, "");
    }

    public static void setLineChannelAccessToken(Context context, String token) {
        prefs(context).edit().putString(KEY_LINE_CHANNEL_ACCESS_TOKEN, token == null ? "" : token.trim()).apply();
    }

    /** Gemini API(Google AI Studio発行のAPIキー)。未設定なら空文字。 */
    public static String getGeminiApiKey(Context context) {
        return prefs(context).getString(KEY_GEMINI_API_KEY, "");
    }

    public static void setGeminiApiKey(Context context, String key) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, key == null ? "" : key.trim()).apply();
    }

    /** 使用するGeminiのモデル名。将来モデルが廃止された場合でもアプリ更新なしで変更できるよう設定可能にしている。 */
    public static String getGeminiModel(Context context) {
        String value = prefs(context).getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL);
        return (value == null || value.trim().isEmpty()) ? DEFAULT_GEMINI_MODEL : value.trim();
    }

    public static void setGeminiModel(Context context, String model) {
        prefs(context).edit().putString(KEY_GEMINI_MODEL, model == null ? "" : model.trim()).apply();
    }

    /** 設定変更の即時反映用。呼び出し側はリスナーへの強参照を保持し続けること(SharedPreferencesは
     * 弱参照でしか保持しないため)。 */
    public static void registerChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
