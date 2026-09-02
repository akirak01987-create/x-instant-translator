package jp.crescendo.xtranslator.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public final class NotificationChannels {
    public static final String SOUND = "x_translated_sound";
    public static final String SILENT = "x_translated_silent";

    private NotificationChannels() {}

    public static void ensure(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        if (nm.getNotificationChannel(SOUND) == null) {
            NotificationChannel sound = new NotificationChannel(
                    SOUND, "X通知（音あり）", NotificationManager.IMPORTANCE_HIGH);
            sound.setDescription("Xの投稿通知をポップアップと通知音付きで表示します");
            nm.createNotificationChannel(sound);
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
}
