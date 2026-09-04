package jp.crescendo.xtranslator.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {NotificationEntity.class, FilterEntity.class, DefaultFilterEntity.class, RawLogEntity.class, WidgetConfigEntity.class}, version = 8, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** フィルターごとの通知音選択(soundOptionIndex)を追加。列を足すだけなので、破壊的な
     * fallbackToDestructiveMigrationに頼らず既存のフィルター・履歴データを保持したまま移行する。 */
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE filters ADD COLUMN soundOptionIndex INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE default_filter ADD COLUMN soundOptionIndex INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** フィルターごとのLINE配信選択(lineEnabled)を追加。こちらも列を足すだけなので破壊的リセットなしで移行する。 */
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE filters ADD COLUMN lineEnabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE default_filter ADD COLUMN lineEnabled INTEGER NOT NULL DEFAULT 0");
        }
    };

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
                            .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
