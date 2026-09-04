package jp.crescendo.xtranslator.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    /** ポップアップ通知を1つのグループにまとめるためのキー。個々の通知にこれを設定し、
     * 別途サマリー通知を同じキーで投稿することで、通知シェード上でひとまとめに表示・折りたたみできる
     * ようにする。設定しないと通知が届くたびに1件ずつバラバラに積み上がってしまう。 */
    private static final String GROUP_KEY = "jp.crescendo.xtranslator.X_GROUP";
    private static final int SUMMARY_NOTIFICATION_ID = 19_999;
    /** 翻訳モデルのダウンロード・翻訳呼び出し1回あたりの上限。ここで頭打ちにしないと、
     * ネットワーク不調時にキューが詰まり、翻訳が不要な後続の通知まで巻き添えで遅延してしまう。 */
    private static final long TRANSLATE_TIMEOUT_SECONDS = 15;

    /** onNotificationPosted の高頻度な再送を無駄なDBアクセスにしないための短期キャッシュ。永続的な重複防止はDBのユニーク制約で行う。 */
    private final Map<String, Long> recentlySeen = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 200;
        }
    };

    /** 通知処理専用のスレッド。履歴画面のポーリングやウィジェット更新など、アプリの他のバックグラウンド
     * 処理(AppExecutors.background)と同じキューを共有すると、片方が詰まった際に通知の受信・翻訳・保存
     * まで巻き添えで遅延してしまうため、通知処理はここだけ独立させている。 */
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();

    /** LINEへの配信専用のスレッド。ネットワークI/Oのため、notificationExecutor(翻訳処理)や
     * AppExecutors.background(履歴ポーリング等)と共有すると、LINE側の通信が遅い場合に
     * それらまで巻き添えで遅延してしまう。 */
    private final ExecutorService lineExecutor = Executors.newSingleThreadExecutor();

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
        // 診断ログはXパッケージのみ記録する。他アプリの通知(LINE・Chromeなど)も記録すると、
        // それらが短時間に大量に届いた際に上限(直近N件)をすぐ使い切ってしまい、
        // 数秒前のXの受信状況すら確認できなくなってしまう。
        if (isXPackage) logReceived(packageName, text);

        if (!isXPackage) return;
        if (text.isEmpty()) return;

        if (Prefs.isHideOriginalNotificationEnabled(this)) {
            // 翻訳やフィルター判定などの処理パイプラインを待たず、Xの元通知は届いた瞬間に消す。
            // 処理完了を待ってから消していると、その間だけXの元通知(英語)が見えてしまっていた。
            cancelNotification(sbn.getKey());
        }

        if (isRedactedByOs(text)) {
            // Android側(画面共有中の機密保護機能など)によって本文が「プライベートな通知内容は
            // 表示されません」に伏せられた通知。実際の投稿内容はこのアプリを含むどの通知リスナーにも
            // 渡されないため、キーワード判定・翻訳のしようがない。ただし何も残さず消してしまうと
            // 「本当に届いていたのか」を確認する手段がなくなるため、伏せられたことが分かる形で
            // 履歴には残す(通知は届いたが内容だけ取得できなかった、という事実自体は伝える)。
            text = "⚠️ 内容が伏せられた通知(画面共有・録画・ミラーリング中などが原因の可能性があります)";
        }

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

        notificationExecutor.execute(() -> {
            try {
                List<FilterEntity> filters = db.filterDao().getAll();
                DefaultFilterEntity defaults = db.defaultFilterDao().getOrCreate();
                FilterMatcher.Effective effective = FilterMatcher.resolve(filters, defaults, author, finalText);

                boolean shouldTranslate = effective.translate && isEnglish;
                String translated = shouldTranslate ? translateBlocking(finalText) : "";
                persistAndNotifySafely(author, finalText, translated, sourcePackage, effective, originalTap);
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
                        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()),
                        TRANSLATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                modelReady = true;
            }
            return com.google.android.gms.tasks.Tasks.await(
                    translator.translate(text), TRANSLATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logError("翻訳中の例外: " + e);
            return "";
        }
    }

    private void persistAndNotifySafely(String author, String original, String translated, String sourcePackage,
                                         FilterMatcher.Effective effective, PendingIntent originalTap) {
        try {
            persistAndNotify(author, original, translated, sourcePackage, effective, originalTap);
        } catch (Exception e) {
            logError("保存処理中の例外: " + e);
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
        }

        if (effective.line) {
            // 失敗してもLineNotifier内でログに留め、他の処理には影響させない。
            String lineText = buildLineMessage(author, original, entity.translatedText);
            lineExecutor.execute(() -> LineNotifier.broadcast(this, lineText));
        }

        WidgetUpdater.updateAll(this);
    }

    private String buildLineMessage(String author, String original, String translated) {
        String body = (translated != null && !translated.isEmpty()) ? translated : original;
        return (author == null || author.isEmpty() ? "X" : author) + "\n" + body;
    }

    private void postSystemNotification(long historyId, String author, String original, String translated,
                                         FilterMatcher.Effective effective, PendingIntent tap, String sourcePackage) {
        String channel = effective.sound ? NotificationChannels.soundChannelId(effective.soundOptionIndex) : NotificationChannels.SILENT;
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
                .setGroup(GROUP_KEY)
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
        updateGroupSummary();
    }

    /** 個々の通知をグループとしてひとまとめにするためのサマリー通知を更新する。これが無いと、
     * 同じグループキーを持つ通知同士でもシェード上で折りたたまれず、届くたびに1件ずつ
     * バラバラに積み上がって見えてしまう。サマリー自体は無音にし、実際の音は各通知の設定に任せる。 */
    private void updateGroupSummary() {
        List<NotificationEntity> recent = db.notificationDao().getRecentPopups(8);
        if (recent.isEmpty()) return;

        NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle()
                .setBigContentTitle("X即時翻訳");
        String topLine = "";
        int shown = 0;
        for (NotificationEntity n : recent) {
            String body = (n.wasTranslated && n.translatedText != null && !n.translatedText.isEmpty())
                    ? n.translatedText : n.originalText;
            String author = (n.author == null || n.author.isEmpty()) ? "X" : n.author;
            String line = author + ": " + body;
            if (shown == 0) topLine = line;
            if (shown < 5) inbox.addLine(line);
            shown++;
        }

        NotificationCompat.Builder summary = new NotificationCompat.Builder(this, NotificationChannels.SILENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("X即時翻訳")
                .setContentText(topLine)
                .setStyle(inbox)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);

        NotificationManagerCompat.from(this).notify(SUMMARY_NOTIFICATION_ID, summary.build());
    }

    /** 本文の取得元を複数試す。通常の通知(EXTRA_TEXT/EXTRA_BIG_TEXT)に加え、
     * InboxStyle等でまとめられた通知(EXTRA_TEXT_LINES)、MessagingStyle通知(EXTRA_MESSAGES)、
     * 件名のみの通知(EXTRA_SUB_TEXT)も拾う。取りこぼしを避けるため、可能な限り多くの形を試す。 */
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
        if (text.isEmpty()) text = extractMessagingStyleText(extras);
        if (text.isEmpty()) text = string(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        return text;
    }

    /** MessagingStyle(会話型)の通知は、実際の本文がEXTRA_TEXTではなくEXTRA_MESSAGESに
     * 入っていることがある。最後のメッセージの本文を拾う。 */
    private String extractMessagingStyleText(Bundle extras) {
        try {
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages == null || messages.length == 0) return "";
            List<Notification.MessagingStyle.Message> parsed =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages);
            if (parsed.isEmpty()) return "";
            return string(parsed.get(parsed.size() - 1).getText());
        } catch (Exception e) {
            return "";
        }
    }

    /** 診断用: Xパッケージから実際に何を受信しているかを記録する。他アプリの通知はここでは記録しない
     * (直近N件しか保持できないログが無関係な通知で埋まってしまうのを防ぐため)。 */
    private void logReceived(String packageName, String text) {
        RawLogEntity log = new RawLogEntity();
        log.timestamp = System.currentTimeMillis();
        log.packageName = packageName;
        log.isXPackage = true;
        log.textFound = !text.isEmpty();
        log.textPreview = text.substring(0, Math.min(text.length(), 60));
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

    /** 画面共有・録画中の機密保護機能などにより、Android自体が通知本文を伏せ字プレースホルダーに
     * 差し替えている場合を検出する。この場合、本当の投稿内容はOSレベルでどの通知リスナーからも
     * 見えなくなっており、アプリ側で復元する手段はない。 */
    private boolean isRedactedByOs(String text) {
        return text.contains("通知内容は表示されません");
    }

    private boolean isX(String pkg) {
        for (String x : X_PACKAGES) if (x.equals(pkg)) return true;
        return false;
    }

    /** 翻訳を試みるかどうかの判定。日本語の投稿を英語として誤翻訳しないことより、
     * 英語の投稿を「短いから」「日本語混じりだから」といった理由で翻訳し損ねないことを優先する。
     * 以前は英字4文字以上を要求していたため、"Yes!"のような短い投稿が翻訳されずに漏れていた。 */
    private boolean looksEnglish(String value) {
        int latin = 0, japanese = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latin++;
            if ((c >= 0x3040 && c <= 0x30ff) || (c >= 0x4e00 && c <= 0x9fff)) japanese++;
        }
        return latin > 0 && latin >= japanese;
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
        notificationExecutor.shutdown();
        lineExecutor.shutdown();
        super.onDestroy();
    }
}
