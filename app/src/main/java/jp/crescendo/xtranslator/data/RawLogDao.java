package jp.crescendo.xtranslator.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RawLogDao {
    @Insert
    void insert(RawLogEntity entity);

    @Query("SELECT * FROM raw_log ORDER BY timestamp DESC LIMIT 50")
    List<RawLogEntity> getRecent();

    @Query("DELETE FROM raw_log WHERE id NOT IN (SELECT id FROM raw_log ORDER BY timestamp DESC LIMIT 50)")
    void trimToRecent();

    @Query("DELETE FROM raw_log")
    void deleteAll();
}
