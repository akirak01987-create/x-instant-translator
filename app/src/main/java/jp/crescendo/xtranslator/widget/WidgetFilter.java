package jp.crescendo.xtranslator.widget;

import android.text.TextUtils;

import jp.crescendo.xtranslator.data.NotificationEntity;

/** ウィジェットに表示する通知を、保存時に一致したフィルター(filterId)の複数選択で絞り込む。 */
public final class WidgetFilter {
    private WidgetFilter() {}

    /** filterIdsCsvはカンマ区切りのフィルターID一覧。空/nullなら常にすべて対象とする。 */
    public static boolean matches(NotificationEntity item, String filterIdsCsv) {
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
}
