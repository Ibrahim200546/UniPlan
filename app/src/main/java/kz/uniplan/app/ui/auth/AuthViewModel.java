package kz.uniplan.app.ui.auth;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.util.Validation;

public final class AuthViewModel extends AndroidViewModel {
    private final AuthRepository auth;
    public final SavedStateHandle state;
    public final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);
    public final MutableLiveData<Integer> notice=new MutableLiveData<>();
    public AuthViewModel(Application app,SavedStateHandle state) { super(app); this.state=state; auth=((UniPlanApp)app).auth(); }
    public boolean registering() { return Boolean.TRUE.equals(state.get("register")); }
    public void toggle() { state.set("register",!registering()); }
    public void submit(String name,String email,String password,String repeat) {
        if(Boolean.TRUE.equals(busy.getValue())) return;
        if(!Validation.login(email,password) || (registering() && (name.trim().isEmpty() || !password.equals(repeat)))) { notice.setValue(R.string.auth_validation); return; }
        if(registering() && password.length()<6) { notice.setValue(R.string.auth_password_policy); return; }
        busy.setValue(true);
        AuthRepository.Callback callback=error->{ busy.setValue(false); if(error!=null) notice.setValue(error); };
        if(registering()) auth.register(name.trim(),email.trim(),password,callback); else auth.signIn(email.trim(),password,callback);
    }
    public void reset(String email) {
        if(Boolean.TRUE.equals(busy.getValue())) return;
        if(!Validation.email(email)) { notice.setValue(R.string.auth_validation); return; }
        busy.setValue(true); auth.resetPassword(email.trim(),error->{ busy.setValue(false); notice.setValue(error==null?R.string.auth_reset_neutral:error); });
    }
    public void demo(int index) { auth.openDemo(index); }
}
