package jp.crescendo.xtranslator.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "app_prefs";
    private static final String KEY_RETENTION_DAYS = "history_retention_days";
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 365;

    private Prefs() {}

    public static int getRetentionDays(Context context) {
        return prefs(context).getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
    }

    public static void setRetentionDays(Context context, int value) {
        int clamped = Math.max(MIN_RETENTION_DAYS, Math.min(MAX_RETENTION_DAYS, value));
        prefs(context).edit().putInt(KEY_RETENTION_DAYS, clamped).apply();
    }

    public static long getRetentionCutoffMillis(Context context) {
        long days = getRetentionDays(context);
        return System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
