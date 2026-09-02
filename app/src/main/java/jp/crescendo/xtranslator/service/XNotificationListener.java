package jp.crescendo.xtranslator.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.DefaultFilterEntity;
import jp.crescendo.xtranslator.data.FilterEntity;
import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.Prefs;
import jp.crescendo.xtranslator.filter.FilterMatcher;
import jp.crescendo.xtranslator.util.PendingIntentCache;

public class XNotificationListener extends NotificationListenerService {
    private static final String[] X_PACKAGES = {"com.twitter.android", "com.twitter.android.lite"};

    /** onNotificationPosted の高頻度な再送を無駄なDBアクセスにしないための短期キャッシュ。永続的な重複防止はDBのユニーク制約で行う。 */
    private final Map<String, Long> recentlySeen = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 200;
        }
    };

    private Translator translator;
    private volatile boolean modelReady;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensure(this);
        db = AppDatabase.getInstance(this);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.JAPANESE).build();
        translator = Translation.getClient(options);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> modelReady = true);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isX(sbn.getPackageName())) return;
        Bundle extras = sbn.getNotification().extras;
        String title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = string(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = string(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (text.isEmpty()) return;

        String author = title;
        String dedupeKey = dedupeKey(author, text);

        synchronized (recentlySeen) {
            Long seen = recentlySeen.get(dedupeKey);
            long now = System.currentTimeMillis();
            if (seen != null && now - seen < 5_000) return;
            recentlySeen.put(dedupeKey, now);
        }

        boolean isEnglish = looksEnglish(text);
        PendingIntent originalTap = sbn.getNotification().contentIntent;
        String sourcePackage = sbn.getPackageName();
        String finalText = text;

        AppExecutors.background(() -> {
            List<FilterEntity> filters = db.filterDao().getAll();
            DefaultFilterEntity defaults = db.defaultFilterDao().getOrCreate();
            FilterMatcher.Effective effective = FilterMatcher.resolve(filters, defaults, author, finalText);

            boolean shouldTranslate = effective.translate && isEnglish;
            if (shouldTranslate) {
                ensureModelAndTranslate(finalText, translated ->
                        persistAndNotify(author, finalText, translated, sourcePackage, effective, originalTap));
            } else {
                persistAndNotify(author, finalText, "", sourcePackage, effective, originalTap);
            }
        });
    }

    private void ensureModelAndTranslate(String text, java.util.function.Consumer<String> onResult) {
        if (modelReady) {
            translator.translate(text)
                    .addOnSuccessListener(onResult::accept)
                    .addOnFailureListener(e -> onResult.accept(""));
        } else {
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                    .addOnSuccessListener(v -> {
                        modelReady = true;
                        translator.translate(text)
                                .addOnSuccessListener(onResult::accept)
                                .addOnFailureListener(e -> onResult.accept(""));
                    })
                    .addOnFailureListener(e -> onResult.accept(""));
        }
    }

    /** バックグラウンドスレッドから呼び出される。DB保存・上限トリム・ポップアップ表示までを行う。 */
    private void persistAndNotify(String author, String original, String translated, String sourcePackage,
                                   FilterMatcher.Effective effective, PendingIntent originalTap) {
        NotificationEntity entity = new NotificationEntity();
        entity.dedupeKey = dedupeKey(author, original);
        entity.receivedAt = System.currentTimeMillis();
        entity.author = author;
        entity.originalText = original;
        entity.translatedText = translated == null ? "" : translated;
        entity.wasTranslated = effective.translate && !entity.translatedText.isEmpty();
        entity.sourcePackage = sourcePackage;
        entity.textColor = effective.textColor;
        entity.backgroundColor = effective.backgroundColor;
        entity.filterName = effective.filterName;
        entity.popupShown = effective.popup;
        entity.soundPlayed = effective.popup && effective.sound;

        long insertedId = db.notificationDao().insert(entity);
        if (insertedId == -1) return; // 既存の同一投稿。DBのユニーク制約で重複保存を防止。

        trimHistoryIfNeeded();

        if (originalTap != null) {
            PendingIntentCache.put(insertedId, originalTap);
        }

        if (effective.popup) {
            postSystemNotification(insertedId, author, original, entity.translatedText, effective, originalTap, sourcePackage);
        }
    }

    private void trimHistoryIfNeeded() {
        int max = Prefs.getMaxHistory(this);
        int count = db.notificationDao().count();
        if (count > max) {
            List<Long> overflowIds = db.notificationDao().oldestIds(count - max);
            db.notificationDao().deleteByIds(overflowIds);
        }
    }

    private void postSystemNotification(long historyId, String author, String original, String translated,
                                         FilterMatcher.Effective effective, PendingIntent tap, String sourcePackage) {
        String channel = effective.sound ? NotificationChannels.SOUND : NotificationChannels.SILENT;
        boolean hasTranslation = translated != null && !translated.isEmpty();
        String contentText = hasTranslation ? translated : original;
        String bigText = hasTranslation ? (translated + "\n\n原文: " + original) : original;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(author.isEmpty() ? "X通知" : author)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(effective.sound ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);
        if (!effective.sound) builder.setSilent(true);

        PendingIntent tapIntent = tap;
        if (tapIntent == null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(sourcePackage);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                tapIntent = PendingIntent.getActivity(this, (int) historyId, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
        }
        if (tapIntent != null) builder.setContentIntent(tapIntent);

        NotificationManagerCompat.from(this).notify((int) (20_000 + (historyId % 20_000)), builder.build());
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

    private String string(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String dedupeKey(String author, String text) {
        String a = author == null ? "" : author;
        String t = text == null ? "" : text;
        return a.length() + "|" + a + "|" + t;
    }

    @Override
    public void onDestroy() {
        if (translator != null) translator.close();
        super.onDestroy();
    }
}
