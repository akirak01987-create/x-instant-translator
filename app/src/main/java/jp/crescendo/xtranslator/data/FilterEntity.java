package jp.crescendo.xtranslator.data;

import android.graphics.Color;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "filters")
public class FilterEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** @ユーザー名または表示名の一部。空欄なら全投稿者が対象。 */
    public String authorPattern = "";
    /** キーワードを改行区切りで保持。空なら本文条件なし。 */
    public String keywordsRaw = "";
    /** true: すべて含む(AND) / false: いずれかを含む(OR) */
    public boolean matchAll = false;
    public boolean caseSensitive = false;
    public boolean soundEnabled = true;
    /** 使用する通知音の種類(0〜NotificationChannels.SOUND_OPTION_COUNT-1)。 */
    public int soundOptionIndex = 0;
    public boolean popupEnabled = true;
    public boolean translateEnabled = true;
    /** trueなら、一致した投稿の翻訳文をLINE公式アカウントから友だち全員へブロードキャスト配信する。 */
    public boolean lineEnabled = false;
    public boolean enabled = true;
    public int textColor = Color.BLACK;
    public int backgroundColor = Color.WHITE;
    public int sortOrder = 0;
}
