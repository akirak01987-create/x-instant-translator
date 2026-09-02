package jp.crescendo.xtranslator.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FilterDao {
    @Query("SELECT * FROM filters ORDER BY sortOrder ASC")
    List<FilterEntity> getAll();

    @Query("SELECT * FROM filters WHERE id = :id LIMIT 1")
    FilterEntity getById(long id);

    @Insert
    long insert(FilterEntity filter);

    @Update
    void update(FilterEntity filter);

    @Delete
    void delete(FilterEntity filter);

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM filters")
    int getMaxOrder();
}
