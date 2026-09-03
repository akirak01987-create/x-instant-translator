package jp.crescendo.xtranslator.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.app.PendingIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.WidgetConfigEntity;
import jp.crescendo.xtranslator.ui.MainActivity;

/** 通知の保存・削除のたびに全ウィジェットの表示内容を更新する。 */
public final class WidgetUpdater {
    private static final String TAG = "WidgetUpdater";

    private WidgetUpdater() {}

    public static void updateAll(Context context) {
        AppExecutors.background(() -> {
            Context app = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(app);

            int[] listIds = manager.getAppWidgetIds(new ComponentName(app, ListWidgetProvider.class));
            for (int id : listIds) safeUpdateListWidget(app, manager, id);

            int[] singleIds = manager.getAppWidgetIds(new ComponentName(app, SingleWidgetProvider.class));
            for (int id : singleIds) safeUpdateSingleWidget(app, manager, id);
        });
    }

    public static void updateWidget(Context context, int widgetId, boolean isListWidget) {
        AppExecutors.background(() -> {
            Context app = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            if (isListWidget) {
                safeUpdateListWidget(app, manager, widgetId);
            } else {
                safeUpdateSingleWidget(app, manager, widgetId);
            }
        });
    }

    /** 1件のウィジェット更新で例外が起きても、ウィジェット全体が「読み込みエラー」表示のまま
     * 固まらないよう、最低限の表示にフォールバックする。 */
    private static void safeUpdateListWidget(Context context, AppWidgetManager manager, int widgetId) {
        try {
            updateListWidget(context, manager, widgetId);
        } catch (Exception e) {
            Log.e(TAG, "updateListWidget failed for widgetId=" + widgetId, e);
            try {
                RemoteViews fallback = new RemoteViews(context.getPackageName(), R.layout.widget_list);
                fallback.setTextViewText(R.id.widget_list_title, "ウォッチリスト");
                manager.updateAppWidget(widgetId, fallback);
            } catch (Exception ignored) {
                // これ以上は打つ手がない
            }
        }
    }

    private static void safeUpdateSingleWidget(Context context, AppWidgetManager manager, int widgetId) {
        try {
            updateSingleWidget(context, manager, widgetId);
        } catch (Exception e) {
            Log.e(TAG, "updateSingleWidget failed for widgetId=" + widgetId, e);
            try {
                RemoteViews fallback = new RemoteViews(context.getPackageName(), R.layout.widget_single);
                fallback.setTextViewText(R.id.widget_single_title, "最新の通知");
                manager.updateAppWidget(widgetId, fallback);
            } catch (Exception ignored) {
                // これ以上は打つ手がない
            }
        }
    }

    static void updateListWidget(Context context, AppWidgetManager manager, int widgetId) {
        AppDatabase db = AppDatabase.getInstance(context);
        WidgetConfigEntity config = db.widgetConfigDao().getById(widgetId);
        String title = config != null && !TextUtils.isEmpty(config.title) ? config.title : "ウォッチリスト";

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list);
        views.setTextViewText(R.id.widget_list_title, title);

        Intent serviceIntent = new Intent(context, ListWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        serviceIntent.setData(Uri.parse("widget://list/" + widgetId));
        views.setRemoteAdapter(R.id.widget_list_view, serviceIntent);
        views.setEmptyView(R.id.widget_list_view, R.id.widget_list_empty);

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent template = PendingIntent.getActivity(context, 100_000 + widgetId, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setPendingIntentTemplate(R.id.widget_list_view, template);

        manager.updateAppWidget(widgetId, views);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list_view);
    }

    static void updateSingleWidget(Context context, AppWidgetManager manager, int widgetId) {
        AppDatabase db = AppDatabase.getInstance(context);
        WidgetConfigEntity config = db.widgetConfigDao().getById(widgetId);
        String title = config != null && !TextUtils.isEmpty(config.title) ? config.title : "最新の通知";
        String filterIds = config != null ? config.filterIds : "";

        NotificationEntity latest = null;
        List<NotificationEntity> all = db.notificationDao().getAll();
        for (NotificationEntity item : all) {
            if (WidgetFilter.matches(item, filterIds)) {
                latest = item;
                break;
            }
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_single);
        views.setTextViewText(R.id.widget_single_title, title);
        if (latest != null) {
            boolean hasTranslation = latest.wasTranslated && !TextUtils.isEmpty(latest.translatedText);
            String body = hasTranslation ? latest.translatedText : latest.originalText;
            views.setTextViewText(R.id.widget_single_author,
                    TextUtils.isEmpty(latest.author) ? "投稿者不明" : latest.author);
            views.setTextViewText(R.id.widget_single_body, body);
            views.setInt(R.id.widget_single_accent, "setBackgroundColor", latest.textColor);
            views.setViewVisibility(R.id.widget_single_accent, View.VISIBLE);
            views.setViewVisibility(R.id.widget_single_empty, View.GONE);
            views.setViewVisibility(R.id.widget_single_author, View.VISIBLE);
            views.setViewVisibility(R.id.widget_single_body, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_single_accent, View.GONE);
            views.setViewVisibility(R.id.widget_single_empty, View.VISIBLE);
            views.setViewVisibility(R.id.widget_single_author, View.GONE);
            views.setViewVisibility(R.id.widget_single_body, View.GONE);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 200_000 + widgetId, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_single_root, pi);

        manager.updateAppWidget(widgetId, views);
    }
}
