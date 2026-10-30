package kz.uniplan.app.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;

@Database(entities = {ProfileEntity.class, SubjectEntity.class, TaskEntity.class,
        LessonEntity.class, NoteEntity.class, SeedStateEntity.class, DatasetImportEntity.class, SyncStateEntity.class}, version = 3, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    public abstract StudyDao studyDao();

    /** Existing lessons retain their IDs, links, times and room; new metadata starts empty. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN lessonNumber INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE lessons ADD COLUMN lessonType TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE lessons ADD COLUMN teacher TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE lessons ADD COLUMN meetingUrl TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE TABLE IF NOT EXISTS dataset_imports (ownerUid TEXT NOT NULL, datasetKey TEXT NOT NULL, importedAt INTEGER NOT NULL, PRIMARY KEY(ownerUid, datasetKey))");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (ownerUid TEXT NOT NULL, revision INTEGER NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY(ownerUid))");
            // Existing owners have unsynced data; do not silently replace it on first cloud contact.
            db.execSQL("INSERT INTO sync_state(ownerUid,revision,dirty) SELECT ownerUid,0,1 FROM profiles");
        }
    };

    public static AppDatabase get(Context context) {
        if (instance == null) synchronized (AppDatabase.class) {
            if (instance == null) instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "uniplan.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3).build();
        }
        return instance;
    }
}
