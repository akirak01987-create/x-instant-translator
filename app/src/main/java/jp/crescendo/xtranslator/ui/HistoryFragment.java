package jp.crescendo.xtranslator.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.Prefs;
import jp.crescendo.xtranslator.service.GeminiAnalyzer;
import jp.crescendo.xtranslator.util.PendingIntentCache;
import jp.crescendo.xtranslator.widget.WidgetUpdater;

public class HistoryFragment extends Fragment implements HistoryAdapter.Listener {
    private static final long POLL_INTERVAL_MS = 3000;

    private HistoryAdapter adapter;
    private TextView emptyText;
    private Button btnTimeFilter;

    private List<NotificationEntity> allItems = new ArrayList<>();
    private List<Long> lastSubmittedIds = new ArrayList<>();
    /** 分単位(0〜1439)。未設定ならnull。 */
    private Integer filterStartMinutes;
    private Integer filterEndMinutes;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            reload();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /** 設定画面のタブに切り替えても(hide/showのため)このFragmentのonResumeは再度呼ばれない。
     * 文字サイズ設定をアプリを閉じずとも即座に反映させるため、SharedPreferencesの変更を直接監視する。
     * SharedPreferencesはリスナーを弱参照でしか保持しないため、フィールドとして強参照を保持する。 */
    private final SharedPreferences.OnSharedPreferenceChangeListener textSizeListener = (prefs, key) -> {
        if (isAdded()) adapter.setTextScale(Prefs.getHistoryTextScale(requireContext()));
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new HistoryAdapter(this);
        RecyclerView recycler = view.findViewById(R.id.recycler_history);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        emptyText = view.findViewById(R.id.text_empty);
        btnTimeFilter = view.findViewById(R.id.btn_time_filter);

        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> confirmClearAll());
        view.findViewById(R.id.btn_ai_analyze).setOnClickListener(v -> runAiAnalysis());
        btnTimeFilter.setOnClickListener(v -> showTimeFilterDialog());

        adapter.setTextScale(Prefs.getHistoryTextScale(requireContext()));
        Prefs.registerChangeListener(requireContext(), textSizeListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Prefs.unregisterChangeListener(requireContext(), textSizeListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void reload() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> {
            db.notificationDao().deleteOlderThan(Prefs.getRetentionCutoffMillis(requireContext()));
            List<NotificationEntity> all = db.notificationDao().getAll();
            AppExecutors.main(() -> {
                if (!isAdded()) return;
                allItems = all;
                applyFilterAndSubmit();
            });
        });
    }

    /** 「時間で絞り込み」ボタンから開く別画面(ダイアログ)。現在の絞り込み状態をコピーして編集し、
     * 「この条件で検索」でフラグメント側の状態へ反映する。 */
    private void showTimeFilterDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_filter, null);
        Button dialogFrom = view.findViewById(R.id.btn_time_from);
        Button dialogTo = view.findViewById(R.id.btn_time_to);

        Integer[] pendingStart = {filterStartMinutes};
        Integer[] pendingEnd = {filterEndMinutes};
        dialogFrom.setText(formatMinutes(pendingStart[0], "開始時刻"));
        dialogTo.setText(formatMinutes(pendingEnd[0], "終了時刻"));

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(view).create();

        dialogFrom.setOnClickListener(v -> pickTime(pendingStart[0], minutes -> {
            pendingStart[0] = minutes;
            dialogFrom.setText(formatMinutes(minutes, "開始時刻"));
        }));
        dialogTo.setOnClickListener(v -> pickTime(pendingEnd[0], minutes -> {
            pendingEnd[0] = minutes;
            dialogTo.setText(formatMinutes(minutes, "終了時刻"));
        }));
        view.findViewById(R.id.btn_time_apply).setOnClickListener(v -> {
            filterStartMinutes = pendingStart[0];
            filterEndMinutes = pendingEnd[0];
            updateTimeFilterButtonLabel();
            applyFilterAndSubmit();
            dialog.dismiss();
        });
        view.findViewById(R.id.btn_time_clear).setOnClickListener(v -> {
            filterStartMinutes = null;
            filterEndMinutes = null;
            updateTimeFilterButtonLabel();
            applyFilterAndSubmit();
            dialog.dismiss();
        });

        dialog.show();
    }

    private interface OnMinutesPicked {
        void onPicked(int minutes);
    }

    private void pickTime(Integer current, OnMinutesPicked callback) {
        int initHour = current != null ? current / 60 : 9;
        int initMinute = current != null ? current % 60 : 0;
        new TimePickerDialog(requireContext(), (view, hourOfDay, minute) ->
                callback.onPicked(hourOfDay * 60 + minute), initHour, initMinute, true).show();
    }

    private static String formatMinutes(Integer minutes, String placeholder) {
        return minutes == null ? placeholder : String.format(Locale.JAPAN, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private void updateTimeFilterButtonLabel() {
        if (filterStartMinutes == null || filterEndMinutes == null) {
            btnTimeFilter.setText("⏱ 時間で絞り込み");
        } else {
            btnTimeFilter.setText("⏱ " + formatMinutes(filterStartMinutes, "") + "〜" + formatMinutes(filterEndMinutes, ""));
        }
    }

    private void applyFilterAndSubmit() {
        List<NotificationEntity> filtered = currentFilteredItems();

        List<Long> ids = new ArrayList<>();
        for (NotificationEntity item : filtered) ids.add(item.id);
        if (!ids.equals(lastSubmittedIds)) {
            adapter.submit(filtered);
            lastSubmittedIds = ids;
        }
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 現在の「時間で絞り込み」条件を適用した一覧。一覧表示とAI分析の両方で同じ絞り込み結果を使う。 */
    private List<NotificationEntity> currentFilteredItems() {
        List<NotificationEntity> filtered = new ArrayList<>();
        for (NotificationEntity item : allItems) {
            if (inTimeRange(minutesOfDay(item.receivedAt))) filtered.add(item);
        }
        return filtered;
    }

    /** 現在表示中(時間で絞り込み済み)の投稿をGemini APIへ送り、ドル円相場に関連しそうな
     * 内容を中心に分析させる。 */
    private void runAiAnalysis() {
        List<NotificationEntity> filtered = currentFilteredItems();
        if (filtered.isEmpty()) {
            Toast.makeText(requireContext(), "分析対象の投稿がありません(絞り込み範囲に投稿がありません)", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> posts = new ArrayList<>();
        for (NotificationEntity item : filtered) {
            String body = (item.wasTranslated && item.translatedText != null && !item.translatedText.isEmpty())
                    ? item.translatedText : item.originalText;
            String author = (item.author == null || item.author.isEmpty()) ? "" : item.author + ": ";
            posts.add(author + body);
            if (posts.size() >= GeminiAnalyzer.MAX_POSTS) break;
        }

        android.content.Context appContext = requireContext().getApplicationContext();
        Toast.makeText(requireContext(), "AIで分析中…", Toast.LENGTH_SHORT).show();
        AppExecutors.background(() -> {
            try {
                String result = GeminiAnalyzer.analyzeUsdJpy(appContext, posts);
                AppExecutors.main(() -> {
                    if (isAdded()) showAnalysisResult(result);
                });
            } catch (GeminiAnalyzer.GeminiException e) {
                String message = e.getMessage();
                AppExecutors.main(() -> {
                    if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAnalysisResult(String text) {
        TextView content = new TextView(requireContext());
        content.setText(text.isEmpty() ? "(結果が空でした)" : text);
        content.setTextIsSelectable(true);
        int padding = dp(20);
        content.setPadding(padding, dp(12), padding, dp(12));

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);

        new AlertDialog.Builder(requireContext())
                .setTitle("ドル円 AI分析結果")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean inTimeRange(int minutes) {
        if (filterStartMinutes == null || filterEndMinutes == null) return true;
        if (filterStartMinutes <= filterEndMinutes) {
            return minutes >= filterStartMinutes && minutes <= filterEndMinutes;
        }
        // 開始が終了より後(例: 22:00〜翌2:00)の場合は日をまたぐ範囲として扱う
        return minutes >= filterStartMinutes || minutes <= filterEndMinutes;
    }

    private static int minutesOfDay(long epochMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMillis);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(requireContext())
                .setTitle("全件削除")
                .setMessage("通知履歴をすべて削除します。よろしいですか？")
                .setPositiveButton("削除", (d, w) -> {
                    AppDatabase db = AppDatabase.getInstance(requireContext());
                    AppExecutors.background(() -> {
                        db.notificationDao().deleteAll();
                        WidgetUpdater.updateAll(requireContext().getApplicationContext());
                        AppExecutors.main(this::reload);
                    });
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    @Override
    public void onOpen(NotificationEntity item) {
        PendingIntent cached = PendingIntentCache.get(item.id);
        if (cached != null) {
            try {
                cached.send();
                return;
            } catch (PendingIntent.CanceledException ignored) {
                // 期限切れの場合はXアプリの起動にフォールバックする
            }
        }
        PackageManager pm = requireContext().getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(item.sourcePackage);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } else {
            Toast.makeText(requireContext(), "Xアプリが見つかりません", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(NotificationEntity item) {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> {
            db.notificationDao().deleteById(item.id);
            WidgetUpdater.updateAll(requireContext().getApplicationContext());
            AppExecutors.main(this::reload);
        });
    }
}
