package jp.crescendo.xtranslator.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications", indices = {@Index(value = "dedupeKey", unique = true)})
public class NotificationEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String dedupeKey;
    public long receivedAt;
    public String author;
    public String originalText;
    public String translatedText;
    public boolean wasTranslated;
    public String sourcePackage;
    public int textColor;
    public int backgroundColor;
    public long filterId;
    public String filterName;
    public boolean popupShown;
    public boolean soundPlayed;
}
