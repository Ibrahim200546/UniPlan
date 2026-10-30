package kz.uniplan.app;
import android.app.Application;
import android.content.Context;
import kz.uniplan.app.data.auth.AuthProvider;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.data.files.NoteFiles;
import kz.uniplan.app.util.SettingsStore;
public final class UniPlanApp extends Application {
    private AuthRepository auth;
    private StudyRepository data;
    private NoteFiles files;
    private SettingsStore preferences;
    @Override public void onCreate() {
        super.onCreate();
        preferences=new SettingsStore(this); preferences.apply();
        auth=AuthProvider.create(this);
        data=new StudyRepository(AppDatabase.get(this),auth);
        files=new NoteFiles(this,auth);
    }
    public static UniPlanApp get(Context context) { return (UniPlanApp)context.getApplicationContext(); }
    public AuthRepository auth() { return auth; }
    public StudyRepository data() { return data; }
    public NoteFiles files() { return files; }
    public SettingsStore prefs() { return preferences; }
}
