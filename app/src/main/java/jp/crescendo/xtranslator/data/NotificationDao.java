package jp.crescendo.xtranslator.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(NotificationEntity entity);

    @Query("SELECT * FROM notifications ORDER BY receivedAt DESC")
    List<NotificationEntity> getAll();

    /** 通知グループのサマリー表示用。ポップアップを出した投稿のうち直近N件を新しい順に返す。 */
    @Query("SELECT * FROM notifications WHERE popupShown = 1 ORDER BY receivedAt DESC LIMIT :limit")
    List<NotificationEntity> getRecentPopups(int limit);

    /** AI分析画面の時間範囲絞り込み用。指定時刻以降に受信した投稿を古い順に返す
     * (時系列で並んでいた方がAIが状況の推移を把握しやすいため)。 */
    @Query("SELECT * FROM notifications WHERE receivedAt >= :cutoffMillis ORDER BY receivedAt ASC")
    List<NotificationEntity> getSince(long cutoffMillis);

    @Query("DELETE FROM notifications WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM notifications")
    int count();

    @Query("DELETE FROM notifications WHERE receivedAt < :cutoffMillis")
    void deleteOlderThan(long cutoffMillis);
}
