package jp.crescendo.xtranslator;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        askNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean enabled = getSystemService(NotificationManager.class)
                .isNotificationListenerAccessGranted(
                        new android.content.ComponentName(this, XNotificationListener.class));
        status.setText(enabled ? "✓ 通知へのアクセス：許可済み" : "！通知へのアクセス：未許可");
        status.setTextColor(enabled ? Color.rgb(0, 120, 60) : Color.rgb(190, 50, 35));
    }

    private View buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(54), pad, pad);

        TextView title = text("X 即時翻訳", 28, true);
        root.addView(title);
        TextView detail = text("Xの英語通知を受信した瞬間に、端末内で日本語へ翻訳して表示します。APIキー・月額料金は不要です。", 16, false);
        detail.setPadding(0, dp(14), 0, dp(26));
        root.addView(detail);

        status = text("確認中…", 17, true);
        status.setPadding(0, 0, 0, dp(14));
        root.addView(status);

        root.addView(button("1. 通知へのアクセスを許可", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(button("2. 翻訳モデルを準備", v -> downloadModel()));
        root.addView(button("3. 通知設定を確認", v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        }));

        TextView note = text("設定後はアプリを閉じても動作します。Xの通知自体が端末に届いている必要があります。初回モデルは約30MBです。", 14, false);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);
        return root;
    }

    private void downloadModel() {
        Toast.makeText(this, "翻訳モデルを準備しています…", Toast.LENGTH_LONG).show();
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.JAPANESE).build();
        Translator translator = Translation.getClient(options);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "準備完了。Xの英語通知を待っています", Toast.LENGTH_LONG).show();
                    translator.close();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "準備に失敗しました。通信状態を確認してください", Toast.LENGTH_LONG).show();
                    translator.close();
                });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(16); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.bottomMargin = dp(12); b.setLayoutParams(p);
        return b;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
