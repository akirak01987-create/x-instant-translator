package jp.crescendo.xtranslator.widget;

import android.text.TextUtils;

import java.util.Locale;

import jp.crescendo.xtranslator.data.NotificationEntity;

/** ウィジェットに表示する通知の絞り込み。投稿者名または本文にキーワードが部分一致するかを見る。 */
public final class WidgetFilter {
    private WidgetFilter() {}

    public static boolean matches(NotificationEntity item, String keyword) {
        if (TextUtils.isEmpty(keyword)) return true;
        String needle = keyword.trim().toLowerCase(Locale.getDefault());
        if (needle.isEmpty()) return true;

        return contains(item.author, needle)
                || contains(item.translatedText, needle)
                || contains(item.originalText, needle);
    }

    private static boolean contains(String value, String needleLower) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(needleLower);
    }
}
