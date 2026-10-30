package kz.uniplan.app.data;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.data.repository.PersonalSchedule;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.data.repository.StudyRepository.Result;
import kz.uniplan.app.util.Validation;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class PersonalScheduleTest {
    private Context context;
    private AppDatabase db;
    private StudyDao dao;
    private FakeAuth auth;
    private StudyRepository repository;
    private ExecutorService verificationWorker;
    private JSONObject expected;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        dao = db.studyDao();
        auth = new FakeAuth(true);
        repository = new StudyRepository(db, auth);
        verificationWorker = Executors.newSingleThreadExecutor();
        expected = fixture("personal_schedule_expected.json");
    }

    @After public void cleanup() {
        if (repository != null && auth.current() != null) drain(repository);
        if (db != null) onDatabase(() -> { db.close(); return null; });
        if (verificationWorker != null) verificationWorker.shutdownNow();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void firstPersonalSessionImportsAll23ExactRowsAndSevenSubjects() throws Exception {
        auth.select("demo-1"); drain(repository);
        List<SubjectEntity> subjects = onDatabase(() -> dao.subjects("demo-1"));
        List<LessonEntity> lessons = onDatabase(() -> dao.lessons("demo-1"));
        assertEquals(7, subjects.size()); assertEquals(23, lessons.size());
        ProfileEntity profile = onDatabase(() -> dao.profile("demo-1"));
        assertEquals("Ибраһим", profile.displayName); assertEquals("АЖҚ-321", profile.studyGroup);
        Map<Long, String> subjectNames = new HashMap<>();
        for (SubjectEntity subject : subjects) subjectNames.put(subject.id, subject.name);
        JSONArray rows = expected.getJSONArray("lessons");
        int[] dailyCounts = new int[7];
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i); LessonEntity lesson = lessons.get(i);
            assertEquals("demo-1", lesson.ownerUid);
            assertEquals(row.getInt(0), lesson.dayOfWeek); assertEquals(row.getInt(1), lesson.lessonNumber);
            assertEquals(minutes(row.getString(2)), lesson.startMinute); assertEquals(minutes(row.getString(3)), lesson.endMinute);
            assertEquals(expected.getJSONObject("subjects").getString(row.getString(4)), subjectNames.get(lesson.subjectId));
            assertEquals(row.getString(5), lesson.lessonType);
            assertEquals(expected.getJSONObject("teachers").getString(row.getString(6)), lesson.teacher);
            assertEquals(expected.getJSONObject("rooms").getString(row.getString(7)), lesson.room);
            String linkKey = row.getString(8);
            assertEquals(linkKey.isEmpty() ? "" : expected.getJSONObject("links").getString(linkKey), lesson.meetingUrl);
            assertTrue(Validation.webUrl(lesson.meetingUrl)); dailyCounts[lesson.dayOfWeek - 1]++;
        }
        assertArrayEquals(new int[]{6, 4, 3, 6, 3, 1, 0}, dailyCounts);
        assertEquals(expected.getJSONObject("links").getString("address"), lessons.get(22).meetingUrl);
        assertEquals(Integer.valueOf(1), onDatabase(() -> dao.datasetImported("demo-1", PersonalSchedule.DATASET_KEY)));
        assertEquals(Integer.valueOf(0), onDatabase(() -> dao.seeded("demo-1")));
    }

    @Test public void repeatSessionsDoNotDuplicateOrResurrectDeletedLessonsOrResetProfile() {
        auth.select("demo-1"); drain(repository);
        long deletedId = onDatabase(() -> dao.lessons("demo-1").get(0).id);
        assertNull(this.<Boolean>await(cb -> repository.deleteLesson(deletedId, cb)).error);
        assertNull(this.<Boolean>await(cb -> repository.saveProfile("Өзгертілген есім", "Жаңа топ", cb)).error);
        auth.select(null); auth.select("demo-1"); drain(repository);
        assertEquals(22, onDatabase(() -> dao.lessons("demo-1")).size());
        assertNull(onDatabase(() -> dao.lesson("demo-1", deletedId)));
        assertEquals("Өзгертілген есім", onDatabase(() -> dao.profile("demo-1")).displayName);
        assertEquals("Жаңа топ", onDatabase(() -> dao.profile("demo-1")).studyGroup);
        assertEquals(Boolean.FALSE, onDatabase(() -> PersonalSchedule.importOnce(db, "demo-1")));
        assertEquals(7, onDatabase(() -> dao.subjects("demo-1")).size());
    }

    @Test public void personalImportIsRestrictedToTheFirstDemoIdentity() {
        auth.select("demo-2"); drain(repository);
        assertTrue(onDatabase(() -> dao.subjects("demo-2")).isEmpty());
        assertTrue(onDatabase(() -> dao.lessons("demo-2")).isEmpty());
        assertEquals(Boolean.FALSE, onDatabase(() -> PersonalSchedule.importOnce(db, "demo-2")));
        FakeAuth firebaseAuth = new FakeAuth(false); firebaseAuth.select("demo-1");
        StudyRepository firebaseRepository = new StudyRepository(db, firebaseAuth);
        drain(firebaseRepository);
        assertTrue(onDatabase(() -> dao.lessons("demo-1")).isEmpty());
        assertEquals(Integer.valueOf(0), onDatabase(() -> dao.datasetImported("demo-1", PersonalSchedule.DATASET_KEY)));
        auth.select("demo-1"); drain(repository);
        long id = onDatabase(() -> dao.lessons("demo-1").get(0).id);
        auth.select("demo-2"); drain(repository);
        assertEquals("missing", this.<LessonEntity>await(cb -> repository.lesson(id, cb)).error);
        assertTrue(onDatabase(() -> dao.lessons("demo-2")).isEmpty());
    }

    @Test public void importAddsMissingDataAndPreservesExistingRowsAndSampleMarker() throws Exception {
        long existingSubject = onDatabase(() -> {
            SubjectEntity subject = new SubjectEntity(); subject.ownerUid = "demo-1";
            subject.name = "3D Max-та моделдеу"; subject.teacher = "Менің оқытушым"; subject.color = 17;
            return dao.insertSubject(subject);
        });
        LessonEntity exactFirst = rowLesson(expected.getJSONArray("lessons").getJSONArray(0), existingSubject);
        long exactId = onDatabase(() -> dao.insertLesson(exactFirst));
        long oldLessonId = onDatabase(() -> {
            LessonEntity lesson = new LessonEntity(); lesson.ownerUid = "demo-1"; lesson.subjectId = existingSubject;
            lesson.dayOfWeek = 7; lesson.room = "Менің аудиториям";
            return dao.insertLesson(lesson);
        });
        onDatabase(() -> {
            TaskEntity task = new TaskEntity(); task.ownerUid = "demo-1"; task.subjectId = existingSubject;
            task.title = "Менің тапсырмам"; task.dueAt = 100; dao.insertTask(task);
            NoteEntity note = new NoteEntity(); note.ownerUid = "demo-1"; note.title = "Менің жазбам";
            note.content = "Өз мәтінім"; dao.insertNote(note);
            SeedStateEntity sample = new SeedStateEntity(); sample.ownerUid = "demo-1"; sample.seededAt = 10;
            dao.insertSeed(sample); return null;
        });
        auth.select("demo-1"); drain(repository);
        assertEquals(7, onDatabase(() -> dao.subjects("demo-1")).size());
        assertEquals(24, onDatabase(() -> dao.lessons("demo-1")).size());
        assertEquals("Менің оқытушым", onDatabase(() -> dao.subject("demo-1", existingSubject)).teacher);
        assertEquals(17, onDatabase(() -> dao.subject("demo-1", existingSubject)).color);
        assertNotNull(onDatabase(() -> dao.lesson("demo-1", exactId)));
        assertEquals("Менің аудиториям", onDatabase(() -> dao.lesson("demo-1", oldLessonId)).room);
        assertEquals("Менің тапсырмам", onDatabase(() -> dao.tasks("demo-1").get(0)).title);
        assertEquals("Өз мәтінім", onDatabase(() -> dao.notes("demo-1").get(0)).content);
        assertEquals(Integer.valueOf(1), onDatabase(() -> dao.seeded("demo-1")));
        assertEquals(Integer.valueOf(1), onDatabase(() -> dao.datasetImported("demo-1", PersonalSchedule.DATASET_KEY)));
    }

    @Test public void lessonMetadataIsCopiedSavedAndValidatedWithoutChangingUrl() throws Exception {
        auth.select("demo-1"); drain(repository);
        LessonEntity lesson = onDatabase(() -> dao.lessons("demo-1").get(22));
        LessonEntity copy = lesson.copy();
        assertEquals(lesson.lessonNumber, copy.lessonNumber); assertEquals(lesson.lessonType, copy.lessonType);
        assertEquals(lesson.teacher, copy.teacher); assertEquals(lesson.meetingUrl, copy.meetingUrl);
        copy.lessonNumber = 9; copy.lessonType = "Практикалық сабақ"; copy.teacher = "Дәрежесі\nЖаңа оқытушы";
        assertNull(this.<Long>await(cb -> repository.saveLesson(copy, cb)).error);
        LessonEntity saved = onDatabase(() -> dao.lesson("demo-1", copy.id));
        assertEquals(9, saved.lessonNumber); assertEquals("Практикалық сабақ", saved.lessonType);
        assertEquals("Дәрежесі\nЖаңа оқытушы", saved.teacher);
        assertEquals(expected.getJSONObject("links").getString("address"), saved.meetingUrl);
        for (String url : new String[]{"javascript:alert(1)", "file:///tmp/file", "https:///missing-host", "https://bad host/path"}) {
            copy.meetingUrl = url;
            assertEquals("validation", this.<Long>await(cb -> repository.saveLesson(copy, cb)).error);
        }
        copy.meetingUrl = "http://meet.google.com/mhi-wnyv-ind"; copy.lessonNumber = 100;
        assertEquals("validation", this.<Long>await(cb -> repository.saveLesson(copy, cb)).error);
        assertEquals(saved.meetingUrl, onDatabase(() -> dao.lesson("demo-1", copy.id)).meetingUrl);
    }

    @Test public void failedImportRollsBackEveryDatasetRowAndCanBeRetried() {
        onDatabase(() -> {
            db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER abort_personal_import BEFORE INSERT ON lessons WHEN NEW.lessonNumber=10 BEGIN SELECT RAISE(ABORT, 'test abort'); END");
            return null;
        });
        boolean failed = onDatabase(() -> {
            try { PersonalSchedule.importOnce(db, "demo-1"); return false; }
            catch (RuntimeException expectedFailure) { return true; }
        });
        assertTrue(failed);
        assertTrue(onDatabase(() -> dao.subjects("demo-1")).isEmpty());
        assertTrue(onDatabase(() -> dao.lessons("demo-1")).isEmpty());
        assertNull(onDatabase(() -> dao.profile("demo-1")));
        assertEquals(Integer.valueOf(0), onDatabase(() -> dao.datasetImported("demo-1", PersonalSchedule.DATASET_KEY)));
        onDatabase(() -> { db.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER abort_personal_import"); return null; });
        assertEquals(Boolean.TRUE, onDatabase(() -> PersonalSchedule.importOnce(db, "demo-1")));
        assertEquals(23, onDatabase(() -> dao.lessons("demo-1")).size());
    }

    @Test public void versionOneMigrationPreservesDataAndValidatesTheNewRoomSchema() {
        String name = "personal-migration-test.db"; context.deleteDatabase(name);
        File path = context.getDatabasePath(name); assertTrue(path.getParentFile().isDirectory() || path.getParentFile().mkdirs());
        JSONObject schema = fixture("personal_schedule_database_v1.json");
        onDatabase(() -> {
            SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(path, null);
            try {
                JSONObject database = schema.getJSONObject("database"); JSONArray entities = database.getJSONArray("entities");
                for (int i = 0; i < entities.length(); i++) {
                    JSONObject entity = entities.getJSONObject(i); String table = entity.getString("tableName");
                    legacy.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
                    JSONArray indices = entity.optJSONArray("indices");
                    if (indices != null) for (int j = 0; j < indices.length(); j++) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                }
                JSONArray setup = database.getJSONArray("setupQueries");
                for (int i = 0; i < setup.length(); i++) legacy.execSQL(setup.getString(i));
                legacy.execSQL("INSERT INTO profiles VALUES('demo-1','Old name','Old group')");
                legacy.execSQL("INSERT INTO subjects VALUES(101,'demo-1','Existing subject','Existing teacher',17,0)");
                legacy.execSQL("INSERT INTO lessons VALUES(301,'demo-1',101,2,100,150,'Existing room')");
                legacy.execSQL("INSERT INTO tasks VALUES(201,'demo-1',101,'Existing task','Keep description',0,123456,1,0,10,20)");
                legacy.execSQL("INSERT INTO notes VALUES(401,'demo-1',101,'Existing note','Keep content',10,20)");
                legacy.execSQL("INSERT INTO seed_state VALUES('demo-1',42)");
                legacy.setVersion(1);
            } finally { legacy.close(); }
            return null;
        });
        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build();
        try {
            StudyDao migratedDao = migrated.studyDao();
            LessonEntity old = onDatabase(() -> migratedDao.lesson("demo-1", 301));
            assertNotNull(old); assertEquals(101, old.subjectId); assertEquals(100, old.startMinute); assertEquals(150, old.endMinute);
            assertEquals("Existing room", old.room); assertEquals(0, old.lessonNumber);
            assertEquals("", old.lessonType); assertEquals("", old.teacher); assertEquals("", old.meetingUrl);
            assertEquals("Keep content", onDatabase(() -> migratedDao.note("demo-1", 401)).content);
            assertEquals("Keep description", onDatabase(() -> migratedDao.task("demo-1", 201)).description);
            assertEquals("Old name", onDatabase(() -> migratedDao.profile("demo-1")).displayName);
            assertTrue(onDatabase(() -> migratedDao.syncState("demo-1")).dirty);
            assertEquals(0,onDatabase(() -> migratedDao.syncState("demo-1")).revision);
            assertEquals(Integer.valueOf(1), onDatabase(() -> migratedDao.seeded("demo-1")));
            assertEquals(Boolean.TRUE, onDatabase(() -> PersonalSchedule.importOnce(migrated, "demo-1")));
            assertEquals(24, onDatabase(() -> migratedDao.lessons("demo-1")).size());
            assertNotNull(onDatabase(() -> migratedDao.lesson("demo-1", 301)));
            assertEquals("Ибраһим", onDatabase(() -> migratedDao.profile("demo-1")).displayName);
            assertEquals("Keep content", onDatabase(() -> migratedDao.note("demo-1", 401)).content);
        } finally { onDatabase(() -> { migrated.close(); return null; }); context.deleteDatabase(name); }
    }

    private LessonEntity rowLesson(JSONArray row, long subjectId) throws Exception {
        LessonEntity value = new LessonEntity(); value.ownerUid = "demo-1"; value.subjectId = subjectId;
        value.dayOfWeek = row.getInt(0); value.lessonNumber = row.getInt(1); value.startMinute = minutes(row.getString(2)); value.endMinute = minutes(row.getString(3));
        value.lessonType = row.getString(5); value.teacher = expected.getJSONObject("teachers").getString(row.getString(6));
        value.room = expected.getJSONObject("rooms").getString(row.getString(7));
        String key = row.getString(8); value.meetingUrl = key.isEmpty() ? "" : expected.getJSONObject("links").getString(key); return value;
    }
    private static int minutes(String time) { String[] parts = time.split(":"); return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]); }
    private JSONObject fixture(String name) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new AssertionError("Missing test fixture: " + name);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void drain(StudyRepository target) { this.<SubjectEntity>await(cb -> target.subject(-1, cb)); }
    private static final class Outcome<T> { T value; String error; }
    private <T> Outcome<T> await(Consumer<Result<T>> operation) {
        CountDownLatch finished = new CountDownLatch(1); Outcome<T> result = new Outcome<>();
        operation.accept((value, error) -> { result.value = value; result.error = error; finished.countDown(); });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try {
            while (finished.getCount() != 0 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                if (finished.await(5, TimeUnit.MILLISECONDS)) return result;
            }
            shadowOf(Looper.getMainLooper()).idle(); assertEquals("Async operation timed out", 0, finished.getCount()); return result;
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private <T> T onDatabase(Callable<T> action) {
        try { return verificationWorker.submit(action).get(20, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new AssertionError("Database verification failed", failure); }
    }
    private static final class FakeAuth implements AuthRepository {
        private final MutableLiveData<Session> state = new MutableLiveData<>(null);
        private final boolean demo;
        private volatile Session current;
        FakeAuth(boolean demo) { this.demo = demo; }
        void select(String uid) { current = uid == null ? null : new Session(uid, "Test", "test@example.kz"); state.setValue(current); }
        public LiveData<Session> session() { return state; }
        public Session current() { return current; }
        public boolean isDemo() { return demo; }
        public void openDemo(int index) { select("demo-" + index); }
        public void signOut() { select(null); }
        public void signIn(String email, String password, Callback callback) { throw new UnsupportedOperationException(); }
        public void register(String name, String email, String password, Callback callback) { throw new UnsupportedOperationException(); }
        public void resetPassword(String email, Callback callback) { throw new UnsupportedOperationException(); }
    }
}
