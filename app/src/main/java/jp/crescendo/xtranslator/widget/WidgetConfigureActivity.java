package jp.crescendo.xtranslator.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.FilterEntity;
import jp.crescendo.xtranslator.data.WidgetConfigEntity;
import jp.crescendo.xtranslator.filter.FilterMatcher;

/** 2種類のウィジェット(一覧/最新1件)に共通の設定画面。表示条件(フィルター選択)と見出しを設定する。 */
public class WidgetConfigureActivity extends AppCompatActivity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean isListWidget = true;

    private Spinner filterSpinner;
    private final List<Long> filterIds = new ArrayList<>();
    private final List<String> filterLabels = new ArrayList<>();

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
        filterSpinner = findViewById(R.id.spinner_widget_filter);

        AppDatabase db = AppDatabase.getInstance(this);
        AppExecutors.background(() -> {
            List<FilterEntity> filters = db.filterDao().getAll();
            WidgetConfigEntity existing = db.widgetConfigDao().getById(widgetId);
            AppExecutors.main(() -> {
                setupFilterSpinner(filters, existing != null ? existing.filterId : WidgetConfigEntity.FILTER_ALL);
                if (existing != null) {
                    titleField.setText(existing.title);
                } else {
                    titleField.setText(isListWidget ? "ウォッチリスト" : "最新の通知");
                }
            });
        });

        findViewById(R.id.btn_widget_save).setOnClickListener(v -> save(titleField));
    }

    private void setupFilterSpinner(List<FilterEntity> filters, long selectedFilterId) {
        filterIds.clear();
        filterLabels.clear();
        filterIds.add(WidgetConfigEntity.FILTER_ALL);
        filterLabels.add("すべての通知");
        filterIds.add(WidgetConfigEntity.FILTER_DEFAULT_ONLY);
        filterLabels.add("デフォルト設定が適用された通知のみ");
        for (FilterEntity f : filters) {
            filterIds.add(f.id);
            filterLabels.add(FilterMatcher.describe(f));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filterLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(adapter);

        int position = filterIds.indexOf(selectedFilterId);
        filterSpinner.setSelection(position >= 0 ? position : 0);
    }

    private boolean resolveIsListWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
        if (info == null || info.provider == null) return true;
        ComponentName listComponent = new ComponentName(this, ListWidgetProvider.class);
        return info.provider.equals(listComponent);
    }

    private void save(EditText titleField) {
        String title = titleField.getText().toString().trim();
        int position = filterSpinner.getSelectedItemPosition();
        long selectedFilterId = position >= 0 && position < filterIds.size()
                ? filterIds.get(position) : WidgetConfigEntity.FILTER_ALL;

        WidgetConfigEntity entity = new WidgetConfigEntity();
        entity.widgetId = widgetId;
        entity.title = TextUtils.isEmpty(title) ? (isListWidget ? "ウォッチリスト" : "最新の通知") : title;
        entity.filterId = selectedFilterId;
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
