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
    default boolean supportsCloud() { return false; }
    default LiveData<Boolean> recovering() { return new androidx.lifecycle.MutableLiveData<>(false); }
    default void handleRedirect(String uri, Callback callback) { callback.complete(kz.uniplan.app.R.string.auth_failed); }
    default void updatePassword(String password, Callback callback) { callback.complete(kz.uniplan.app.R.string.auth_failed); }
    default void resendConfirmation(String email, Callback callback) { callback.complete(kz.uniplan.app.R.string.auth_failed); }
    /** Cloud-only token access. Call on a worker thread; implementations may refresh tokens. */
    default String accessToken() throws Exception { throw new IllegalStateException("Cloud authentication is not configured"); }
}
