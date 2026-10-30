package kz.uniplan.app.ui.notes;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.List;
import java.util.Objects;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.entity.NoteEntity;
import kz.uniplan.app.data.local.entity.SubjectEntity;

/** Shared by the editor and preview through their parent fragment's ViewModelStore. */
public final class NoteEditViewModel extends AndroidViewModel {
    public static final class Event {
        public final String action;
        public final String error;
        private boolean consumed;
        Event(String action, String error) { this.action = action; this.error = error; }
        public boolean consume() { if (consumed) return false; consumed = true; return true; }
    }
    private final UniPlanApp app;
    private final SavedStateHandle state;
    private final String ownerUid;
    private final AuthRepository.Session ownerSession;
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> available = new MutableLiveData<>(false);
    public final MutableLiveData<Event> events = new MutableLiveData<>();
    public final LiveData<String> title;
    public final LiveData<String> content;
    public final LiveData<Boolean> dirty;
    public final LiveData<Long> subjectId;
    public final LiveData<Long> updatedAt;
    public final LiveData<List<SubjectEntity>> subjects;

    public NoteEditViewModel(@NonNull Application application, SavedStateHandle state) {
        super(application);
        app = UniPlanApp.get(application);
        this.state = state;
        AuthRepository.Session session = app.auth().current();
        ownerSession = session;
        String captured = state.get("noteOwner");
        ownerUid = captured == null ? (session == null ? "" : session.uid) : captured;
        state.set("noteOwner", ownerUid);
        // A restored form belonging to a different account must not display its draft.
        if (session == null || !ownerUid.equals(session.uid)) {
            state.set("noteTitle", ""); state.set("noteContent", ""); state.set("noteSubject", 0L);
            state.set("noteCreated", 0L); state.set("noteUpdated", 0L); state.set("noteDirty", false);
            state.remove("pendingExport");
        }
        title = state.getLiveData("noteTitle", "");
        content = state.getLiveData("noteContent", "");
        dirty = state.getLiveData("noteDirty", false);
        subjectId = state.getLiveData("noteSubject", 0L);
        updatedAt = state.getLiveData("noteUpdated", 0L);
        subjects = app.data().subjects();
        if (!validSession()) { events.setValue(new Event("error", "session")); return; }
        if (id() == 0 || Boolean.TRUE.equals(state.get("noteLoaded"))) available.setValue(true);
        else load();
    }

    private boolean validSession() {
        AuthRepository.Session session = app.auth().current();
        return session != null && session == ownerSession && !ownerUid.isEmpty() && ownerUid.equals(session.uid);
    }
    private boolean start() {
        if (!validSession()) { emit("error", "session"); return false; }
        if (Boolean.TRUE.equals(busy.getValue())) return false;
        busy.setValue(true);
        return true;
    }
    private void load() {
        if (!start()) return;
        app.data().note(id(), (note, error) -> {
            busy.setValue(false);
            if (!validSession()) return;
            if (error != null || note == null) { emit("error", error == null ? "missing" : error); return; }
            state.set("noteTitle", note.title);
            state.set("noteContent", note.content);
            state.set("noteSubject", note.subjectId == null ? 0L : note.subjectId);
            state.set("noteCreated", note.createdAt);
            state.set("noteUpdated", note.updatedAt);
            state.set("noteLoaded", true);
            state.set("noteDirty", false);
            available.setValue(true);
        });
    }
    public long id() { Long value = state.get("id"); return value == null ? 0L : value; }
    public long createdAt() { Long value = state.get("noteCreated"); return value == null ? 0L : value; }
    public String titleText() { String value = title.getValue(); return value == null ? "" : value; }
    public String bodyText() { String value = content.getValue(); return value == null ? "" : value; }
    public boolean isDirty() { return Boolean.TRUE.equals(dirty.getValue()); }
    public void setTitle(String text) { if (!Objects.equals(text, title.getValue())) { state.set("noteTitle", text); state.set("noteDirty", true); } }
    public void setContent(String text) { if (!Objects.equals(text, content.getValue())) { state.set("noteContent", text); state.set("noteDirty", true); } }
    public void setSubject(long id) { if (!Objects.equals(id, subjectId.getValue())) { state.set("noteSubject", id); state.set("noteDirty", true); } }

    public void save() {
        if (!Boolean.TRUE.equals(available.getValue()) || !start()) return;
        if (titleText().trim().isEmpty()) { busy.setValue(false); emit("error", "validation"); return; }
        NoteEntity note = new NoteEntity();
        note.id = id(); note.ownerUid = ownerUid; note.title = titleText().trim(); note.content = bodyText();
        Long subject = subjectId.getValue(); note.subjectId = subject == null || subject == 0L ? null : subject;
        long now = System.currentTimeMillis();
        note.createdAt = createdAt() == 0 ? now : createdAt(); note.updatedAt = now;
        String savedTitle = titleText(); String savedBody = bodyText();
        app.data().saveNote(note, (id, error) -> {
            busy.setValue(false);
            if (!validSession()) return;
            if (error != null) { emit("error", error); return; }
            state.set("id", id); state.set("noteCreated", note.createdAt); state.set("noteUpdated", now); state.set("noteLoaded", true);
            boolean unchanged = savedTitle.equals(titleText()) && savedBody.equals(bodyText()) && Objects.equals(subject, subjectId.getValue());
            state.set("noteDirty", !unchanged);
            emit("saved", null);
        });
    }
    public void delete() {
        if (id() == 0 || !start()) return;
        app.data().deleteNote(id(), (ignored, error) -> {
            busy.setValue(false);
            if (!validSession()) return;
            if (error == null) { state.set("noteDirty", false); available.setValue(false); }
            emit(error == null ? "deleted" : "error", error);
        });
    }
    public void saveCopy(boolean external) {
        if (id() == 0) { emit("save_first", null); return; }
        if (!start()) return;
        app.files().save(id(), bodyText(), external, (ignored, error) -> { busy.setValue(false); if (validSession()) emit(error == null ? "copied" : "error", error); });
    }
    public void readCopy(boolean external) {
        if (id() == 0) { emit("save_first", null); return; }
        if (!start()) return;
        app.files().read(id(), external, (text, error) -> {
            busy.setValue(false);
            if (!validSession()) return;
            if (error == null && text.length() > 20000) { emit("error", "validation"); return; }
            if (error == null) { setContent(text); emit("opened", null); }
            else emit("error", error);
        });
    }
    public boolean prepareExport() {
        if (!validSession() || !Boolean.TRUE.equals(available.getValue()) || Boolean.TRUE.equals(busy.getValue())) return false;
        state.set("pendingExport", titleText() + "\n\n" + bodyText());
        return true;
    }
    public void exportTo(Uri uri) {
        String text = state.get("pendingExport");
        state.remove("pendingExport");
        if (uri == null) { emit("cancelled", null); return; }
        if (text == null || !start()) return;
        app.files().export(uri, text, ownerUid, (ignored, error) -> { busy.setValue(false); if (validSession()) emit(error == null ? "exported" : "error", error); });
    }
    private void emit(String action, String error) { events.setValue(new Event(action, error)); }
}
