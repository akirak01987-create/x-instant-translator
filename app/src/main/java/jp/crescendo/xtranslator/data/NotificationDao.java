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

    @Query("DELETE FROM notifications WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM notifications")
    int count();

    @Query("DELETE FROM notifications WHERE receivedAt < :cutoffMillis")
    void deleteOlderThan(long cutoffMillis);
}
