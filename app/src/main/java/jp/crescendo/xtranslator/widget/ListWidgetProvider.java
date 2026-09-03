package jp.crescendo.xtranslator.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;

/** ウォッチリスト風に、条件に合う通知を一覧表示するウィジェット。 */
public class ListWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            WidgetUpdater.updateWidget(context, id, true);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        AppDatabase db = AppDatabase.getInstance(context);
        AppExecutors.background(() -> {
            for (int id : appWidgetIds) db.widgetConfigDao().deleteById(id);
        });
    }
}
