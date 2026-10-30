package kz.uniplan.app.ui.tasks;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.List;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.data.repository.StudyRepository;

/** Screen-scoped state. The repository captures the authenticated owner for each operation. */
public class StudyViewModel extends AndroidViewModel {
    public final SavedStateHandle state;
    private final StudyRepository repository;
    private final String ownerUid;
    private final AuthRepository.Session ownerSession;
    public final LiveData<List<TaskEntity>> tasks;
    public final LiveData<List<SubjectEntity>> subjects;
    public final LiveData<List<LessonEntity>> lessons;
    public final LiveData<ProfileEntity> profile;
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    public final MutableLiveData<Event<String>> errors = new MutableLiveData<>();
    public final MutableLiveData<Event<Long>> saved = new MutableLiveData<>();
    public final MutableLiveData<Event<Boolean>> removed = new MutableLiveData<>();
    public final MutableLiveData<Event<TaskEntity>> deletedTask = new MutableLiveData<>();
    public final MutableLiveData<Integer> revision = new MutableLiveData<>(0);
    public final MutableLiveData<TaskEntity> detail = new MutableLiveData<>();

    public StudyViewModel(@NonNull Application application, SavedStateHandle handle) {
        super(application);
        state = handle;
        repository = UniPlanApp.get(application).data();
        ownerSession = repository.session();
        String restoredOwner = state.get("screenOwner");
        ownerUid = restoredOwner != null ? restoredOwner : ownerSession == null ? null : ownerSession.uid;
        state.set("screenOwner", ownerUid);
        if (ownerSession == null || !ownerSession.uid.equals(ownerUid)) {
            // Saved form content belongs only to the account that created it.
            for (String key : new String[]{"title", "description", "name", "teacher", "room", "search", "filterName", "lessonType", "lessonTeacher", "meetingUrl", "lessonNumber"}) state.set(key, "");
            state.set("subject", 0L); state.set("filter", 0L); state.set("initialized", false);
        }
        tasks = repository.tasks();
        subjects = repository.subjects();
        lessons = repository.lessons();
        profile = repository.profile();
    }
    public static final class Event<T> {
        private final T value;
        private boolean handled;
        public Event(T value) { this.value = value; }
        public T take() { if (handled) return null; handled = true; return value; }
    }
    public String text(String key) { String value = state.get(key); return value == null ? "" : value; }
    public long number(String key, long fallback) { Number value = state.get(key); return value == null ? fallback : value.longValue(); }
    public boolean flag(String key) { return Boolean.TRUE.equals(state.get(key)); }
    public void set(String key, Object value) { state.set(key, value); }
    public void notifyState() { revision.setValue(revision.getValue() + 1); }
    public boolean isBusy() { return Boolean.TRUE.equals(busy.getValue()); }
    private void failure(String code) { if (code != null) errors.setValue(new Event<>(code)); }
    private boolean authorized() {
        boolean allowed = ownerUid != null && ownerSession != null && repository.session() == ownerSession && ownerUid.equals(ownerSession.uid);
        if (!allowed) failure("session");
        return allowed;
    }
    private void finish(Long id, String error) { busy.setValue(false); failure(error); if (error == null) saved.setValue(new Event<>(id)); }

    public void loadTask(long id) {
        if (!authorized()) return;
        if (flag("initialized") || isBusy()) return;
        if (id == 0) {
            set("id", 0L); set("due", System.currentTimeMillis() + 86400000L);
            set("priority", 1); set("initialized", true); notifyState(); return;
        }
        busy.setValue(true);
        repository.task(id, (t, error) -> {
            busy.setValue(false); failure(error); if (t == null) return;
            set("id", t.id); set("title", t.title); set("description", t.description);
            set("subject", t.subjectId); set("kind", t.kind); set("due", t.dueAt);
            set("priority", t.priority); set("completed", t.completed);
            set("created", t.createdAt); set("initialized", true); notifyState();
        });
    }
    public void loadDetail(long id) {
        if (!authorized()) return;
        busy.setValue(true);
        repository.task(id, (t, error) -> { busy.setValue(false); failure(error); detail.setValue(t); });
    }
    public void saveTask() {
        if (!authorized() || isBusy() || !flag("initialized")) return;
        TaskEntity t = new TaskEntity();
        t.id = number("id", 0); t.title = text("title").trim(); t.description = text("description");
        t.subjectId = number("subject", 0); t.kind = (int) number("kind", 0);
        t.dueAt = number("due", 0); t.priority = (int) number("priority", 1);
        t.completed = flag("completed"); t.createdAt = number("created", System.currentTimeMillis());
        busy.setValue(true); repository.saveTask(t, this::finish);
    }
    public void complete(long id, boolean complete) {
        if (!authorized()) return;
        repository.completeTask(id, complete, (value, error) -> failure(error));
    }
    public void deleteTask(long id) {
        if (!authorized()) return;
        repository.deleteTask(id, (t, error) -> { failure(error); if (t != null) deletedTask.setValue(new Event<>(t)); });
    }
    public void restore(TaskEntity t) {
        if (!authorized()) return;
        repository.restoreTask(t, (id, error) -> failure(error));
    }
    public void loadSubject(long id) {
        if (!authorized()) return;
        if (flag("initialized") || isBusy()) return;
        if (id == 0) { set("id", 0L); set("initialized", true); notifyState(); return; }
        busy.setValue(true);
        repository.subject(id, (s, error) -> {
            busy.setValue(false); failure(error); if (s == null) return;
            set("id", s.id); set("name", s.name); set("teacher", s.teacher);
            set("colorValue", s.color); set("subjectColorHex", kz.uniplan.app.util.AppearancePalette.hex(s.color)); set("icon", s.icon); set("initialized", true); notifyState();
        });
    }
    public void saveSubject(int color) {
        if (!authorized() || isBusy() || !flag("initialized")) return;
        SubjectEntity s = new SubjectEntity();
        s.id = number("id", 0); s.name = text("name").trim(); s.teacher = text("teacher").trim();
        s.color = color; s.icon = (int) number("icon", 0);
        busy.setValue(true); repository.saveSubject(s, this::finish);
    }
    public void deleteSubject(long id) {
        if (!authorized() || isBusy()) return;
        busy.setValue(true);
        repository.deleteSubject(id, (ok,error) -> { busy.setValue(false); failure(error); if (error == null) removed.setValue(new Event<>(true)); });
    }
    public void loadLesson(long id) {
        if (!authorized()) return;
        if (flag("initialized") || isBusy()) return;
        if (id == 0) {
            set("id", 0L); set("start", 540); set("end", 590);
            if (!state.contains("day")) set("day", CoreText.today());
            set("initialized", true); notifyState(); return;
        }
        busy.setValue(true);
        repository.lesson(id, (l,error) -> {
            busy.setValue(false); failure(error); if (l == null) return;
            set("id", l.id); set("subject", l.subjectId); set("day", l.dayOfWeek);
            set("start", l.startMinute); set("end", l.endMinute); set("room", l.room);
            set("lessonNumber", l.lessonNumber == 0 ? "" : Integer.toString(l.lessonNumber));
            set("lessonType", l.lessonType); set("lessonTeacher", l.teacher); set("meetingUrl", l.meetingUrl);
            set("initialized", true); notifyState();
        });
    }
    public void saveLesson() {
        if (!authorized() || isBusy() || !flag("initialized")) return;
        LessonEntity l = new LessonEntity();
        l.id = number("id", 0); l.subjectId = number("subject", 0);
        l.dayOfWeek = (int) number("day", 1); l.startMinute = (int) number("start", 540);
        l.endMinute = (int) number("end", 590); l.room = text("room").trim();
        String lessonNumber = text("lessonNumber").trim();
        try { l.lessonNumber = lessonNumber.isEmpty() ? 0 : Integer.parseInt(lessonNumber); }
        catch (NumberFormatException invalid) { failure("validation"); return; }
        l.lessonType = text("lessonType").trim(); l.teacher = text("lessonTeacher").trim(); l.meetingUrl = text("meetingUrl").trim();
        busy.setValue(true); repository.saveLesson(l, this::finish);
    }
    public void deleteLesson(long id) {
        if (!authorized() || isBusy()) return;
        busy.setValue(true);
        repository.deleteLesson(id, (ok,error) -> { busy.setValue(false); failure(error); if (error == null) removed.setValue(new Event<>(true)); });
    }
}
