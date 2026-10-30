package kz.uniplan.app.data;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.shadows.ShadowEnvironment;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.files.NoteFiles;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.data.repository.StudyRepository.Result;

/** Host-side integration tests: real generated Room DAO and native SQLite, no mocked DAO. */
@RunWith(RobolectricTestRunner.class)
// No TLS is exercised here; avoid Conscrypt's POSIX-only native loader on Windows.
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class RepositoryRobolectricTest {
    private AppDatabase db;
    private StudyDao dao;
    private StudyRepository repository;
    private NoteFiles files;
    private FakeAuth auth;
    private Context context;
    private File externalDirectory;
    private ExecutorService verificationWorker;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        ShadowEnvironment.addExternalDir("uniplan-test-storage");
        externalDirectory = context.getExternalFilesDir(null);
        assertNotNull(externalDirectory);
        ShadowEnvironment.setExternalStorageState(externalDirectory, Environment.MEDIA_MOUNTED);
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        dao = db.studyDao();
        verificationWorker = Executors.newSingleThreadExecutor();
        auth = new FakeAuth();
        auth.select("owner-a");
        repository = new StudyRepository(db, auth);
        files = new NoteFiles(context, auth, db);
    }

    @After public void cleanup() {
        if (repository != null && auth.current() != null) {
            // The callback also acts as a barrier for the profile initializer.
            this.<Boolean>await(cb -> repository.saveProfile("Test", "", cb));
        }
        if (db != null) onDatabase(() -> { db.close(); return null; });
        if (verificationWorker != null) verificationWorker.shutdownNow();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void taskCrudUndoAndSubjectRestrictionUseRealTransactions() {
        long subject = createSubject();
        Outcome<Long> inserted = await(cb -> repository.saveTask(task(subject), cb));
        assertNull(inserted.error);
        long id = inserted.value;
        Outcome<TaskEntity> loaded = await(cb -> repository.task(id, cb));
        loaded.value.title = "Жаңартылды";
        assertNull(this.<Long>await(cb -> repository.saveTask(loaded.value, cb)).error);
        assertNull(this.<Boolean>await(cb -> repository.completeTask(id, true, cb)).error);
        assertTrue(onDatabase(() -> dao.task("owner-a", id)).completed);
        assertEquals("linked", this.<Boolean>await(cb -> repository.deleteSubject(subject, cb)).error);
        assertNotNull(onDatabase(() -> dao.subject("owner-a", subject)));
        Outcome<TaskEntity> deleted = await(cb -> repository.deleteTask(id, cb));
        assertNull(onDatabase(() -> dao.task("owner-a", id)));
        assertNull(this.<Long>await(cb -> repository.restoreTask(deleted.value, cb)).error);
        assertNull(this.<Long>await(cb -> repository.restoreTask(deleted.value, cb)).error);
        List<TaskEntity> restored = onDatabase(() -> dao.tasks("owner-a"));
        assertEquals(1, restored.size());
        assertEquals(id, restored.get(0).id);
        assertEquals("Жаңартылды", restored.get(0).title);
    }

    @Test public void foreignIdsAndCompositeForeignKeysRejectCrossOwnerAccess() {
        long subjectA = createSubject();
        Outcome<Long> added = await(cb -> repository.saveTask(task(subjectA), cb));
        long taskA = added.value;
        TaskEntity old = onDatabase(() -> dao.task("owner-a", taskA));
        auth.select("owner-b");
        assertEquals("missing", this.<TaskEntity>await(cb -> repository.task(taskA, cb)).error);
        old.title = "Unauthorized";
        assertEquals("session", this.<Long>await(cb -> repository.saveTask(old, cb)).error);
        assertEquals("missing", this.<Long>await(cb -> repository.saveTask(task(subjectA), cb)).error);
        assertEquals("missing", this.<Boolean>await(cb -> repository.completeTask(taskA, true, cb)).error);
        assertEquals("missing", this.<TaskEntity>await(cb -> repository.deleteTask(taskA, cb)).error);
        assertEquals(Integer.valueOf(0), onDatabase(() -> dao.completeTask("owner-b", taskA, true, 1)));
        assertEquals(Integer.valueOf(0), onDatabase(() -> dao.deleteSubject("owner-b", subjectA)));
        assertTrue(onDatabase(() -> dao.tasks("owner-b")).isEmpty());
        assertEquals("Тапсырма", onDatabase(() -> dao.task("owner-a", taskA)).title);
        TaskEntity forged = task(subjectA); forged.ownerUid = "owner-b";
        boolean rejected = onDatabase(() -> {
            try { dao.insertTask(forged); return false; }
            catch (SQLiteConstraintException expected) { return true; }
        });
        assertTrue("SQLite must enforce the composite owner/subject foreign key", rejected);
    }

    @Test public void lessonAndNoteCrudValidateIntervalsAndPreserveOptionalSubject() {
        long subject = createSubject();
        LessonEntity lesson = new LessonEntity(); lesson.subjectId = subject; lesson.room = "305";
        Outcome<Long> addedLesson = await(cb -> repository.saveLesson(lesson, cb));
        assertNull(addedLesson.error);
        LessonEntity updated = onDatabase(() -> dao.lesson("owner-a", addedLesson.value));
        updated.endMinute = updated.startMinute;
        assertEquals("validation", this.<Long>await(cb -> repository.saveLesson(updated, cb)).error);
        updated.endMinute = 660; updated.room = "306";
        assertNull(this.<Long>await(cb -> repository.saveLesson(updated, cb)).error);
        assertEquals("306", onDatabase(() -> dao.lesson("owner-a", addedLesson.value)).room);
        NoteEntity note = note();
        Outcome<Long> addedNote = await(cb -> repository.saveNote(note, cb));
        assertNull(addedNote.error);
        assertNull(onDatabase(() -> dao.note("owner-a", addedNote.value)).subjectId);
        NoteEntity changed = onDatabase(() -> dao.note("owner-a", addedNote.value));
        changed.subjectId = subject; changed.content = "Жаңа мәтін";
        assertNull(this.<Long>await(cb -> repository.saveNote(changed, cb)).error);
        assertEquals("Жаңа мәтін", onDatabase(() -> dao.note("owner-a", addedNote.value)).content);
        assertNull(this.<Boolean>await(cb -> repository.deleteLesson(addedLesson.value, cb)).error);
        assertEquals("linked", this.<Boolean>await(cb -> repository.deleteSubject(subject, cb)).error);
        assertNull(this.<Boolean>await(cb -> repository.deleteNote(addedNote.value, cb)).error);
        assertNull(this.<Boolean>await(cb -> repository.deleteSubject(subject, cb)).error);
    }

    @Test public void profileIsInitializedWithoutOpeningScreenAndEditsSurviveRelogin() {
        createSubject();
        assertEquals("Test", onDatabase(() -> dao.profile("owner-a")).displayName);
        assertNull(this.<Boolean>await(cb -> repository.saveProfile("Ибраһим", "АЖҚ-321", cb)).error);
        auth.select("owner-b"); createSubject();
        assertNotNull(onDatabase(() -> dao.profile("owner-b")));
        auth.select("owner-a"); createSubject();
        ProfileEntity profile = onDatabase(() -> dao.profile("owner-a"));
        assertEquals("Ибраһим", profile.displayName); assertEquals("АЖҚ-321", profile.studyGroup);
    }

    @Test public void explicitDemoSeedIsIdempotentAndPartitionedByOwner() {
        assertEquals(Boolean.TRUE, this.<Boolean>await(cb -> repository.seedDemo(cb)).value);
        assertEquals(Boolean.FALSE, this.<Boolean>await(cb -> repository.seedDemo(cb)).value);
        assertEquals(2, onDatabase(() -> dao.subjects("owner-a")).size());
        assertEquals(4, onDatabase(() -> dao.tasks("owner-a")).size());
        auth.select("owner-b");
        assertTrue(onDatabase(() -> dao.tasks("owner-b")).isEmpty());
        assertEquals(Boolean.TRUE, this.<Boolean>await(cb -> repository.seedDemo(cb)).value);
        assertEquals(4, onDatabase(() -> dao.tasks("owner-a")).size());
        assertEquals(4, onDatabase(() -> dao.tasks("owner-b")).size());
    }

    @Test public void delayedOperationCannotWriteUsingTheNewSession() throws Exception {
        createSubject(); // Open the database and drain the initial profile write.
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        AtomicReference<Throwable> blockerFailure = new AtomicReference<>();
        Thread blocker = new Thread(() -> {
            try {
                db.runInTransaction(() -> {
                    locked.countDown();
                    try { if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Transaction gate timed out"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                });
            } catch (Throwable failure) { blockerFailure.set(failure); }
        }, "uniplan-test-transaction-gate");
        blocker.start();
        assertTrue(locked.await(10, TimeUnit.SECONDS));
        try {
            SubjectEntity queued = new SubjectEntity(); queued.name = "Captured A";
            AtomicReference<String> error = new AtomicReference<>();
            repository.saveSubject(queued, (value, problem) -> { error.set(problem); finished.countDown(); });
            auth.select("owner-b");
            release.countDown();
            pumpUntil(finished);
            assertEquals("session", error.get());
        } finally { release.countDown(); blocker.join(5000); }
        assertNull(blockerFailure.get());
        assertTrue(onDatabase(() -> dao.subjects("owner-b")).isEmpty());
        for (SubjectEntity value : onDatabase(() -> dao.subjects("owner-a"))) assertEquals("owner-a", value.ownerUid);
    }

    @Test public void liveDataClearsOldOwnerBeforeNewQueryEmits() {
        createSubject(); CountDownLatch initial = new CountDownLatch(1);
        Observer<List<SubjectEntity>> observer = values -> {
            if (values != null && values.size() == 1 && "owner-a".equals(values.get(0).ownerUid)) initial.countDown();
        };
        repository.subjects().observeForever(observer);
        try {
            pumpUntil(initial);
            auth.select("owner-b");
            assertNotNull(repository.subjects().getValue());
            assertTrue(repository.subjects().getValue().isEmpty());
        } finally { repository.subjects().removeObserver(observer); }
    }

    @Test public void internalAndExternalCopiesRoundTripUtf8WithoutChangingRoom() {
        Outcome<Long> saved = await(cb -> repository.saveNote(note(), cb)); long id = saved.value;
        String text = "Ә Ғ Қ Ң Ө Ұ Ү Һ І\nРусский текст 🚀";
        assertEquals(Boolean.TRUE, this.<Boolean>await(cb -> files.save(id, text, false, cb)).value);
        assertEquals(text, this.<String>await(cb -> files.read(id, false, cb)).value);
        assertEquals(Boolean.TRUE, this.<Boolean>await(cb -> files.save(id, text, true, cb)).value);
        assertEquals(text, this.<String>await(cb -> files.read(id, true, cb)).value);
        assertEquals("Бастапқы мәтін", onDatabase(() -> dao.note("owner-a", id)).content);
        auth.select("owner-b");
        assertEquals("io", this.<String>await(cb -> files.read(id, false, cb)).error);
        assertNotEquals(NoteFiles.ownerDirectory("owner-a"), NoteFiles.ownerDirectory("owner-b"));
        assertTrue(NoteFiles.ownerDirectory("../owner/a").matches("[0-9a-f]{64}"));
    }

    @Test public void externalReadOnlyAndMissingCopyReturnErrorsWithoutTouchingRoom() {
        Outcome<Long> saved = await(cb -> repository.saveNote(note(), cb)); long id = saved.value;
        assertEquals("io", this.<String>await(cb -> files.read(id, false, cb)).error);
        assertNull(this.<Boolean>await(cb -> files.save(id, "Copy", true, cb)).error);
        ShadowEnvironment.setExternalStorageState(externalDirectory, Environment.MEDIA_MOUNTED_READ_ONLY);
        assertEquals("Copy", this.<String>await(cb -> files.read(id, true, cb)).value);
        assertEquals("io", this.<Boolean>await(cb -> files.save(id, "Replacement", true, cb)).error);
        ShadowEnvironment.setExternalStorageState(externalDirectory, Environment.MEDIA_REMOVED);
        assertEquals("io", this.<String>await(cb -> files.read(id, true, cb)).error);
        assertEquals("Бастапқы мәтін", onDatabase(() -> dao.note("owner-a", id)).content);
    }

    @Test public void exportWritesClosesAndDistinguishesCancelIoAndOwnerMismatch() {
        Uri document = Uri.parse("content://uniplan.test/document/ok");
        class TrackedOutput extends ByteArrayOutputStream {
            boolean closed;
            @Override public void close() throws IOException { closed = true; super.close(); }
        }
        TrackedOutput output = new TrackedOutput();
        shadowOf(context.getContentResolver()).registerOutputStream(document, output);
        String body = "Ә Ғ Қ Ң Ө Ұ Ү Һ І";
        assertEquals(Boolean.TRUE, this.<Boolean>await(cb -> files.export(document, body, "owner-a", cb)).value);
        assertEquals(body, new String(output.toByteArray(), StandardCharsets.UTF_8)); assertTrue(output.closed);
        Outcome<Boolean> cancelled = await(cb -> files.export(null, body, cb));
        assertNull(cancelled.error); assertEquals(Boolean.FALSE, cancelled.value);
        Uri failingDocument = Uri.parse("content://uniplan.test/document/fail");
        shadowOf(context.getContentResolver()).registerOutputStream(failingDocument, new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("Simulated full storage"); }
        });
        assertEquals("io", this.<Boolean>await(cb -> files.export(failingDocument, body, cb)).error);
        assertEquals("session", this.<Boolean>await(cb -> files.export(document, body, "owner-b", cb)).error);
    }

    private long createSubject() {
        SubjectEntity subject = new SubjectEntity(); subject.name = "Әдебиет"; subject.teacher = "Оқытушы";
        Outcome<Long> result = await(cb -> repository.saveSubject(subject, cb));
        assertNull(result.error); return result.value;
    }
    private TaskEntity task(long subjectId) {
        TaskEntity value = new TaskEntity(); value.title = "Тапсырма"; value.subjectId = subjectId;
        value.dueAt = System.currentTimeMillis() + 60000; return value;
    }
    private NoteEntity note() {
        NoteEntity value = new NoteEntity(); value.title = "Қазақша"; value.content = "Бастапқы мәтін"; return value;
    }
    private static final class Outcome<T> { T value; String error; }
    private <T> Outcome<T> await(Consumer<Result<T>> operation) {
        CountDownLatch finished = new CountDownLatch(1); Outcome<T> result = new Outcome<>();
        operation.accept((value, error) -> { result.value = value; result.error = error; finished.countDown(); });
        pumpUntil(finished); return result;
    }
    /** Workers run normally; each pass delivers main-handler callbacks, then yields to workers. */
    private void pumpUntil(CountDownLatch finished) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try {
            while (finished.getCount() != 0 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                if (finished.await(5, TimeUnit.MILLISECONDS)) return;
            }
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals("Async operation timed out", 0, finished.getCount());
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    /** Direct verification queries also remain off Android's main thread. */
    private <T> T onDatabase(Callable<T> action) {
        try { return verificationWorker.submit(action).get(20, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new AssertionError("Database verification failed", failure); }
    }
    private static final class FakeAuth implements AuthRepository {
        private final MutableLiveData<Session> state = new MutableLiveData<>();
        private volatile Session current;
        void select(String uid) { current = uid == null ? null : new Session(uid, "Test", "test@example.kz"); state.setValue(current); }
        public LiveData<Session> session() { return state; }
        public Session current() { return current; }
        public boolean isDemo() { return true; }
        public void openDemo(int index) { select("owner-" + index); }
        public void signOut() { select(null); }
        public void signIn(String email, String password, Callback callback) { throw new UnsupportedOperationException(); }
        public void register(String name, String email, String password, Callback callback) { throw new UnsupportedOperationException(); }
        public void resetPassword(String email, Callback callback) { throw new UnsupportedOperationException(); }
    }
}
