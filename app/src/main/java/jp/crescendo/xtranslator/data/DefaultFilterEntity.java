package jp.crescendo.xtranslator.data;

import android.graphics.Color;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** どのフィルターにも一致しなかった通知へ適用する既定設定。常に1行のみ存在する。 */
@Entity(tableName = "default_filter")
public class DefaultFilterEntity {
    @PrimaryKey
    public long id = 1;

    public boolean soundEnabled = true;
    /** 使用する通知音の種類(0〜NotificationChannels.SOUND_OPTION_COUNT-1)。 */
    public int soundOptionIndex = 0;
    public boolean popupEnabled = true;
    public boolean translateEnabled = true;
    public int textColor = Color.BLACK;
    public int backgroundColor = Color.WHITE;

    public static DefaultFilterEntity createDefault() {
        DefaultFilterEntity e = new DefaultFilterEntity();
        e.id = 1;
        e.soundEnabled = true;
        e.soundOptionIndex = 0;
        e.popupEnabled = true;
        e.translateEnabled = true;
        e.textColor = Color.BLACK;
        e.backgroundColor = Color.WHITE;
        return e;
    }
}
