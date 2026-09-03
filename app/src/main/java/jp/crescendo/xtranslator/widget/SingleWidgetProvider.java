package jp.crescendo.xtranslator.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;

/** 条件に合う最新の1件だけを大きく表示する、ティッカー風のウィジェット。 */
public class SingleWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            WidgetUpdater.updateWidget(context, id, false);
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
