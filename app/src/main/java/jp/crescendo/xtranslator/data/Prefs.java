package jp.crescendo.xtranslator.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "app_prefs";
    private static final String KEY_RETENTION_MINUTES = "history_retention_minutes";
    public static final int DEFAULT_RETENTION_MINUTES = 1440; // 1日
    public static final int MIN_RETENTION_MINUTES = 1;
    public static final int MAX_RETENTION_MINUTES = 525_600; // 365日

    private Prefs() {}

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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
