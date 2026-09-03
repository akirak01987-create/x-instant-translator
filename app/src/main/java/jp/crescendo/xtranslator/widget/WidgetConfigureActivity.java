package jp.crescendo.xtranslator.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.FilterEntity;
import jp.crescendo.xtranslator.data.WidgetConfigEntity;
import jp.crescendo.xtranslator.filter.FilterMatcher;

/** 2種類のウィジェット(一覧/最新1件)に共通の設定画面。表示条件(フィルターの複数選択)と見出しを設定する。 */
public class WidgetConfigureActivity extends AppCompatActivity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean isListWidget = true;

    private ListView filterList;
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
        filterList = findViewById(R.id.list_widget_filters);
        filterList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        AppDatabase db = AppDatabase.getInstance(this);
        AppExecutors.background(() -> {
            List<FilterEntity> filters = db.filterDao().getAll();
            WidgetConfigEntity existing = db.widgetConfigDao().getById(widgetId);
            AppExecutors.main(() -> {
                setupFilterChecklist(filters, existing);
                if (existing != null) {
                    titleField.setText(existing.title);
                } else {
                    titleField.setText(isListWidget ? "ウォッチリスト" : "最新の通知");
                }
            });
        });

        findViewById(R.id.btn_widget_save).setOnClickListener(v -> save(titleField));
    }

    private void setupFilterChecklist(List<FilterEntity> filters, WidgetConfigEntity existing) {
        filterIds.clear();
        filterLabels.clear();
        filterIds.add(FilterMatcher.DEFAULT_FILTER_ID);
        filterLabels.add("デフォルト設定(どのフィルターにも一致しない投稿)");
        for (FilterEntity f : filters) {
            filterIds.add(f.id);
            filterLabels.add(FilterMatcher.describe(f));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, filterLabels);
        filterList.setAdapter(adapter);

        // 未設定(新規追加時)はすべてチェック=すべての通知を対象にする。既存設定があれば保存済みのIDだけ復元する。
        Set<Long> checked;
        if (existing == null || TextUtils.isEmpty(existing.filterIds)) {
            checked = new HashSet<>(filterIds);
        } else {
            checked = new HashSet<>();
            for (String part : existing.filterIds.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                try {
                    checked.add(Long.parseLong(p));
                } catch (NumberFormatException ignored) {
                    // 無視
                }
            }
        }
        for (int i = 0; i < filterIds.size(); i++) {
            filterList.setItemChecked(i, checked.contains(filterIds.get(i)));
        }
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

        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < filterIds.size(); i++) {
            if (!filterList.isItemChecked(i)) continue;
            if (csv.length() > 0) csv.append(",");
            csv.append(filterIds.get(i));
        }
        if (csv.length() == 0) {
            Toast.makeText(this, "表示する通知を少なくとも1つ選択してください", Toast.LENGTH_SHORT).show();
            return;
        }

        WidgetConfigEntity entity = new WidgetConfigEntity();
        entity.widgetId = widgetId;
        entity.title = TextUtils.isEmpty(title) ? (isListWidget ? "ウォッチリスト" : "最新の通知") : title;
        entity.filterIds = csv.toString();
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
