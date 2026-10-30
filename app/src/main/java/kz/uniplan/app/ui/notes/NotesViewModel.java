package kz.uniplan.app.ui.notes;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.entity.NoteEntity;

/** List preferences and query survive view recreation; the repository owns account switching. */
public final class NotesViewModel extends AndroidViewModel {
    private final SavedStateHandle state;
    private final UniPlanApp app;
    private final String ownerUid;
    private final AuthRepository.Session ownerSession;
    private List<NoteEntity> source;
    public final MediatorLiveData<List<NoteEntity>> notes = new MediatorLiveData<>();

    public NotesViewModel(@NonNull Application application, SavedStateHandle state) {
        super(application);
        this.state = state;
        app = UniPlanApp.get(application);
        AuthRepository.Session session = app.auth().current();
        ownerSession = session;
        ownerUid = session == null ? "" : session.uid;
        String savedOwner = state.get("notesOwner");
        if (savedOwner != null && !savedOwner.equals(ownerUid)) state.set("notesQuery", "");
        state.set("notesOwner", ownerUid);
        notes.addSource(app.data().notes(), list -> { source = list; filter(); });
        notes.addSource(state.<String>getLiveData("notesQuery", ""), ignored -> filter());
    }

    private void filter() {
        if (source == null) return;
        String query = query().trim().toLowerCase(Locale.ROOT);
        List<NoteEntity> result = new ArrayList<>();
        for (NoteEntity note : source) {
            if (ownerUid.equals(note.ownerUid) && note.title.toLowerCase(Locale.ROOT).contains(query)) result.add(note);
        }
        result.sort(Comparator.comparingLong((NoteEntity n) -> n.updatedAt).reversed().thenComparingLong(n -> n.id));
        notes.setValue(result);
    }

    public String query() { String value = state.get("notesQuery"); return value == null ? "" : value; }
    public void setQuery(String query) { state.set("notesQuery", query); }
    public boolean grid() { return app.prefs().noteGrid(ownerUid); }
    public void toggleGrid() {
        if (app.auth().current() == ownerSession && ownerSession != null) app.prefs().setNoteGrid(ownerUid, !grid());
    }
}
