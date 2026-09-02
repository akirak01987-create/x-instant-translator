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

    @Query("DELETE FROM notifications WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM notifications")
    int count();

    @Query("SELECT id FROM notifications ORDER BY receivedAt ASC LIMIT :n")
    List<Long> oldestIds(int n);

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);
}
