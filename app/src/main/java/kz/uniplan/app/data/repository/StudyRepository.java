package kz.uniplan.app.data.repository;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.auth.AuthRepository.Session;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.util.Validation;
import kz.uniplan.app.data.sync.CloudSync;
import kz.uniplan.app.data.auth.SupabaseHttp;

/** Single owner-scoped boundary for UI. Input objects are copied before enqueueing. */
public final class StudyRepository {
    public interface Result<T> { void done(T value, String error); }
    private interface Work<T> { T run(String uid) throws Exception; }
    private static final class Failure extends Exception {
        final String code;
        Failure(String code) { this.code = code; }
    }
    private final AppDatabase db;
    private final StudyDao dao;
    private final AuthRepository auth;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LiveData<List<SubjectEntity>> subjects;
    private final LiveData<List<TaskEntity>> tasks;
    private final LiveData<List<LessonEntity>> lessons;
    private final LiveData<List<NoteEntity>> notes;
    private final LiveData<ProfileEntity> profile;
    // Application-scoped repository: observer intentionally lives with the auth service.
    private final Observer<Session> profileInitializer;
    private CloudSync cloud;

    public StudyRepository(AppDatabase db, AuthRepository auth) {
        this.db = db; this.dao = db.studyDao(); this.auth = auth;
        subjects = switchOwnerList(dao::observeSubjects);
        tasks = switchOwnerList(dao::observeTasks);
        lessons = switchOwnerList(dao::observeLessons);
        notes = switchOwnerList(dao::observeNotes);
        MediatorLiveData<ProfileEntity> profileResult = new MediatorLiveData<>();
        final LiveData<?>[] previousProfile = {null};
        profileResult.addSource(auth.session(), user -> {
            if (previousProfile[0] != null) profileResult.removeSource(previousProfile[0]);
            profileResult.setValue(null);
            if (user == null) return;
            LiveData<ProfileEntity> source = dao.observeProfile(user.uid);
            previousProfile[0] = source;
            profileResult.addSource(source, value -> { if (auth.current() == user) profileResult.setValue(value); });
        });
        profile = profileResult;
        profileInitializer = user -> {
            if (user == null) return;
            worker.execute(() -> {
                if (auth.current() != user) return;
                // The authorized personal dataset belongs only to the first local demo identity.
                // Import first so the initial profile emission already has the requested group.
                if (auth.isDemo() && PersonalSchedule.OWNER_UID.equals(user.uid)) PersonalSchedule.importOnce(db, user.uid);
                ProfileEntity initial = new ProfileEntity();
                initial.ownerUid = user.uid;
                initial.displayName = user.displayName == null ? "" : user.displayName;
                dao.insertProfile(initial);
                if(dao.syncState(user.uid)==null) {
                    SyncStateEntity sync=new SyncStateEntity(); sync.ownerUid=user.uid;
                    sync.dirty=!dao.subjects(user.uid).isEmpty() || !dao.tasks(user.uid).isEmpty()
                            || !dao.lessons(user.uid).isEmpty() || !dao.notes(user.uid).isEmpty();
                    dao.saveSyncState(sync);
                }
            });
        };
        auth.session().observeForever(profileInitializer);
    }
    /** Clear synchronously on every auth event; switchMap alone can retain the old value. */
    private <T> LiveData<List<T>> switchOwnerList(Function<String,LiveData<List<T>>> sourceFactory) {
        MediatorLiveData<List<T>> result = new MediatorLiveData<>(Collections.emptyList());
        final LiveData<?>[] previous = {null};
        result.addSource(auth.session(), user -> {
            if (previous[0] != null) result.removeSource(previous[0]);
            result.setValue(Collections.emptyList());
            if (user == null) return;
            LiveData<List<T>> source = sourceFactory.apply(user.uid);
            previous[0] = source;
            result.addSource(source, values -> { if (auth.current() == user) result.setValue(values); });
        });
        return result;
    }
    public Session session() { return auth.current(); }
    public LiveData<List<SubjectEntity>> subjects() { return subjects; }
    public LiveData<List<TaskEntity>> tasks() { return tasks; }
    public LiveData<List<LessonEntity>> lessons() { return lessons; }
    public LiveData<List<NoteEntity>> notes() { return notes; }
    public LiveData<ProfileEntity> profile() { return profile; }
    public void loadProfile(Result<ProfileEntity> callback) { execute(false,uid->present(dao.profile(uid)).copy(),callback); }

    private <T> void execute(boolean transaction, Work<T> action, Result<T> callback) {
        final Session captured = auth.current();
        if (captured == null) { main.post(() -> callback.done(null, "session")); return; }
        worker.execute(() -> {
            T value = null; String error = null;
            try {
                if (auth.current() != captured) throw new Failure("session");
                if (transaction) {
                    // Room's Callable transaction preserves all-or-nothing behavior.
                    value = db.runInTransaction(() -> {
                        T changed=action.run(captured.uid);
                        SyncStateEntity sync=dao.syncState(captured.uid);
                        if(sync==null) { sync=new SyncStateEntity(); sync.ownerUid=captured.uid; }
                        sync.dirty=true; dao.saveSyncState(sync);
                        return changed;
                    });
                } else value = action.run(captured.uid);
            } catch (Exception failure) {
                Throwable cause = failure;
                while (cause.getCause() != null && !(cause instanceof Failure)) cause = cause.getCause();
                error = cause instanceof Failure ? ((Failure) cause).code :
                        failure instanceof SQLiteConstraintException ? "linked" : "unknown";
            }
            final T result = value; final String resultError = error;
            main.post(() -> {
                if (auth.current() != captured) callback.done(null, "session");
                else callback.done(result, resultError);
            });
        });
    }
    private static <T> T present(T value) throws Failure { if (value == null) throw new Failure("missing"); return value; }
    public void configureCloud(SupabaseHttp.Transport transport) { cloud=new CloudSync(db,auth,transport); }
    public void synchronize(CloudSync.Conflict choice,boolean keepLocal,Result<CloudSync.Conflict> callback) {
        final Session captured=auth.current();
        if(captured==null || cloud==null) { main.post(()->callback.done(null,"session")); return; }
        worker.execute(()->{
            CloudSync.Conflict result=null; String error=null;
            try { if(auth.current()!=captured) throw new Failure("session"); result=cloud.sync(captured,choice,keepLocal); }
            catch(Exception failure) { error="sync"; }
            CloudSync.Conflict conflict=result; String failure=error;
            main.post(()->callback.done(auth.current()==captured?conflict:null,auth.current()==captured?failure:"session"));
        });
    }
    private static void valid(boolean condition) throws Failure { if (!condition) throw new Failure("validation"); }
    private static void inputOwner(String input,String uid) throws Failure {
        if (input != null && !input.isEmpty() && !uid.equals(input)) throw new Failure("session");
    }
    private static String clean(String text) { return text == null ? "" : text; }
    private void subjectOwned(String uid,long id) throws Failure { present(dao.subject(uid,id)); }

    public void subject(long id,Result<SubjectEntity> callback) { execute(false, uid -> present(dao.subject(uid,id)),callback); }
    public void task(long id,Result<TaskEntity> callback) { execute(false, uid -> present(dao.task(uid,id)),callback); }
    public void lesson(long id,Result<LessonEntity> callback) { execute(false, uid -> present(dao.lesson(uid,id)),callback); }
    public void note(long id,Result<NoteEntity> callback) { execute(false, uid -> present(dao.note(uid,id)),callback); }

    public void saveSubject(SubjectEntity value,Result<Long> callback) {
        SubjectEntity e = value.copy();
        execute(true, uid -> {
            inputOwner(e.ownerUid,uid); valid(Validation.title(e.name));
            e.ownerUid=uid; e.name=e.name.trim(); e.teacher=clean(e.teacher).trim();
            valid(e.id >= 0 && e.icon >= 0 && e.icon <= 5);
            if (e.id == 0) return dao.insertSubject(e);
            present(dao.subject(uid,e.id));
            dao.updateSubject(uid,e.id,e.name,e.teacher,e.color,e.icon);
            return e.id;
        },callback);
    }
    public void saveTask(TaskEntity value,Result<Long> callback) {
        TaskEntity e = value.copy();
        execute(true, uid -> {
            inputOwner(e.ownerUid,uid);
            valid(e.id >= 0 && Validation.task(e.title,e.subjectId,e.kind,e.dueAt,e.priority));
            subjectOwned(uid,e.subjectId);
            e.ownerUid=uid; e.title=e.title.trim(); e.description=clean(e.description); e.updatedAt=System.currentTimeMillis();
            if (e.id == 0) { e.createdAt=e.updatedAt; return dao.insertTask(e); }
            present(dao.task(uid,e.id));
            dao.updateTask(uid,e.id,e.subjectId,e.title,e.description,e.kind,e.dueAt,e.priority,e.completed,e.updatedAt);
            return e.id;
        },callback);
    }
    public void saveLesson(LessonEntity value,Result<Long> callback) {
        LessonEntity e = value.copy();
        execute(true, uid -> {
            inputOwner(e.ownerUid,uid);
            valid(e.id >= 0 && Validation.lesson(e.dayOfWeek,e.startMinute,e.endMinute));
            valid(e.lessonNumber >= 0 && e.lessonNumber <= 99 && clean(e.lessonType).length() <= 100
                    && clean(e.teacher).length() <= 300 && Validation.webUrl(clean(e.meetingUrl).trim()));
            subjectOwned(uid,e.subjectId);
            e.ownerUid=uid; e.room=clean(e.room).trim();
            e.lessonType=clean(e.lessonType).trim();e.teacher=clean(e.teacher).trim();e.meetingUrl=clean(e.meetingUrl).trim();
            if (e.id == 0) return dao.insertLesson(e);
            present(dao.lesson(uid,e.id));
            dao.updateLesson(uid,e.id,e.subjectId,e.dayOfWeek,e.startMinute,e.endMinute,e.room,
                    e.lessonNumber,e.lessonType,e.teacher,e.meetingUrl);
            return e.id;
        },callback);
    }
    public void saveNote(NoteEntity value,Result<Long> callback) {
        NoteEntity e = value.copy();
        execute(true, uid -> {
            inputOwner(e.ownerUid,uid); valid(e.id >= 0 && Validation.title(e.title));
            if (e.subjectId != null) subjectOwned(uid,e.subjectId);
            e.ownerUid=uid; e.title=e.title.trim(); e.content=clean(e.content); e.updatedAt=System.currentTimeMillis();
            if (e.id == 0) { e.createdAt=e.updatedAt; return dao.insertNote(e); }
            present(dao.note(uid,e.id));
            dao.updateNote(uid,e.id,e.subjectId,e.title,e.content,e.updatedAt);
            return e.id;
        },callback);
    }
    public void deleteSubject(long id,Result<Boolean> callback) {
        execute(true,uid -> {
            present(dao.subject(uid,id));
            if (dao.subjectLinks(uid,id) > 0) throw new Failure("linked");
            return dao.deleteSubject(uid,id) == 1;
        },callback);
    }
    public void deleteTask(long id,Result<TaskEntity> callback) {
        execute(true,uid -> { TaskEntity old=present(dao.task(uid,id)); dao.deleteTask(uid,id); return old; },callback);
    }
    public void restoreTask(TaskEntity value,Result<Long> callback) {
        TaskEntity e=value.copy();
        execute(true,uid -> {
            // Only deleted snapshots with a concrete trusted owner are accepted.
            if (!uid.equals(e.ownerUid)) throw new Failure("session");
            valid(e.id > 0 && Validation.task(e.title,e.subjectId,e.kind,e.dueAt,e.priority));
            subjectOwned(uid,e.subjectId);
            // A repeated Undo is harmless, and does not overwrite a newer record.
            if (dao.task(uid,e.id) != null) return e.id;
            dao.insertTask(e);
            return e.id;
        },callback);
    }
    public void deleteLesson(long id,Result<Boolean> callback) {
        execute(true,uid -> { present(dao.lesson(uid,id)); return dao.deleteLesson(uid,id)==1; },callback);
    }
    public void deleteNote(long id,Result<Boolean> callback) {
        execute(true,uid -> { present(dao.note(uid,id)); return dao.deleteNote(uid,id)==1; },callback);
    }
    public void completeTask(long id,boolean completed,Result<Boolean> callback) {
        execute(true,uid -> { present(dao.task(uid,id)); return dao.completeTask(uid,id,completed,System.currentTimeMillis())==1; },callback);
    }
    public void saveProfile(String name,String group,Result<Boolean> callback) {
        execute(true,uid -> {
            valid(Validation.title(name) && clean(group).length() <= 100);
            ProfileEntity p=new ProfileEntity(); p.ownerUid=uid; p.displayName=name.trim(); p.studyGroup=clean(group).trim();
            dao.insertProfile(p); dao.updateProfile(uid,p.displayName,p.studyGroup); return true;
        },callback);
    }
    public void seedDemo(Result<Boolean> callback) {
        execute(true,uid -> {
            valid(auth.isDemo());
            if (dao.seeded(uid)>0) return false;
            SubjectEntity mobile = new SubjectEntity(); mobile.ownerUid=uid; mobile.name="Үлгі · Мобильді қосымшалар"; mobile.teacher="А. Сәрсенова"; mobile.color=0xff3d5c52; mobile.icon=0;
            long mobileId=dao.insertSubject(mobile);
            SubjectEntity database = new SubjectEntity(); database.ownerUid=uid; database.name="Үлгі · Деректер қоры"; database.teacher="Е. Омаров"; database.color=0xff9c6b33; database.icon=1;
            long databaseId=dao.insertSubject(database);
            long now=System.currentTimeMillis();
            Calendar due=Calendar.getInstance(); due.add(Calendar.DAY_OF_YEAR,1); due.set(Calendar.HOUR_OF_DAY,18); due.set(Calendar.MINUTE,0); due.set(Calendar.SECOND,0); due.set(Calendar.MILLISECOND,0);
            TaskEntity task = new TaskEntity(); task.ownerUid=uid; task.subjectId=mobileId; task.title="Үлгі · Fragment өмірлік циклін түсіндіру"; task.description="Дәріс материалын қайталап, шағын мысал дайындау."; task.dueAt=due.getTimeInMillis(); task.priority=2; task.createdAt=now;task.updatedAt=now;dao.insertTask(task);
            TaskEntity exam = new TaskEntity(); exam.ownerUid=uid; exam.subjectId=databaseId; exam.title="Үлгі · Деректер қоры бойынша емтихан"; exam.description="Кестелер, байланыстар және транзакциялар."; exam.kind=1; due.add(Calendar.DAY_OF_YEAR,3); exam.dueAt=due.getTimeInMillis(); exam.createdAt=now;exam.updatedAt=now;dao.insertTask(exam);
            TaskEntity completed=new TaskEntity();completed.ownerUid=uid;completed.subjectId=mobileId;completed.title="Үлгі · XML макетін дайындау";completed.dueAt=now-86400000L;completed.completed=true;completed.createdAt=now;completed.updatedAt=now;dao.insertTask(completed);
            TaskEntity late=new TaskEntity();late.ownerUid=uid;late.subjectId=databaseId;late.title="Үлгі · SQL сұрауларын қайталау";late.dueAt=now-3600000L;late.priority=0;late.createdAt=now;late.updatedAt=now;dao.insertTask(late);
            int weekday=(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7+1;
            LessonEntity lesson=new LessonEntity();lesson.ownerUid=uid;lesson.subjectId=mobileId;lesson.dayOfWeek=weekday;lesson.startMinute=540;lesson.endMinute=590;lesson.room="Үлгі · 305";dao.insertLesson(lesson);
            LessonEntity next=new LessonEntity();next.ownerUid=uid;next.subjectId=databaseId;next.dayOfWeek=weekday%7+1;next.startMinute=630;next.endMinute=680;next.room="Үлгі · 212";dao.insertLesson(next);
            NoteEntity note=new NoteEntity();note.ownerUid=uid;note.subjectId=mobileId;note.title="Үлгі · Қорғауға дайындық";note.content="1. Қосымшаның мақсатын түсіндіру.\n2. Тапсырма қосып, орындалуын белгілеу.\n3. Қазақ әріптерін тексеру: Ә Ғ Қ Ң Ө Ұ Ү Һ І.\nБұл — демонстрациялық мысал.";note.createdAt=now;note.updatedAt=now;dao.insertNote(note);
            SeedStateEntity marker=new SeedStateEntity();marker.ownerUid=uid;marker.seededAt=now;dao.insertSeed(marker);
            return true;
        },callback);
    }
}
