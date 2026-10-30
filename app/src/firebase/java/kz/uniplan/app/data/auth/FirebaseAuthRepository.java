package kz.uniplan.app.data.auth;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import kz.uniplan.app.R;

public final class FirebaseAuthRepository implements AuthRepository {
    private final MutableLiveData<Session> state = new MutableLiveData<>(null);
    private FirebaseAuth firebase;
    private volatile Session current;
    private boolean registrationInProgress;
    public FirebaseAuthRepository(Context context) {
        FirebaseApp app = FirebaseApp.initializeApp(context);
        if (app != null) {
            firebase = FirebaseAuth.getInstance(app);
            publish(firebase.getCurrentUser());
            firebase.addAuthStateListener(auth -> { if (!registrationInProgress) publish(auth.getCurrentUser()); });
        }
    }
    private void publish(FirebaseUser user) {
        if(user==null) { current=null; state.setValue(null); return; }
        // Auth SDK remains the authority; stable object identity protects pending work.
        if(current != null && current.uid.equals(user.getUid())) return;
        current = new Session(user.getUid(), user.getDisplayName()==null ? "" : user.getDisplayName(), user.getEmail()==null ? "" : user.getEmail());
        state.setValue(current);
    }
    private boolean ready(Callback cb) { if(firebase!=null) return true; cb.complete(R.string.auth_configuration); return false; }
    private int error(Exception ex) {
        if(ex instanceof FirebaseNetworkException) return R.string.auth_network;
        if(ex instanceof FirebaseAuthWeakPasswordException) return R.string.auth_password_policy;
        return R.string.auth_failed; // Do not expose account-existence or raw server messages.
    }
    @Override public LiveData<Session> session() { return state; }
    @Override public Session current() { return current; }
    @Override public boolean isDemo() { return false; }
    @Override public void openDemo(int index) { throw new IllegalStateException("Demo unavailable"); }
    @Override public void signIn(String email,String password,Callback cb) {
        if(!ready(cb)) return;
        firebase.signInWithEmailAndPassword(email,password).addOnCompleteListener(result -> {
            if(result.isSuccessful()) { publish(firebase.getCurrentUser()); cb.complete(null); }
            else cb.complete(error(result.getException()));
        });
    }
    @Override public void register(String name,String email,String password,Callback cb) {
        if(!ready(cb)) return;
        registrationInProgress=true;
        firebase.createUserWithEmailAndPassword(email,password).addOnCompleteListener(result -> {
            if(!result.isSuccessful() || firebase.getCurrentUser()==null) { registrationInProgress=false; cb.complete(error(result.getException())); return; }
            FirebaseUser user=firebase.getCurrentUser();
            user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build()).addOnCompleteListener(updated -> {
                registrationInProgress=false;
                publish(firebase.getCurrentUser());
                // Account creation succeeded even when optional display-name update did not.
                cb.complete(null);
            });
        });
    }
    @Override public void resetPassword(String email,Callback cb) {
        if(!ready(cb)) return;
        firebase.sendPasswordResetEmail(email).addOnCompleteListener(result -> {
            if(result.getException() instanceof FirebaseNetworkException) cb.complete(R.string.auth_network);
            else cb.complete(null); // Neutral whether the address exists or not.
        });
    }
    @Override public void signOut() { if(firebase!=null) firebase.signOut(); current=null; state.setValue(null); }
}
