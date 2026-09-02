package jp.crescendo.xtranslator.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 通知リスナーが実際に何を受信しているかを確認するための診断用ログ。
 * Xパッケージ以外は本文を記録しない（他アプリの通知内容を保存しないため）。
 */
@Entity(tableName = "raw_log")
public class RawLogEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public String packageName = "";
    public boolean isXPackage;
    public boolean textFound;
    /** Xパッケージの場合のみ、本文の先頭部分を記録する。 */
    public String textPreview = "";
}
