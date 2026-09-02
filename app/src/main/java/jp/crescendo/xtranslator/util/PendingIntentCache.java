package jp.crescendo.xtranslator.util;

import android.app.PendingIntent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * プロセスが生存している間だけ、履歴項目IDと元通知のPendingIntentを対応付けるキャッシュ。
 * プロセス終了後(端末再起動など)は失われるため、履歴タップ時はXアプリの起動にフォールバックする。
 */
public final class PendingIntentCache {
    private static final int MAX_ENTRIES = 200;
    private static final Map<Long, PendingIntent> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<Long, PendingIntent>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, PendingIntent> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    private PendingIntentCache() {}

    public static void put(long historyId, PendingIntent intent) {
        if (intent != null) CACHE.put(historyId, intent);
    }

    public static PendingIntent get(long historyId) {
        return CACHE.get(historyId);
    }
}
