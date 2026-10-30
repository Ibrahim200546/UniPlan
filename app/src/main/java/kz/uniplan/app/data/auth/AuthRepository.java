package kz.uniplan.app.data.auth;

import androidx.lifecycle.LiveData;

/** The only authority for identity. Session objects are stable for an auth epoch. */
public interface AuthRepository {
    final class Session {
        public final String uid, displayName, email;
        public Session(String uid, String displayName, String email) {
            this.uid = uid; this.displayName = displayName; this.email = email;
        }
    }
    interface Callback { void complete(Integer errorStringRes); }
    LiveData<Session> session();
    Session current();
    boolean isDemo();
    void openDemo(int index);
    void signIn(String email, String password, Callback callback);
    void register(String name, String email, String password, Callback callback);
    void resetPassword(String email, Callback callback);
    void signOut();
}
