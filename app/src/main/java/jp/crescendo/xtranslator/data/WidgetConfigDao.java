package jp.crescendo.xtranslator.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WidgetConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WidgetConfigEntity entity);

    @Query("SELECT * FROM widget_configs WHERE widgetId = :widgetId LIMIT 1")
    WidgetConfigEntity getById(int widgetId);

    @Query("SELECT * FROM widget_configs")
    List<WidgetConfigEntity> getAll();

    @Query("DELETE FROM widget_configs WHERE widgetId = :widgetId")
    void deleteById(int widgetId);
}
