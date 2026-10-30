package kz.uniplan.app.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.files.NoteFiles;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.data.repository.StudyRepository.Result;

/** These tests execute against Android SQLite/Room, not mocked DAOs. */
@RunWith(AndroidJUnit4.class)
public class DataIsolationTest {
    private AppDatabase db;
    private StudyDao dao;
    private StudyRepository repo;
    private NoteFiles files;
    private FakeAuth auth;
    private Context context;
    @Before public void setup() {
        context=ApplicationProvider.getApplicationContext();
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase.class).build();dao=db.studyDao();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            auth=new FakeAuth();auth.select("test-owner-a");repo=new StudyRepository(db,auth);files=new NoteFiles(context,auth,db);
        });
    }
    @After public void finish() {
        // Drain repository work (including initial profile creation) before closing SQLite.
        if(repo!=null && auth.current()!=null) this.<Boolean>await(cb -> repo.saveProfile("Test","",cb));
        db.close();
    }
    private void user(String uid) { InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> auth.select(uid)); }
    private long subject() {
        SubjectEntity subject=new SubjectEntity();subject.name="Әдебиет";subject.teacher="Оқытушы";
        Outcome<Long> result=await(cb -> repo.saveSubject(subject,cb));assertNull(result.error);return result.value;
    }
    private TaskEntity task(long subject) { TaskEntity task=new TaskEntity();task.title="Тапсырма";task.subjectId=subject;task.dueAt=System.currentTimeMillis()+60000;return task; }
    private NoteEntity note() { NoteEntity note=new NoteEntity();note.title="Қазақша";note.content="Ә Ғ Қ Ң Ө Ұ Ү Һ І";return note; }

    @Test public void taskCrudAndUndoAreAtomicAndIdempotent() {
        long subject=subject();TaskEntity task=task(subject);
        Outcome<Long> added=await(cb -> repo.saveTask(task,cb));assertNull(added.error);
        long id=added.value;
        Outcome<TaskEntity> loaded=await(cb -> repo.task(id,cb));assertEquals("Тапсырма",loaded.value.title);
        loaded.value.title="Өзгертілді";assertNull(this.<Long>await(cb -> repo.saveTask(loaded.value,cb)).error);
        assertNull(this.<Boolean>await(cb -> repo.completeTask(id,true,cb)).error);
        assertTrue(dao.task(auth.current().uid,id).completed);
        Outcome<TaskEntity> deleted=await(cb -> repo.deleteTask(id,cb));assertNull(dao.task(auth.current().uid,id));
        assertNull(this.<Long>await(cb -> repo.restoreTask(deleted.value,cb)).error);
        assertNull(this.<Long>await(cb -> repo.restoreTask(deleted.value,cb)).error);
        assertEquals(1,dao.tasks(auth.current().uid).size());assertEquals(id,dao.tasks(auth.current().uid).get(0).id);
        assertEquals("linked",this.<Boolean>await(cb -> repo.deleteSubject(subject,cb)).error);
        assertNotNull(dao.subject(auth.current().uid,subject));
    }
    @Test public void foreignIdsCannotBeReadChangedOrLinked() {
        long subjectA=subject();Outcome<Long> created=await(cb -> repo.saveTask(task(subjectA),cb));
        long taskA=created.value;TaskEntity old=dao.task("test-owner-a",taskA);
        user("test-owner-b");
        assertEquals("missing",this.<TaskEntity>await(cb -> repo.task(taskA,cb)).error);
        old.title="Attack";assertEquals("session",this.<Long>await(cb -> repo.saveTask(old,cb)).error);
        TaskEntity foreignLink=task(subjectA);assertEquals("missing",this.<Long>await(cb -> repo.saveTask(foreignLink,cb)).error);
        assertEquals("missing",this.<Boolean>await(cb -> repo.completeTask(taskA,true,cb)).error);
        assertEquals("missing",this.<TaskEntity>await(cb -> repo.deleteTask(taskA,cb)).error);
        assertEquals(0,dao.completeTask("test-owner-b",taskA,true,1));
        assertEquals(0,dao.deleteSubject("test-owner-b",subjectA));
        assertTrue(dao.tasks("test-owner-b").isEmpty());
        assertEquals("Тапсырма",dao.task("test-owner-a",taskA).title);
        TaskEntity direct=task(subjectA);direct.ownerUid="test-owner-b";
        try { dao.insertTask(direct);fail("Composite owner/subject FK must reject cross-owner link"); }
        catch(SQLiteConstraintException expected) { assertTrue(dao.tasks("test-owner-b").isEmpty()); }
    }
    @Test public void noteLessonAndProfileCrudRemainOwnerScoped() {
        long sid=subject();LessonEntity lesson=new LessonEntity();lesson.subjectId=sid;lesson.room="305";
        Outcome<Long> addedLesson=await(cb -> repo.saveLesson(lesson,cb));assertNull(addedLesson.error);
        LessonEntity changed=dao.lesson(auth.current().uid,addedLesson.value);changed.endMinute=changed.startMinute;
        assertEquals("validation",this.<Long>await(cb -> repo.saveLesson(changed,cb)).error);
        changed.endMinute=660;changed.room="306";assertNull(this.<Long>await(cb -> repo.saveLesson(changed,cb)).error);
        assertEquals("306",dao.lesson(auth.current().uid,addedLesson.value).room);
        NoteEntity note=note();Outcome<Long> addedNote=await(cb -> repo.saveNote(note,cb));assertNull(addedNote.error);
        NoteEntity changedNote=dao.note(auth.current().uid,addedNote.value);changedNote.content="Жаңартылды";changedNote.subjectId=sid;
        assertNull(this.<Long>await(cb -> repo.saveNote(changedNote,cb)).error);
        assertEquals("Жаңартылды",dao.note(auth.current().uid,addedNote.value).content);
        assertNull(this.<Boolean>await(cb -> repo.saveProfile("Ибраһим","АЖҚ-321",cb)).error);
        assertEquals("АЖҚ-321",dao.profile(auth.current().uid).studyGroup);
        assertNull(this.<Boolean>await(cb -> repo.deleteLesson(addedLesson.value,cb)).error);
        assertNull(this.<Boolean>await(cb -> repo.deleteNote(addedNote.value,cb)).error);
        assertNull(this.<Boolean>await(cb -> repo.deleteSubject(sid,cb)).error);
        assertTrue(dao.notes(auth.current().uid).isEmpty());assertTrue(dao.lessons(auth.current().uid).isEmpty());
    }
    @Test public void initialProfileIsCreatedWithoutObservingProfileScreen() {
        subject(); // queued after the auth-triggered initializer
        ProfileEntity initial=dao.profile("test-owner-a");assertNotNull(initial);assertEquals("Test",initial.displayName);
        assertNull(this.<Boolean>await(cb -> repo.saveProfile("Өз атым","АЖҚ-321",cb)).error);
        user("test-owner-b");subject();assertNotNull(dao.profile("test-owner-b"));
        user("test-owner-a");subject();assertEquals("Өз атым",dao.profile("test-owner-a").displayName);
    }
    @Test public void demoSeedRunsExactlyOnceForEachOwner() {
        assertEquals(Boolean.TRUE,this.<Boolean>await(cb -> repo.seedDemo(cb)).value);
        assertEquals(Boolean.FALSE,this.<Boolean>await(cb -> repo.seedDemo(cb)).value);
        assertEquals(2,dao.subjects(auth.current().uid).size());assertEquals(4,dao.tasks(auth.current().uid).size());
        user("test-owner-b");assertTrue(dao.tasks(auth.current().uid).isEmpty());
        assertEquals(Boolean.TRUE,this.<Boolean>await(cb -> repo.seedDemo(cb)).value);
        assertEquals(4,dao.tasks("test-owner-a").size());assertEquals(4,dao.tasks("test-owner-b").size());
    }
    @Test public void delayedOperationNeverUsesTheNewOwnersUid() throws Exception {
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), finished=new CountDownLatch(1);
        Thread lockHolder=new Thread(() -> db.runInTransaction(() -> {
            locked.countDown();
            try { if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("Transaction gate timed out"); }
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
        }));
        lockHolder.start();assertTrue(locked.await(5,TimeUnit.SECONDS));
        SubjectEntity queued=new SubjectEntity();queued.name="Captured A";
        AtomicReference<String> resultError=new AtomicReference<>();
        repo.saveSubject(queued,(value,error) -> {resultError.set(error);finished.countDown();});
        user("test-owner-b");release.countDown();
        assertTrue(finished.await(15,TimeUnit.SECONDS));lockHolder.join(5000);
        assertEquals("session",resultError.get());assertTrue(dao.subjects("test-owner-b").isEmpty());
        // The A operation may be cancelled before it starts or finish in A's partition.
        for(SubjectEntity value:dao.subjects("test-owner-a")) assertEquals("test-owner-a",value.ownerUid);
    }
    @Test public void observedListIsClearedBeforeTheNewOwnersQueryArrives() throws Exception {
        subject();CountDownLatch firstOwnerArrived=new CountDownLatch(1);
        Observer<List<SubjectEntity>> observer=values -> {
            if(values!=null && values.size()==1 && values.get(0).ownerUid.equals("test-owner-a")) firstOwnerArrived.countDown();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> repo.subjects().observeForever(observer));
        try {
            assertTrue(firstOwnerArrived.await(10,TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                auth.select("test-owner-b");assertNotNull(repo.subjects().getValue());assertTrue(repo.subjects().getValue().isEmpty());
            });
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> repo.subjects().removeObserver(observer));
        }
    }
    @Test public void copiesRoundTripUtf8AndDoNotReplaceRoom() {
        Outcome<Long> saved=await(cb -> repo.saveNote(note(),cb));long id=saved.value;
        String body="Ә Ғ Қ Ң Ө Ұ Ү Һ І\nРусский текст 🚀";
        assertEquals(Boolean.TRUE,this.<Boolean>await(cb -> files.save(id,body,false,cb)).value);
        assertEquals(body,this.<String>await(cb -> files.read(id,false,cb)).value);
        assertEquals("Ә Ғ Қ Ң Ө Ұ Ү Һ І",dao.note(auth.current().uid,id).content);
        assertEquals(Boolean.TRUE,this.<Boolean>await(cb -> files.save(id,body,true,cb)).value);
        assertEquals(body,this.<String>await(cb -> files.read(id,true,cb)).value);
        user("test-owner-b");assertEquals("io",this.<String>await(cb -> files.read(id,false,cb)).error);
        assertNotEquals(NoteFiles.ownerDirectory("test-owner-a"),NoteFiles.ownerDirectory("test-owner-b"));
        assertTrue(NoteFiles.ownerDirectory("../owner/a").matches("[0-9a-f]{64}"));
    }
    @Test public void exportCancellationAndIoFailureHaveDistinctResults() {
        Outcome<Boolean> cancelled=await(cb -> files.export(null,"Текст",cb));assertNull(cancelled.error);assertFalse(cancelled.value);
        Outcome<Boolean> denied=await(cb -> files.export(Uri.parse("content://invalid.uniplan.test/denied"),"Текст",cb));
        assertNotNull(denied.error);assertNotEquals(Boolean.TRUE,denied.value);
        assertEquals("session",this.<Boolean>await(cb -> files.export(Uri.parse("content://invalid.uniplan.test/file"),"Old data","another-owner",cb)).error);
        assertEquals("io",this.<String>await(cb -> files.read(999999,false,cb)).error);
    }
    private static final class Outcome<T> { T value;String error; }
    private <T> Outcome<T> await(Consumer<Result<T>> operation) {
        CountDownLatch latch=new CountDownLatch(1);Outcome<T> result=new Outcome<>();
        operation.accept((value,error) -> { result.value=value;result.error=error;latch.countDown(); });
        try { assertTrue("Async operation timed out",latch.await(15,TimeUnit.SECONDS)); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new AssertionError(e); }
        return result;
    }
    private static final class FakeAuth implements AuthRepository {
        private final MutableLiveData<Session> state=new MutableLiveData<>();
        private volatile Session current;
        void select(String uid) { current=uid==null?null:new Session(uid,"Test","test@example.kz");state.setValue(current); }
        public LiveData<Session> session(){return state;} public Session current(){return current;} public boolean isDemo(){return true;}
        public void openDemo(int index){select("test-owner-"+index);} public void signOut(){select(null);}
        public void signIn(String email,String password,Callback cb){throw new UnsupportedOperationException();}
        public void register(String name,String email,String password,Callback cb){throw new UnsupportedOperationException();}
        public void resetPassword(String email,Callback cb){throw new UnsupportedOperationException();}
    }
}
