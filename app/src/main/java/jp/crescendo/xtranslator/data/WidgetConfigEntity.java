package jp.crescendo.xtranslator.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** ホーム画面ウィジェット1個ごとの表示設定。widgetIdはAppWidgetManagerが払い出すIDをそのまま使う。 */
@Entity(tableName = "widget_configs")
public class WidgetConfigEntity {
    @PrimaryKey
    public int widgetId;

    /** ウィジェットの見出し。空欄ならウィジェット種別ごとの既定値を使う。 */
    public String title = "";
    /** 投稿者名または本文に部分一致するキーワード。空欄ならすべての通知が対象。 */
    public String keyword = "";
    public int maxItems = 20;
}
