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
import jp.crescendo.xtranslator.data.RawLogEntity;
import jp.crescendo.xtranslator.filter.FilterMatcher;
import jp.crescendo.xtranslator.util.PendingIntentCache;
import jp.crescendo.xtranslator.widget.WidgetUpdater;

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
        String packageName = sbn.getPackageName();
        boolean isXPackage = isX(packageName);
        Bundle extras = sbn.getNotification().extras;
        String title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = extractText(extras);
        logReceived(packageName, isXPackage, text);

        if (!isXPackage) return;
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
        String sourcePackage = packageName;
        String finalText = text;
        String originalKey = sbn.getKey();

        AppExecutors.background(() -> {
            try {
                List<FilterEntity> filters = db.filterDao().getAll();
                DefaultFilterEntity defaults = db.defaultFilterDao().getOrCreate();
                FilterMatcher.Effective effective = FilterMatcher.resolve(filters, defaults, author, finalText);

                boolean shouldTranslate = effective.translate && isEnglish;
                String translated = shouldTranslate ? translateBlocking(finalText) : "";
                persistAndNotifySafely(author, finalText, translated, sourcePackage, effective, originalTap, originalKey);
            } catch (Exception e) {
                logError("処理中の例外: " + e);
            }
        });
    }

    /** バックグラウンドスレッドから呼び出される。ML Kitのtranslate()は同一Translatorインスタンスに対する
     * 同時実行をサポートしておらず、続けて呼ぶと先勝ちの呼び出しが結果を返さなくなることがある。通知が
     * 連続して届いてもこのメソッドは常に1件ずつ完了を待ってから戻るため、翻訳の取りこぼしを防げる。 */
    private String translateBlocking(String text) {
        try {
            if (!modelReady) {
                com.google.android.gms.tasks.Tasks.await(
                        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()));
                modelReady = true;
            }
            return com.google.android.gms.tasks.Tasks.await(translator.translate(text));
        } catch (Exception e) {
            logError("翻訳中の例外: " + e);
            return "";
        }
    }

    private void persistAndNotifySafely(String author, String original, String translated, String sourcePackage,
                                         FilterMatcher.Effective effective, PendingIntent originalTap, String originalKey) {
        try {
            persistAndNotify(author, original, translated, sourcePackage, effective, originalTap, originalKey);
        } catch (Exception e) {
            logError("保存処理中の例外: " + e);
        }
    }

    /** バックグラウンドスレッドから呼び出される。DB保存・上限トリム・ポップアップ表示までを行う。 */
    private void persistAndNotify(String author, String original, String translated, String sourcePackage,
                                   FilterMatcher.Effective effective, PendingIntent originalTap, String originalKey) {
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
        entity.filterId = effective.filterId;
        entity.filterName = effective.filterName;
        entity.popupShown = effective.popup;
        entity.soundPlayed = effective.popup && effective.sound;

        long insertedId = db.notificationDao().insert(entity);
        if (insertedId == -1) return; // 既存の同一投稿。DBのユニーク制約で重複保存を防止。

        db.notificationDao().deleteOlderThan(Prefs.getRetentionCutoffMillis(this));

        if (originalTap != null) {
            PendingIntentCache.put(insertedId, originalTap);
        }

        if (effective.popup) {
            postSystemNotification(insertedId, author, original, entity.translatedText, effective, originalTap, sourcePackage);
            if (Prefs.isHideOriginalNotificationEnabled(this) && originalKey != null) {
                // 翻訳したポップアップを出す代わりに、Xの元通知(英語)は消して二重表示を防ぐ。
                cancelNotification(originalKey);
            }
        }

        WidgetUpdater.updateAll(this);
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

    /** 本文の取得元を複数試す。通常の通知(EXTRA_TEXT/EXTRA_BIG_TEXT)に加え、
     * InboxStyle等でまとめられた通知(EXTRA_TEXT_LINES)や、件名のみの通知(EXTRA_SUB_TEXT)も拾う。 */
    private String extractText(Bundle extras) {
        String text = string(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = string(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (text.isEmpty()) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                StringBuilder sb = new StringBuilder();
                for (CharSequence line : lines) {
                    if (line == null) continue;
                    String trimmed = line.toString().trim();
                    if (trimmed.isEmpty()) continue;
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(trimmed);
                }
                text = sb.toString();
            }
        }
        if (text.isEmpty()) text = string(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        return text;
    }

    /** 診断用: 通知リスナーが実際に何を受信しているかを記録する。Xパッケージ以外は本文を保存しない。 */
    private void logReceived(String packageName, boolean isXPackage, String text) {
        RawLogEntity log = new RawLogEntity();
        log.timestamp = System.currentTimeMillis();
        log.packageName = packageName;
        log.isXPackage = isXPackage;
        log.textFound = !text.isEmpty();
        log.textPreview = isXPackage ? text.substring(0, Math.min(text.length(), 60)) : "";
        AppExecutors.background(() -> {
            db.rawLogDao().insert(log);
            db.rawLogDao().trimToRecent();
        });
    }

    /** 診断用: 保存処理などで起きた例外を受信ログに残す。 */
    private void logError(String message) {
        RawLogEntity log = new RawLogEntity();
        log.timestamp = System.currentTimeMillis();
        log.packageName = "(内部エラー)";
        log.isXPackage = true;
        log.textFound = false;
        log.textPreview = message.substring(0, Math.min(message.length(), 120));
        AppExecutors.background(() -> {
            db.rawLogDao().insert(log);
            db.rawLogDao().trimToRecent();
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
