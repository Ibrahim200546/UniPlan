package kz.uniplan.app.ui.profile;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.local.entity.ProfileEntity;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.data.auth.AuthRepository;
public final class ProfileViewModel extends AndroidViewModel {
    private final StudyRepository repository;
    public final SavedStateHandle state;
    private final AuthRepository.Session session;
    private final String owner;
    public final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);
    public final MutableLiveData<String> result=new MutableLiveData<>();
    public final MutableLiveData<Boolean> syncing=new MutableLiveData<>(false);
    public final MutableLiveData<Integer> syncNotice=new MutableLiveData<>();
    public final MutableLiveData<ProfileEntity> synchronizedProfile=new MutableLiveData<>();
    public final MutableLiveData<kz.uniplan.app.data.sync.CloudSync.Conflict> conflict=new MutableLiveData<>();
    public ProfileViewModel(Application application,SavedStateHandle state) {
        super(application); this.state=state; repository=((UniPlanApp)application).data(); session=repository.session();
        String restoredOwner=state.get("profileOwner"); owner=restoredOwner==null?(session==null?null:session.uid):restoredOwner;
        state.set("profileOwner",owner);
        if(!authorized()) { state.remove("name"); state.remove("group"); state.remove("initialized"); }
    }
    public boolean authorized() { return session!=null && session==repository.session() && session.uid.equals(owner); }
    public void sync(boolean resolve,boolean keepLocal) {
        if(!authorized() || Boolean.TRUE.equals(syncing.getValue())) return;
        kz.uniplan.app.data.sync.CloudSync.Conflict choice=resolve?conflict.getValue():null;
        if(resolve && choice==null) return;
        syncing.setValue(true); conflict.setValue(null); syncNotice.setValue(kz.uniplan.app.R.string.sync_busy);
        repository.synchronize(choice,keepLocal,(value,error)->{
            syncing.setValue(false);
            syncNotice.setValue(error!=null?kz.uniplan.app.R.string.sync_error:value!=null?kz.uniplan.app.R.string.sync_conflict:kz.uniplan.app.R.string.sync_done);
            conflict.setValue(value);
            // Remote restores can update the profile form on the next visit.
            if(error==null && value==null) repository.loadProfile((profile,problem)->{
                if(problem==null && authorized()) {
                    state.set("initialized",true); state.set("name",profile.displayName); state.set("group",profile.studyGroup);
                    synchronizedProfile.setValue(profile);
                }
            });
        });
    }
    public LiveData<ProfileEntity> profile() { return repository.profile(); }
    public void save(String name,String group) { if(!authorized()) { result.setValue("session"); return; } if(Boolean.TRUE.equals(busy.getValue())) return; busy.setValue(true); repository.saveProfile(name,group,(ok,error)->{ busy.setValue(false); result.setValue(error==null?"saved":error); }); }
    public void seed() { if(!authorized()) { result.setValue("session"); return; } if(Boolean.TRUE.equals(busy.getValue())) return; busy.setValue(true); repository.seedDemo((ok,error)->{ busy.setValue(false); result.setValue(error==null?(Boolean.TRUE.equals(ok)?"seeded":"seeded_already"):error); }); }
}
