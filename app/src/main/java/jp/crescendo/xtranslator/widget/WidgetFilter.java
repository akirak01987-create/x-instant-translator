package jp.crescendo.xtranslator.widget;

import android.text.TextUtils;

import jp.crescendo.xtranslator.data.NotificationEntity;

/** ウィジェットに表示する通知を、保存時に一致したフィルター(filterId)の複数選択、および受信時刻の新しさで絞り込む。 */
public final class WidgetFilter {
    /** ウィジェットには直近この時間内に届いた通知だけを表示する。アプリ本体の履歴保存期間とは別。 */
    public static final long DISPLAY_WINDOW_MILLIS = 2 * 60 * 60 * 1000L;

    private WidgetFilter() {}

    /** filterIdsCsvはカンマ区切りのフィルターID一覧。空/nullなら常にすべて対象とする。 */
    public static boolean matches(NotificationEntity item, String filterIdsCsv) {
        if (!withinDisplayWindow(item)) return false;
        if (TextUtils.isEmpty(filterIdsCsv)) return true;
        for (String part : filterIdsCsv.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                if (Long.parseLong(p) == item.filterId) return true;
            } catch (NumberFormatException ignored) {
                // 壊れた値は無視する
            }
        }
        return false;
    }

    public static boolean withinDisplayWindow(NotificationEntity item) {
        return System.currentTimeMillis() - item.receivedAt <= DISPLAY_WINDOW_MILLIS;
    }
}
