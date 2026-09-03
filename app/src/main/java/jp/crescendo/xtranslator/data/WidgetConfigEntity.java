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
    /** 表示対象のフィルターIDをカンマ区切りで保持する(デフォルト設定はFilterMatcher.DEFAULT_FILTER_ID=0)。
     * 空文字/未設定の場合はすべての通知を対象とする。複数選択可。 */
    public String filterIds = "";
    public int maxItems = 20;
}
