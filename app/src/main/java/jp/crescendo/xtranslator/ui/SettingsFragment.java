package jp.crescendo.xtranslator.ui;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.Prefs;
import jp.crescendo.xtranslator.service.XNotificationListener;

public class SettingsFragment extends Fragment {
    private TextView listenerStatusText;
    private TextView postPermissionStatusText;
    private TextView historyCountText;
    private EditText editRetentionDays;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        listenerStatusText = view.findViewById(R.id.text_listener_status);
        postPermissionStatusText = view.findViewById(R.id.text_post_permission_status);
        historyCountText = view.findViewById(R.id.text_history_count);
        editRetentionDays = view.findViewById(R.id.edit_retention_days);

        view.findViewById(R.id.btn_grant_access).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        view.findViewById(R.id.btn_prepare_model).setOnClickListener(v -> downloadModel());

        view.findViewById(R.id.btn_app_notification_settings).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(intent);
        });

        view.findViewById(R.id.btn_save_retention).setOnClickListener(v -> saveRetentionDays());

        editRetentionDays.setText(String.valueOf(Prefs.getRetentionDays(requireContext())));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateListenerStatus();
        updatePostPermissionStatus();
        updateHistoryCount();
    }

    private void updateListenerStatus() {
        boolean enabled = false;
        NotificationManager nm = (NotificationManager) requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            enabled = nm.isNotificationListenerAccessGranted(
                    new ComponentName(requireContext(), XNotificationListener.class));
        }
        listenerStatusText.setText(enabled
                ? "✓ 通知へのアクセス（Xの通知を読み取る許可）：許可済み"
                : "！通知へのアクセス（Xの通知を読み取る許可）：未許可");
        listenerStatusText.setTextColor(enabled ? Color.rgb(0, 120, 60) : Color.rgb(190, 50, 35));
    }

    private void updatePostPermissionStatus() {
        boolean enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
        postPermissionStatusText.setText(enabled
                ? "✓ このアプリの通知（ポップアップ表示）：許可済み"
                : "！このアプリの通知（ポップアップ表示）：未許可 → これが原因で通知が来ないことがあります");
        postPermissionStatusText.setTextColor(enabled ? Color.rgb(0, 120, 60) : Color.rgb(190, 50, 35));
    }

    private void updateHistoryCount() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> {
            int count = db.notificationDao().count();
            AppExecutors.main(() -> {
                if (isAdded()) {
                    historyCountText.setText("現在の保存件数: " + count + " 件（" + Prefs.getRetentionDays(requireContext()) + " 日以内の履歴を保持）");
                }
            });
        });
    }

    private void saveRetentionDays() {
        String raw = editRetentionDays.getText().toString().trim();
        if (raw.isEmpty()) return;
        try {
            int value = Integer.parseInt(raw);
            Prefs.setRetentionDays(requireContext(), value);
            editRetentionDays.setText(String.valueOf(Prefs.getRetentionDays(requireContext())));
            Toast.makeText(requireContext(), "保存期間を更新しました", Toast.LENGTH_SHORT).show();
            updateHistoryCount();
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "数値を入力してください", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadModel() {
        Toast.makeText(requireContext(), "翻訳モデルを準備しています…", Toast.LENGTH_LONG).show();
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.JAPANESE).build();
        Translator translator = Translation.getClient(options);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "準備完了。Xの英語通知を待っています", Toast.LENGTH_LONG).show();
                    }
                    translator.close();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "準備に失敗しました。通信状態を確認してください", Toast.LENGTH_LONG).show();
                    }
                    translator.close();
                });
    }
}
