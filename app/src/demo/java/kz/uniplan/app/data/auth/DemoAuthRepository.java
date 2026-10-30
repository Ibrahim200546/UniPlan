package kz.uniplan.app.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import kz.uniplan.app.R;

/** Local identities only. Never accepts or pretends to validate credentials. */
public final class DemoAuthRepository implements AuthRepository {
    private final SharedPreferences preferences;
    private final MutableLiveData<Session> state = new MutableLiveData<>(null);
    private volatile Session current;
    public DemoAuthRepository(Context context) {
        preferences = context.getSharedPreferences("demo_identity", Context.MODE_PRIVATE);
        int index = preferences.getInt("profile", 0);
        if (index == 1 || index == 2) publish(index);
    }
    private void publish(int index) {
        current = new Session("demo-" + index, index == 1 ? "Ибраһим" : "Данияр", "");
        state.setValue(current);
    }
    @Override public LiveData<Session> session() { return state; }
    @Override public Session current() { return current; }
    @Override public boolean isDemo() { return true; }
    @Override public void openDemo(int index) {
        if(index != 1 && index != 2) throw new IllegalArgumentException("Invalid demo index");
        preferences.edit().putInt("profile", index).apply(); publish(index);
    }
    @Override public void signIn(String email,String password,Callback cb) { cb.complete(R.string.auth_demo_only); }
    @Override public void register(String name,String email,String password,Callback cb) { cb.complete(R.string.auth_demo_only); }
    @Override public void resetPassword(String email,Callback cb) { cb.complete(R.string.auth_demo_only); }
    @Override public void signOut() { preferences.edit().remove("profile").apply(); current=null; state.setValue(null); }
}
