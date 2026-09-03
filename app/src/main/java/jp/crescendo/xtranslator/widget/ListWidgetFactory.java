package jp.crescendo.xtranslator.widget;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.WidgetConfigEntity;

/** ウィジェット一覧の各行を、DBの内容とウィジェット設定(キーワード)から組み立てる。
 * RemoteViewsFactoryのコールバックはバインダースレッドで呼ばれるため、DBへの同期アクセスが許される。 */
class ListWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private final int widgetId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.JAPAN);
    private final List<NotificationEntity> items = new ArrayList<>();

    ListWidgetFactory(Context context, int widgetId) {
        this.context = context;
        this.widgetId = widgetId;
    }

    @Override
    public void onCreate() {
        // onDataSetChangedで読み込むため、ここでは何もしない。
    }

    @Override
    public void onDataSetChanged() {
        items.clear();
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            WidgetConfigEntity config = db.widgetConfigDao().getById(widgetId);
            String filterIds = config != null ? config.filterIds : "";
            int maxItems = config != null && config.maxItems > 0 ? config.maxItems : 20;

            for (NotificationEntity item : db.notificationDao().getAll()) {
                if (WidgetFilter.matches(item, filterIds)) {
                    items.add(item);
                    if (items.size() >= maxItems) break;
                }
            }
        } catch (Exception e) {
            // データ読み込みに失敗しても、ウィジェット全体を「読み込みエラー」にしない。
            // 空一覧のまま(空状態の表示)にフォールバックする。
            Log.e("ListWidgetFactory", "onDataSetChanged failed for widgetId=" + widgetId, e);
            items.clear();
        }
    }

    @Override
    public void onDestroy() {
        items.clear();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        try {
            NotificationEntity item = items.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);

            String author = TextUtils.isEmpty(item.author) ? "投稿者不明" : item.author;
            views.setTextViewText(R.id.widget_item_meta, timeFormat.format(item.receivedAt) + "　" + author);

            boolean hasTranslation = item.wasTranslated && !TextUtils.isEmpty(item.translatedText);
            views.setTextViewText(R.id.widget_item_body, hasTranslation ? item.translatedText : item.originalText);
            views.setInt(R.id.widget_item_accent, "setBackgroundColor", item.textColor);

            Intent fillInIntent = new Intent();
            views.setOnClickFillInIntent(R.id.widget_item_body, fillInIntent);
            return views;
        } catch (Exception e) {
            // 1件の描画に失敗しても一覧全体を巻き添えにしない。
            Log.e("ListWidgetFactory", "getViewAt failed for widgetId=" + widgetId + " position=" + position, e);
            RemoteViews fallback = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);
            fallback.setTextViewText(R.id.widget_item_body, "");
            return fallback;
        }
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
