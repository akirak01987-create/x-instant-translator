package jp.crescendo.xtranslator.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** ホーム画面ウィジェット1個ごとの表示設定。widgetIdはAppWidgetManagerが払い出すIDをそのまま使う。 */
@Entity(tableName = "widget_configs")
public class WidgetConfigEntity {
    /** すべての通知を表示対象にする(絞り込みなし)。 */
    public static final long FILTER_ALL = 0;
    /** どのフィルターにも一致しなかった(デフォルト設定が適用された)通知だけを表示する。 */
    public static final long FILTER_DEFAULT_ONLY = -1;

    @PrimaryKey
    public int widgetId;

    /** ウィジェットの見出し。空欄ならウィジェット種別ごとの既定値を使う。 */
    public String title = "";
    /** どのフィルターに一致した通知を表示するか。FILTER_ALL/FILTER_DEFAULT_ONLY、またはFilterEntity.id。 */
    public long filterId = FILTER_ALL;
    public int maxItems = 20;
}
