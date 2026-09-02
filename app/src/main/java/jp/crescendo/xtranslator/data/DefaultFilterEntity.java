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
    public boolean popupEnabled = true;
    public boolean translateEnabled = true;
    public int textColor = Color.BLACK;
    public int backgroundColor = Color.WHITE;

    public static DefaultFilterEntity createDefault() {
        DefaultFilterEntity e = new DefaultFilterEntity();
        e.id = 1;
        e.soundEnabled = true;
        e.popupEnabled = true;
        e.translateEnabled = true;
        e.textColor = Color.BLACK;
        e.backgroundColor = Color.WHITE;
        return e;
    }
}
