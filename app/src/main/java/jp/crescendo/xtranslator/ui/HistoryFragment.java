package jp.crescendo.xtranslator.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.Prefs;
import jp.crescendo.xtranslator.util.PendingIntentCache;

public class HistoryFragment extends Fragment implements HistoryAdapter.Listener {
    private HistoryAdapter adapter;
    private TextView emptyText;

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

        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> confirmClearAll());
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> {
            db.notificationDao().deleteOlderThan(Prefs.getRetentionCutoffMillis(requireContext()));
            List<NotificationEntity> all = db.notificationDao().getAll();
            AppExecutors.main(() -> {
                if (!isAdded()) return;
                adapter.submit(all);
                emptyText.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(requireContext())
                .setTitle("全件削除")
                .setMessage("通知履歴をすべて削除します。よろしいですか？")
                .setPositiveButton("削除", (d, w) -> {
                    AppDatabase db = AppDatabase.getInstance(requireContext());
                    AppExecutors.background(() -> {
                        db.notificationDao().deleteAll();
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
            AppExecutors.main(this::reload);
        });
    }
}
