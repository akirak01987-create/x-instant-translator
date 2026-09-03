package jp.crescendo.xtranslator.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {NotificationEntity.class, FilterEntity.class, DefaultFilterEntity.class, RawLogEntity.class, WidgetConfigEntity.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NotificationDao notificationDao();

    public abstract FilterDao filterDao();

    public abstract DefaultFilterDao defaultFilterDao();

    public abstract RawLogDao rawLogDao();

    public abstract WidgetConfigDao widgetConfigDao();

    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "x_translator.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
