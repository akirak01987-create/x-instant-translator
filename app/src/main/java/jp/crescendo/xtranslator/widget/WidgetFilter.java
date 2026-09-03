package jp.crescendo.xtranslator.widget;

import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.WidgetConfigEntity;
import jp.crescendo.xtranslator.filter.FilterMatcher;

/** ウィジェットに表示する通知を、保存時に一致したフィルター(filterId)で絞り込む。 */
public final class WidgetFilter {
    private WidgetFilter() {}

    public static boolean matches(NotificationEntity item, long widgetFilterId) {
        if (widgetFilterId == WidgetConfigEntity.FILTER_ALL) return true;
        if (widgetFilterId == WidgetConfigEntity.FILTER_DEFAULT_ONLY) {
            return item.filterId == FilterMatcher.DEFAULT_FILTER_ID;
        }
        return item.filterId == widgetFilterId;
    }
}
