package kz.uniplan.app.ui.schedule;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.data.schedule.ScheduleWebSource;
import kz.uniplan.app.domain.schedule.ScheduleImport;
import kz.uniplan.app.domain.schedule.ScheduleParser;
import kz.uniplan.app.ui.tasks.StudyViewModel.Event;

public final class ScheduleImportViewModel extends AndroidViewModel {
    private final SavedStateHandle state;
    private final StudyRepository repository;
    private final AuthRepository.Session owner;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private int generation;
    public final MutableLiveData<ScheduleImport.Preview> preview=new MutableLiveData<>();
    public final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);
    public final MutableLiveData<Event<String>> errors=new MutableLiveData<>();
    public final MutableLiveData<Event<ScheduleImport.Outcome>> saved=new MutableLiveData<>();
    public ScheduleImportViewModel(@NonNull Application app,SavedStateHandle state){
        super(app);this.state=state;repository=UniPlanApp.get(app).data();owner=repository.session();
        String restored=state.get("owner");
        if(owner==null||restored!=null&&!restored.equals(owner.uid)){state.set("source","");state.set("url","");}
        state.set("owner",owner==null?null:owner.uid);
    }
    public String source(){String value=state.get("source");return value==null?"":value;}
    public String url(){String value=state.get("url");return value==null?"":value;}
    public androidx.lifecycle.LiveData<String> sourceChanges(){return state.getLiveData("source","");}
    public void edit(String text){if(!text.equals(source())){state.set("source",text);generation++;preview.setValue(null);}}
    public void analyze(){
        if(Boolean.TRUE.equals(busy.getValue())||!authorized())return;
        final int request=++generation;final String input=source();busy.setValue(true);
        worker.execute(()->{ScheduleImport.Preview parsed=ScheduleParser.parse(input);main.post(()->{if(request!=generation)return;busy.setValue(false);if(authorized())preview.setValue(parsed);});});
    }
    public void download(String url){
        if(Boolean.TRUE.equals(busy.getValue())||!authorized())return;
        state.set("url",url);final int request=++generation;busy.setValue(true);preview.setValue(null);
        worker.execute(()->{
            try {
                String source=new ScheduleWebSource().load(url);ScheduleImport.Preview parsed=ScheduleParser.parse(source);
                main.post(()->{if(request!=generation)return;busy.setValue(false);if(!authorized())return;state.set("source",source);preview.setValue(parsed);});
            } catch(Exception failure){
                String code=failure instanceof ScheduleWebSource.SourceException?((ScheduleWebSource.SourceException)failure).code:"network";
                main.post(()->{if(request==generation){busy.setValue(false);if(authorized())errors.setValue(new Event<>(code));}});
            }
        });
    }
    public void save(){
        ScheduleImport.Preview current=preview.getValue();
        if(Boolean.TRUE.equals(busy.getValue())||!authorized()||current==null||!current.canImport())return;
        busy.setValue(true);
        repository.importSchedule(owner,current.entries,(result,error)->{busy.setValue(false);if(error==null)saved.setValue(new Event<>(result));else errors.setValue(new Event<>(error));});
    }
    private boolean authorized(){if(owner!=null&&repository.session()==owner)return true;errors.setValue(new Event<>("session"));return false;}
    @Override protected void onCleared(){generation++;worker.shutdownNow();super.onCleared();}
}
