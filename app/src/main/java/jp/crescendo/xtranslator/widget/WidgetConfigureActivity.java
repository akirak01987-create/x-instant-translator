package jp.crescendo.xtranslator.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.WidgetConfigEntity;

/** 2種類のウィジェット(一覧/最新1件)に共通の設定画面。表示条件(キーワード)と見出しを設定する。 */
public class WidgetConfigureActivity extends AppCompatActivity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean isListWidget = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_configure);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            widgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        isListWidget = resolveIsListWidget();

        EditText titleField = findViewById(R.id.edit_widget_title);
        EditText keywordField = findViewById(R.id.edit_widget_keyword);

        AppDatabase db = AppDatabase.getInstance(this);
        AppExecutors.background(() -> {
            WidgetConfigEntity existing = db.widgetConfigDao().getById(widgetId);
            AppExecutors.main(() -> {
                if (existing != null) {
                    titleField.setText(existing.title);
                    keywordField.setText(existing.keyword);
                } else {
                    titleField.setText(isListWidget ? "ウォッチリスト" : "最新の通知");
                }
            });
        });

        findViewById(R.id.btn_widget_save).setOnClickListener(v -> save(titleField, keywordField));
    }

    private boolean resolveIsListWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
        if (info == null || info.provider == null) return true;
        ComponentName listComponent = new ComponentName(this, ListWidgetProvider.class);
        return info.provider.equals(listComponent);
    }

    private void save(EditText titleField, EditText keywordField) {
        String title = titleField.getText().toString().trim();
        String keyword = keywordField.getText().toString().trim();

        WidgetConfigEntity entity = new WidgetConfigEntity();
        entity.widgetId = widgetId;
        entity.title = TextUtils.isEmpty(title) ? (isListWidget ? "ウォッチリスト" : "最新の通知") : title;
        entity.keyword = keyword;
        entity.maxItems = 20;

        AppDatabase db = AppDatabase.getInstance(this);
        AppExecutors.background(() -> {
            db.widgetConfigDao().insert(entity);
            WidgetUpdater.updateWidget(this, widgetId, isListWidget);
            AppExecutors.main(() -> {
                Intent result = new Intent();
                result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                setResult(RESULT_OK, result);
                finish();
            });
        });
    }
}
