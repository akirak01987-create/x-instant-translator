package jp.crescendo.xtranslator.data;

import android.graphics.Color;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "filters")
public class FilterEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name = "";
    /** @ユーザー名または表示名の一部。空欄なら全投稿者が対象。 */
    public String authorPattern = "";
    /** キーワードを改行区切りで保持。空なら本文条件なし。 */
    public String keywordsRaw = "";
    /** true: すべて含む(AND) / false: いずれかを含む(OR) */
    public boolean matchAll = false;
    public boolean caseSensitive = false;
    public boolean soundEnabled = true;
    public boolean popupEnabled = true;
    public boolean translateEnabled = true;
    public boolean enabled = true;
    public int textColor = Color.BLACK;
    public int backgroundColor = Color.WHITE;
    public int sortOrder = 0;
}
