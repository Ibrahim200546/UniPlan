package kz.uniplan.app.data.auth;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import kz.uniplan.app.R;

/** Supabase Auth REST client. PKCE binds email links to the requesting installation. */
public final class SupabaseAuthRepository implements AuthRepository {
    public static final String REDIRECT = "kz.uniplan.app://auth/callback";
    private final SupabaseHttp.Transport http;
    private final SessionStorage store, pending;
    private final String project;
    private final boolean configured;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object(), refreshLock = new Object();
    private final MutableLiveData<Session> state = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> recoveryState = new MutableLiveData<>(false);
    private volatile Session current;
    private JSONObject tokens;
    private long epoch;
    private boolean recovery;

    public SupabaseAuthRepository(SupabaseHttp.Transport http, SessionStorage store,
            SessionStorage pending, String project, boolean configured) {
        this.http=http; this.store=store; this.pending=pending; this.project=project; this.configured=configured;
        worker.execute(() -> {
            try {
                String saved=store.load();
                if(saved==null || !configured) return;
                JSONObject value=new JSONObject(saved);
                if(!project.equals(value.optString("project"))) { store.clear(); return; }
                synchronized(lock) {
                    if(epoch!=0) return;
                    install(value, value.optBoolean("recovery"), 0, false);
                }
            } catch(Exception invalid) {
                synchronized(lock) { if(epoch==0) { try { store.clear(); } catch(Exception ignored) { /* Fail closed. */ } } }
            }
        });
    }
    @Override public LiveData<Session> session() { return state; }
    @Override public Session current() { return current; }
    @Override public boolean isDemo() { return false; }
    @Override public boolean supportsCloud() { return true; }
    @Override public LiveData<Boolean> recovering() { return recoveryState; }
    @Override public void openDemo(int index) { /* This provider never creates a local identity. */ }

    private interface Action { Integer run(long generation) throws Exception; }
    private void run(Callback callback, Action action) {
        final long generation;
        synchronized(lock) { generation=epoch; }
        worker.execute(() -> {
            Integer result;
            try { result=configured ? action.run(generation) : Integer.valueOf(R.string.auth_configuration); }
            catch(AuthError error) { result=error.message; }
            catch(IOException network) { result=R.string.auth_network; }
            catch(Exception failure) { result=R.string.auth_failed; }
            final Integer notice=result;
            main.post(() -> { synchronized(lock) { callback.complete(epoch==generation?notice:Integer.valueOf(R.string.auth_session_expired)); } });
        });
    }
    private static final class AuthError extends Exception {
        final int message;
        AuthError(int message) { this.message=message; }
    }
    private static JSONObject checked(SupabaseHttp.Response response) throws AuthError {
        if(response.successful()) return response.json;
        String code=response.json.optString("error_code",response.json.optString("code"));
        if(response.status==429) throw new AuthError(R.string.auth_rate_limit);
        if(code.equals("email_not_confirmed")) throw new AuthError(R.string.auth_confirm_email);
        if(code.equals("weak_password")) throw new AuthError(R.string.auth_password_policy);
        if(code.equals("otp_expired") || code.equals("flow_state_expired") || code.equals("bad_code_verifier")) throw new AuthError(R.string.auth_link_expired);
        throw new AuthError(R.string.auth_failed);
    }
    private void requireEpoch(long generation) throws AuthError {
        if(epoch!=generation) throw new AuthError(R.string.auth_session_expired);
    }
    /** Caller holds lock. Store first; failed encrypted storage must never create a signed-in session. */
    private void install(JSONObject value, boolean recovering, long generation, boolean persist) throws Exception {
        requireEpoch(generation);
        String access=value.getString("access_token"), refresh=value.getString("refresh_token");
        JSONObject user=value.getJSONObject("user");
        String uid=user.getString("id"); UUID.fromString(uid);
        if(access.isEmpty() || refresh.isEmpty()) throw new AuthError(R.string.auth_failed);
        if(!value.has("expires_at")) value.put("expires_at",System.currentTimeMillis()/1000+value.getLong("expires_in"));
        value.put("project",project).put("recovery",recovering);
        if(persist) store.save(value.toString());
        tokens=value; recovery=recovering;
        if(recovering) current=null;
        else if(current==null || !uid.equals(current.uid)) {
            JSONObject metadata=user.optJSONObject("user_metadata");
            String email=user.optString("email");
            current=new Session(uid,metadata==null?email:metadata.optString("display_name",email),email);
        }
        Session published=current;
        main.post(() -> { synchronized(lock) { if(epoch==generation && current==published) {
            recoveryState.setValue(recovery); state.setValue(published);
        } } });
    }
    @Override public void signIn(String email,String password,Callback callback) {
        run(callback,generation -> {
            JSONObject value=checked(http.request("POST","/auth/v1/token?grant_type=password",
                    new JSONObject().put("email",email).put("password",password),null));
            synchronized(lock) { install(value,false,generation,true); pending.clear(); }
            return null;
        });
    }
    private JSONObject pkce(String flow,long generation) throws Exception {
        byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
        String verifier=Base64.encodeToString(random,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        String challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        synchronized(lock) {
            requireEpoch(generation);
            pending.save(new JSONObject().put("verifier",verifier).put("flow",flow).put("project",project)
                    .put("createdAt",System.currentTimeMillis()).toString());
        }
        return new JSONObject().put("code_challenge",challenge).put("code_challenge_method","s256");
    }
    @Override public void register(String name,String email,String password,Callback callback) {
        run(callback,generation -> {
            JSONObject body=pkce("signup",generation).put("email",email).put("password",password)
                    .put("data",new JSONObject().put("display_name",name));
            JSONObject value=checked(http.request("POST","/auth/v1/signup?redirect_to="+Uri.encode(REDIRECT),body,null));
            if(value.has("access_token")) { synchronized(lock) { install(value,false,generation,true); pending.clear(); } return null; }
            return R.string.auth_confirm_email;
        });
    }
    @Override public void resetPassword(String email,Callback callback) {
        run(callback,generation -> {
            checked(http.request("POST","/auth/v1/recover?redirect_to="+Uri.encode(REDIRECT),
                    pkce("recovery",generation).put("email",email),null));
            return null;
        });
    }
    @Override public void resendConfirmation(String email,Callback callback) {
        run(callback,generation -> {
            // Resending preserves the original PKCE verifier; it must not create an unrelated flow.
            checked(http.request("POST","/auth/v1/resend?redirect_to="+Uri.encode(REDIRECT),
                    new JSONObject().put("email",email).put("type","signup"),null));
            return R.string.auth_confirm_email;
        });
    }
    @Override public void handleRedirect(String rawUri,Callback callback) {
        run(callback,generation -> {
            Uri uri=Uri.parse(rawUri);
            if(!"kz.uniplan.app".equals(uri.getScheme()) || !"auth".equals(uri.getHost())
                    || !"/callback".equals(uri.getPath()) || uri.getUserInfo()!=null || uri.getPort()!=-1)
                throw new AuthError(R.string.auth_link_expired);
            String code=uri.getQueryParameter("code"), saved=pending.load();
            if(code==null || code.isEmpty() || saved==null) throw new AuthError(R.string.auth_link_expired);
            JSONObject flow=new JSONObject(saved);
            long age=System.currentTimeMillis()-flow.getLong("createdAt");
            if(!project.equals(flow.getString("project")) || age<0 || age>86400000L) throw new AuthError(R.string.auth_link_expired);
            JSONObject value=checked(http.request("POST","/auth/v1/token?grant_type=pkce",
                    new JSONObject().put("auth_code",code).put("code_verifier",flow.getString("verifier")),null));
            synchronized(lock) { install(value,"recovery".equals(flow.getString("flow")),generation,true); pending.clear(); }
            return null;
        });
    }
    @Override public String accessToken() throws Exception { return token(false); }
    private String token(boolean allowRecovery) throws Exception {
        synchronized(refreshLock) {
            final long generation; final JSONObject snapshot;
            synchronized(lock) {
                if(tokens==null || (current==null && !(allowRecovery && recovery))) throw new AuthError(R.string.auth_session_expired);
                generation=epoch; snapshot=tokens;
                if(snapshot.getLong("expires_at")>System.currentTimeMillis()/1000+60) return snapshot.getString("access_token");
            }
            SupabaseHttp.Response response=http.request("POST","/auth/v1/token?grant_type=refresh_token",
                    new JSONObject().put("refresh_token",snapshot.getString("refresh_token")),null);
            if(!response.successful()) {
                String code=response.json.optString("error_code");
                if(response.status==401 || response.status==403 || code.startsWith("refresh_token_") || code.equals("session_not_found")) {
                    synchronized(lock) { if(epoch==generation && tokens==snapshot) clearLocal(); }
                }
                checked(response);
            }
            synchronized(lock) {
                requireEpoch(generation);
                // A new login or email callback can replace tokens while refresh is in flight.
                if(tokens!=snapshot) throw new AuthError(R.string.auth_session_expired);
                if(!snapshot.getJSONObject("user").getString("id").equals(response.json.getJSONObject("user").getString("id"))) throw new AuthError(R.string.auth_failed);
                install(response.json,recovery,generation,true);
                return tokens.getString("access_token");
            }
        }
    }
    @Override public void updatePassword(String password,Callback callback) {
        run(callback,generation -> {
            synchronized(lock) { if(!recovery) throw new AuthError(R.string.auth_link_expired); }
            JSONObject user=checked(http.request("PUT","/auth/v1/user",new JSONObject().put("password",password),token(true)));
            synchronized(lock) {
                requireEpoch(generation);
                JSONObject value=new JSONObject(tokens.toString()).put("user",user);
                install(value,false,generation,true);
            }
            return null;
        });
    }
    private void clearLocal() {
        epoch++; tokens=null; current=null; recovery=false;
        try { store.clear(); pending.clear(); } catch(Exception ignored) { /* No identity is retained in memory. */ }
        long cleared=epoch;
        main.post(() -> { synchronized(lock) { if(epoch==cleared) { recoveryState.setValue(false); state.setValue(null); } } });
    }
    @Override public void signOut() {
        final String previous;
        synchronized(lock) { previous=tokens==null?null:tokens.optString("access_token",null); clearLocal(); }
        if(previous!=null) worker.execute(() -> {
            try { http.request("POST","/auth/v1/logout?scope=local",null,previous); }
            catch(IOException ignored) { /* Offline logout clears this installation immediately. */ }
        });
    }
}
