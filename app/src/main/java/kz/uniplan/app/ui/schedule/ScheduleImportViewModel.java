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
    public void analyze(){analyze(null);}
    public void analyze(String plainFallback){
        if(Boolean.TRUE.equals(busy.getValue())||!authorized())return;
        final int request=++generation;final String input=source();busy.setValue(true);
        worker.execute(()->{
            ScheduleImport.Preview parsed=ScheduleParser.parse(input);
            String accepted=input;
            boolean unrecognized=parsed.entries.isEmpty()&&parsed.issues.size()==1
                    &&("header".equals(parsed.issues.get(0).code)||"empty".equals(parsed.issues.get(0).code));
            if(unrecognized&&plainFallback!=null&&!plainFallback.equals(input)){
                ScheduleImport.Preview fallback=ScheduleParser.parse(plainFallback);
                if(fallback.canImport()){accepted=plainFallback;parsed=fallback;}
            }
            final ScheduleImport.Preview result=parsed;final String acceptedSource=accepted;
            main.post(()->{
                // Busy gates all other work; a generation change here can only be an edit/clear.
                busy.setValue(false);
                if(request!=generation||!authorized())return;
                if(!input.equals(acceptedSource))state.set("source",acceptedSource);
                preview.setValue(result);
            });
        });
    }
    public void download(String url){
        if(Boolean.TRUE.equals(busy.getValue())||!authorized())return;
        state.set("url",url);final int request=++generation;busy.setValue(true);preview.setValue(null);
        worker.execute(()->{
            try {
                String source=new ScheduleWebSource().load(url);ScheduleImport.Preview parsed=ScheduleParser.parse(source);
                main.post(()->{busy.setValue(false);if(request!=generation||!authorized())return;state.set("source",source);preview.setValue(parsed);});
            } catch(Exception failure){
                String code=failure instanceof ScheduleWebSource.SourceException?((ScheduleWebSource.SourceException)failure).code:"network";
                main.post(()->{busy.setValue(false);if(request==generation&&authorized())errors.setValue(new Event<>(code));});
            }
        });
    }
    public void save(){
        ScheduleImport.Preview current=preview.getValue();
        if(Boolean.TRUE.equals(busy.getValue())||!authorized()||current==null||!current.canImport())return;
        busy.setValue(true);
        repository.importSchedule(owner,current.entries,(result,error)->{busy.setValue(false);if(error==null)saved.setValue(new Event<>(result));else errors.setValue(new Event<>("linked".equals(error)||"unknown".equals(error)?"save":error));});
    }
    private boolean authorized(){if(owner!=null&&repository.session()==owner)return true;errors.setValue(new Event<>("session"));return false;}
    @Override protected void onCleared(){generation++;worker.shutdownNow();super.onCleared();}
}
