package jp.crescendo.xtranslator.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public final class NotificationChannels {
    public static final String SILENT = "x_translated_silent";

    /** 音ありチャンネルは通知音ごとに5つ用意しておく。Androidではチャンネル作成後に音を
     * 変更できない(削除して作り直す必要がある)ため、選択肢の数だけ最初から作っておき、
     * ユーザーはどのチャンネルを使うかを選ぶだけにする。 */
    private static final String SOUND_CHANNEL_PREFIX = "x_translated_sound_";
    public static final int SOUND_OPTION_COUNT = 5;

    private NotificationChannels() {}

    public static void ensure(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        List<Uri> sounds = availableSoundUris(context);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        for (int i = 0; i < SOUND_OPTION_COUNT; i++) {
            String id = soundChannelId(i);
            if (nm.getNotificationChannel(id) != null) continue;
            NotificationChannel channel = new NotificationChannel(
                    id, "X通知（音あり・" + soundLabel(context, i) + "）", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Xの投稿通知をポップアップと通知音付きで表示します");
            Uri sound = i < sounds.size() ? sounds.get(i) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            channel.setSound(sound, attrs);
            nm.createNotificationChannel(channel);
        }

        if (nm.getNotificationChannel(SILENT) == null) {
            NotificationChannel silent = new NotificationChannel(
                    SILENT, "X通知（無音）", NotificationManager.IMPORTANCE_HIGH);
            silent.setDescription("Xの投稿通知をポップアップ表示しますが、音とバイブレーションは鳴りません");
            silent.setSound(null, null);
            silent.enableVibration(false);
            nm.createNotificationChannel(silent);
        }
    }

    /** ポップアップ時に実際に使う音ありチャンネルのID。フィルターごとに選んだ通知音に対応する。 */
    public static String soundChannelId(int index) {
        if (index < 0 || index >= SOUND_OPTION_COUNT) index = 0;
        return SOUND_CHANNEL_PREFIX + index;
    }

    /** 設定画面のプレビュー・一覧表示用に、端末が持つ通知音の一覧を取得する。 */
    public static List<Uri> availableSoundUris(Context context) {
        List<Uri> result = new ArrayList<>();
        RingtoneManager manager = new RingtoneManager(context);
        manager.setType(RingtoneManager.TYPE_NOTIFICATION);
        try {
            android.database.Cursor cursor = manager.getCursor();
            while (result.size() < SOUND_OPTION_COUNT && cursor.moveToNext()) {
                result.add(manager.getRingtoneUri(cursor.getPosition()));
            }
        } catch (Exception ignored) {
            // 端末によっては取得できないことがある。既定の通知音で埋め合わせる。
        }
        while (result.size() < SOUND_OPTION_COUNT) {
            result.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }
        return result;
    }

    /** i番目の通知音の表示名(端末のロケールに従った名前)。取得できない場合は「通知音N」。 */
    public static String soundLabel(Context context, int index) {
        List<Uri> sounds = availableSoundUris(context);
        if (index < 0 || index >= sounds.size()) return "通知音" + (index + 1);
        Uri uri = sounds.get(index);
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
            if (ringtone != null) {
                String title = ringtone.getTitle(context);
                if (title != null && !title.trim().isEmpty()) return title;
            }
        } catch (Exception ignored) {
            // フォールバックへ
        }
        return "通知音" + (index + 1);
    }
}
