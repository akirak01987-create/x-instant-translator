package jp.crescendo.xtranslator.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.DefaultFilterEntity;
import jp.crescendo.xtranslator.data.FilterEntity;
import jp.crescendo.xtranslator.filter.FilterMatcher;
import jp.crescendo.xtranslator.service.NotificationChannels;
import jp.crescendo.xtranslator.util.ColorPalette;
import jp.crescendo.xtranslator.util.InsetsUtil;

public class FilterEditActivity extends AppCompatActivity {
    public static final String EXTRA_FILTER_ID = "filter_id";
    public static final String EXTRA_IS_DEFAULT = "is_default";

    private long filterId = -1;
    private boolean isDefault;
    private FilterEntity editingFilter; // 既存フィルター編集時のみ非null

    private int selectedTextColor = Color.BLACK;
    private int selectedBackgroundColor = Color.WHITE;

    private View groupFilterOnly;
    private EditText editAuthor;
    private EditText editKeywords;
    private RadioGroup radioMatchMode;
    private RadioButton radioAny;
    private RadioButton radioAll;
    private CheckBox checkCaseSensitive;
    private Switch switchFilterEnabled;
    private Switch switchSound;
    private RadioGroup radioSoundOption;
    private RadioButton[] soundOptionButtons;
    private Switch switchPopup;
    private Switch switchTranslate;
    private LinearLayout rowTextColors;
    private LinearLayout rowBgColors;
    private TextView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_edit);
        InsetsUtil.applySystemBarPadding(findViewById(R.id.filter_edit_root), true, true);

        filterId = getIntent().getLongExtra(EXTRA_FILTER_ID, -1);
        isDefault = getIntent().getBooleanExtra(EXTRA_IS_DEFAULT, false);
        String screenTitle = isDefault ? "デフォルト設定" : (filterId == -1 ? "フィルターを追加" : "フィルターを編集");
        setTitle(screenTitle);
        ((TextView) findViewById(R.id.text_screen_title)).setText(screenTitle);

        bindViews();
        if (isDefault) {
            groupFilterOnly.setVisibility(View.GONE);
        }

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        Button deleteButton = findViewById(R.id.btn_delete);
        if (isDefault || filterId == -1) {
            deleteButton.setVisibility(View.GONE);
        } else {
            deleteButton.setOnClickListener(v -> confirmDelete());
        }

        renderColorRows();
        updatePreview();
        loadData();
    }

    private void bindViews() {
        groupFilterOnly = findViewById(R.id.group_filter_only);
        editAuthor = findViewById(R.id.edit_author);
        editKeywords = findViewById(R.id.edit_keywords);
        radioMatchMode = findViewById(R.id.radio_match_mode);
        radioAny = findViewById(R.id.radio_any);
        radioAll = findViewById(R.id.radio_all);
        checkCaseSensitive = findViewById(R.id.check_case_sensitive);
        switchFilterEnabled = findViewById(R.id.switch_enabled);
        switchSound = findViewById(R.id.switch_sound);
        radioSoundOption = findViewById(R.id.radio_sound_option);
        switchPopup = findViewById(R.id.switch_popup);
        switchTranslate = findViewById(R.id.switch_translate);
        rowTextColors = findViewById(R.id.row_text_colors);
        rowBgColors = findViewById(R.id.row_bg_colors);
        preview = findViewById(R.id.preview);

        radioAny.setChecked(true);
        switchFilterEnabled.setChecked(true);
        switchSound.setChecked(true);
        switchPopup.setChecked(true);
        switchTranslate.setChecked(true);
        buildSoundOptionRow();
    }

    /** 通知音の選択肢を5つ動的に生成する。表示名は端末の通知音一覧から取得するため、
     * レイアウトXMLでは固定テキストにできない。 */
    private void buildSoundOptionRow() {
        radioSoundOption.removeAllViews();
        soundOptionButtons = new RadioButton[NotificationChannels.SOUND_OPTION_COUNT];
        for (int i = 0; i < NotificationChannels.SOUND_OPTION_COUNT; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(NotificationChannels.soundLabel(this, i));
            soundOptionButtons[i] = button;
            radioSoundOption.addView(button);
        }
        soundOptionButtons[0].setChecked(true);
    }

    private void setSelectedSoundOption(int index) {
        if (soundOptionButtons == null) return;
        int clamped = Math.max(0, Math.min(soundOptionButtons.length - 1, index));
        soundOptionButtons[clamped].setChecked(true);
    }

    private int getSelectedSoundOption() {
        if (soundOptionButtons == null) return 0;
        for (int i = 0; i < soundOptionButtons.length; i++) {
            if (soundOptionButtons[i].isChecked()) return i;
        }
        return 0;
    }

    private void loadData() {
        AppDatabase db = AppDatabase.getInstance(this);
        if (isDefault) {
            AppExecutors.background(() -> {
                DefaultFilterEntity def = db.defaultFilterDao().getOrCreate();
                AppExecutors.main(() -> applyDefault(def));
            });
        } else if (filterId != -1) {
            AppExecutors.background(() -> {
                FilterEntity f = db.filterDao().getById(filterId);
                AppExecutors.main(() -> {
                    if (f != null) applyFilter(f);
                });
            });
        }
    }

    private void applyDefault(DefaultFilterEntity def) {
        switchSound.setChecked(def.soundEnabled);
        setSelectedSoundOption(def.soundOptionIndex);
        switchPopup.setChecked(def.popupEnabled);
        switchTranslate.setChecked(def.translateEnabled);
        selectedTextColor = def.textColor;
        selectedBackgroundColor = def.backgroundColor;
        renderColorRows();
        updatePreview();
    }

    private void applyFilter(FilterEntity f) {
        editingFilter = f;
        editAuthor.setText(f.authorPattern);
        editKeywords.setText(f.keywordsRaw);
        (f.matchAll ? radioAll : radioAny).setChecked(true);
        checkCaseSensitive.setChecked(f.caseSensitive);
        switchFilterEnabled.setChecked(f.enabled);
        switchSound.setChecked(f.soundEnabled);
        setSelectedSoundOption(f.soundOptionIndex);
        switchPopup.setChecked(f.popupEnabled);
        switchTranslate.setChecked(f.translateEnabled);
        selectedTextColor = f.textColor;
        selectedBackgroundColor = f.backgroundColor;
        renderColorRows();
        updatePreview();
    }

    private void renderColorRows() {
        buildColorRow(rowTextColors, selectedTextColor, color -> {
            selectedTextColor = color;
            renderColorRows();
            updatePreview();
        });
        buildColorRow(rowBgColors, selectedBackgroundColor, color -> {
            selectedBackgroundColor = color;
            renderColorRows();
            updatePreview();
        });
    }

    private interface ColorPick {
        void onPick(int color);
    }

    private void buildColorRow(LinearLayout row, int selected, ColorPick pick) {
        row.removeAllViews();
        int size = dp(36);
        int margin = dp(4);
        for (int color : ColorPalette.COLORS) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(dp(6));
            if (color == selected) {
                bg.setStroke(dp(3), 0xFF2962FF);
            } else {
                bg.setStroke(dp(1), 0xFFBDBDBD);
            }
            swatch.setBackground(bg);
            swatch.setOnClickListener(v -> pick.onPick(color));
            row.addView(swatch);
        }
    }

    private void updatePreview() {
        preview.setTextColor(selectedTextColor);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selectedBackgroundColor);
        bg.setCornerRadius(dp(10));
        preview.setBackground(bg);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void save() {
        AppDatabase db = AppDatabase.getInstance(this);
        if (isDefault) {
            DefaultFilterEntity e = new DefaultFilterEntity();
            e.id = 1;
            e.soundEnabled = switchSound.isChecked();
            e.soundOptionIndex = getSelectedSoundOption();
            e.popupEnabled = switchPopup.isChecked();
            e.translateEnabled = switchTranslate.isChecked();
            e.textColor = selectedTextColor;
            e.backgroundColor = selectedBackgroundColor;
            AppExecutors.background(() -> {
                db.defaultFilterDao().save(e);
                AppExecutors.main(this::finish);
            });
            return;
        }

        FilterEntity f = editingFilter != null ? editingFilter : new FilterEntity();
        f.authorPattern = editAuthor.getText().toString().trim();
        f.keywordsRaw = editKeywords.getText().toString().trim();
        f.matchAll = radioMatchMode.getCheckedRadioButtonId() == R.id.radio_all;
        f.caseSensitive = checkCaseSensitive.isChecked();
        f.enabled = switchFilterEnabled.isChecked();
        f.soundEnabled = switchSound.isChecked();
        f.soundOptionIndex = getSelectedSoundOption();
        f.popupEnabled = switchPopup.isChecked();
        f.translateEnabled = switchTranslate.isChecked();
        f.textColor = selectedTextColor;
        f.backgroundColor = selectedBackgroundColor;

        boolean isNew = editingFilter == null;
        AppExecutors.background(() -> {
            if (isNew) {
                f.sortOrder = db.filterDao().getMaxOrder() + 1;
                db.filterDao().insert(f);
            } else {
                db.filterDao().update(f);
            }
            AppExecutors.main(this::finish);
        });
    }

    private void confirmDelete() {
        String label = "「" + FilterMatcher.describe(editingFilter) + "」";
        new AlertDialog.Builder(this)
                .setTitle("フィルターを削除")
                .setMessage(label + "を削除します。よろしいですか？")
                .setPositiveButton("削除", (d, w) -> {
                    AppDatabase db = AppDatabase.getInstance(this);
                    AppExecutors.background(() -> {
                        db.filterDao().delete(editingFilter);
                        AppExecutors.main(this::finish);
                    });
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }
}
