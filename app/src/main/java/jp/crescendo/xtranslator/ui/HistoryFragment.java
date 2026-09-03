package jp.crescendo.xtranslator.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import jp.crescendo.xtranslator.util.PendingIntentCache;
import jp.crescendo.xtranslator.widget.WidgetUpdater;

public class HistoryFragment extends Fragment implements HistoryAdapter.Listener {
    private static final long POLL_INTERVAL_MS = 3000;

    private HistoryAdapter adapter;
    private TextView emptyText;
    private Button btnTimeFrom;
    private Button btnTimeTo;

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
        btnTimeFrom = view.findViewById(R.id.btn_time_from);
        btnTimeTo = view.findViewById(R.id.btn_time_to);

        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> confirmClearAll());
        btnTimeFrom.setOnClickListener(v -> pickTime(true));
        btnTimeTo.setOnClickListener(v -> pickTime(false));
        view.findViewById(R.id.btn_time_search).setOnClickListener(v -> applyFilterAndSubmit());
        view.findViewById(R.id.btn_time_clear).setOnClickListener(v -> clearTimeFilter());
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

    private void pickTime(boolean isStart) {
        Integer current = isStart ? filterStartMinutes : filterEndMinutes;
        int initHour = current != null ? current / 60 : 9;
        int initMinute = current != null ? current % 60 : 0;
        new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            int totalMinutes = hourOfDay * 60 + minute;
            String formatted = String.format(Locale.JAPAN, "%02d:%02d", hourOfDay, minute);
            if (isStart) {
                filterStartMinutes = totalMinutes;
                btnTimeFrom.setText(formatted);
            } else {
                filterEndMinutes = totalMinutes;
                btnTimeTo.setText(formatted);
            }
        }, initHour, initMinute, true).show();
    }

    private void clearTimeFilter() {
        filterStartMinutes = null;
        filterEndMinutes = null;
        btnTimeFrom.setText("開始時刻");
        btnTimeTo.setText("終了時刻");
        applyFilterAndSubmit();
    }

    private void applyFilterAndSubmit() {
        List<NotificationEntity> filtered = new ArrayList<>();
        for (NotificationEntity item : allItems) {
            if (inTimeRange(minutesOfDay(item.receivedAt))) filtered.add(item);
        }

        List<Long> ids = new ArrayList<>();
        for (NotificationEntity item : filtered) ids.add(item.id);
        if (!ids.equals(lastSubmittedIds)) {
            adapter.submit(filtered);
            lastSubmittedIds = ids;
        }
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
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
