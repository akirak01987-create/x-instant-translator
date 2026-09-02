package jp.crescendo.xtranslator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.LinkedHashMap;
import java.util.Map;

public class XNotificationListener extends NotificationListenerService {
    private static final String CHANNEL = "x_translated_notifications";
    private static final String[] X_PACKAGES = {"com.twitter.android", "com.twitter.android.lite"};
    private final Map<String, Long> recent = new LinkedHashMap<String, Long>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 100; }
    };
    private Translator translator;
    private volatile boolean modelReady;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "X 日本語翻訳", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("英語のX通知を日本語へ翻訳して表示します");
        nm.createNotificationChannel(channel);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.JAPANESE).build();
        translator = Translation.getClient(options);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> modelReady = true);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isX(sbn.getPackageName()) || translator == null) return;
        Bundle extras = sbn.getNotification().extras;
        String title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = string(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = string(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (text.isEmpty() || !looksEnglish(text)) return;

        String key = title + "\n" + text;
        synchronized (recent) {
            Long seen = recent.get(key);
            long now = System.currentTimeMillis();
            if (seen != null && now - seen < 60_000) return;
            recent.put(key, now);
        }

        PendingIntent originalTap = sbn.getNotification().contentIntent;
        final String original = text;
        if (!modelReady) {
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                    .addOnSuccessListener(v -> { modelReady = true; translateAndShow(title, original, originalTap, sbn.getId()); });
        } else {
            translateAndShow(title, original, originalTap, sbn.getId());
        }
    }

    private void translateAndShow(String title, String original, PendingIntent tap, int sourceId) {
        translator.translate(original).addOnSuccessListener(japanese -> {
            NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title.isEmpty() ? "X通知（日本語訳）" : title + "（日本語訳）")
                    .setContentText(japanese)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(japanese + "\n\n原文: " + original))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);
            if (tap != null) b.setContentIntent(tap);
            getSystemService(NotificationManager.class).notify(10_000 + Math.abs(sourceId % 10_000), b.build());
        });
    }

    private boolean isX(String pkg) {
        for (String x : X_PACKAGES) if (x.equals(pkg)) return true;
        return false;
    }

    private boolean looksEnglish(String value) {
        int latin = 0, japanese = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latin++;
            if ((c >= 0x3040 && c <= 0x30ff) || (c >= 0x4e00 && c <= 0x9fff)) japanese++;
        }
        return latin >= 4 && latin > japanese;
    }

    private String string(CharSequence value) { return value == null ? "" : value.toString().trim(); }

    @Override public void onDestroy() {
        if (translator != null) translator.close();
        super.onDestroy();
    }
}
