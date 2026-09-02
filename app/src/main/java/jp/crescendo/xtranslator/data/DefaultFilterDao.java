package jp.crescendo.xtranslator.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DefaultFilterDao {
    @Query("SELECT * FROM default_filter WHERE id = 1 LIMIT 1")
    DefaultFilterEntity getRaw();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(DefaultFilterEntity entity);

    default DefaultFilterEntity getOrCreate() {
        DefaultFilterEntity e = getRaw();
        if (e == null) {
            e = DefaultFilterEntity.createDefault();
            save(e);
        }
        return e;
    }
}
