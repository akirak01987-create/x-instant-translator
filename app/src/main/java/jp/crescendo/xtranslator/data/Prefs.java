package jp.crescendo.xtranslator.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "app_prefs";
    private static final String KEY_MAX_HISTORY = "max_history_count";
    public static final int DEFAULT_MAX_HISTORY = 500;
    public static final int MIN_MAX_HISTORY = 10;
    public static final int MAX_MAX_HISTORY = 5000;

    private Prefs() {}

    public static int getMaxHistory(Context context) {
        return prefs(context).getInt(KEY_MAX_HISTORY, DEFAULT_MAX_HISTORY);
    }

    public static void setMaxHistory(Context context, int value) {
        int clamped = Math.max(MIN_MAX_HISTORY, Math.min(MAX_MAX_HISTORY, value));
        prefs(context).edit().putInt(KEY_MAX_HISTORY, clamped).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
