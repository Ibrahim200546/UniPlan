package kz.uniplan.app.data.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.R;

/** Email/password authentication. Tokens are encrypted with Android Keystore. */
public final class SupabaseAuthRepository implements AuthRepository {
    private final MutableLiveData<Session> session = new MutableLiveData<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SecureSessionStore store;
    private final SupabaseHttp http = new SupabaseHttp(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY);
    private volatile Session current;
    private JSONObject tokens;
    private long epoch;

    public SupabaseAuthRepository(Context context) {
        store = new SecureSessionStore(context);
        final long captured=epoch;
        worker.execute(() -> {
            try {
                String saved=store.load();
                if(saved==null)return;
                JSONObject data=new JSONObject(saved);
                synchronized(this) {
                    if(epoch!=captured)return;
                    tokens=data;
                    Session restored=identity(data.getJSONObject("user"));
                    main.post(() -> { synchronized(this) { if(epoch==captured){current=restored;session.setValue(restored);} } });
                }
            } catch(Exception invalid) { try {store.clear();} catch(Exception ignored){} }
        });
    }
    private Session identity(JSONObject user) throws Exception {
        JSONObject metadata=user.optJSONObject("user_metadata");
        String email=user.optString("email","");
        return new Session(user.getString("id"),metadata==null?email:metadata.optString("display_name",email),email);
    }
    public LiveData<Session> session(){return session;}
    public Session current(){return current;}
    public boolean isDemo(){return false;}
    public void openDemo(int index){}
    public void signIn(String email,String password,Callback callback){authenticate(false,"",email,password,callback);}
    public void register(String name,String email,String password,Callback callback){authenticate(true,name,email,password,callback);}
    private void authenticate(boolean signup,String name,String email,String password,Callback callback){
        final long captured;
        synchronized(this){captured=++epoch;}
        worker.execute(() -> {
            Integer error=null;
            try {
                if(BuildConfig.SUPABASE_KEY.isEmpty())throw new IllegalStateException();
                JSONObject body=new JSONObject().put("email",email).put("password",password);
                if(signup)body.put("data",new JSONObject().put("display_name",name));
                SupabaseHttp.Response response=http.request("POST",signup?"/auth/v1/signup":"/auth/v1/token?grant_type=password",body,null);
                if(!response.successful()) {
                    String code=response.json.optString("error_code",response.json.optString("code"));
                    error=code.equals("email_not_confirmed")?R.string.auth_confirm_email:response.status==429?R.string.auth_rate_limit:R.string.auth_failed;
                } else if(response.json.optString("access_token").isEmpty())error=R.string.auth_confirm_email;
                else {
                    JSONObject data=response.json;
                    data.put("expires_at",System.currentTimeMillis()/1000+data.optLong("expires_in",3600));
                    Session user=identity(data.getJSONObject("user"));
                    synchronized(this){
                        if(epoch!=captured)return;
                        store.save(data.toString());tokens=data;
                    }
                    main.post(() -> {synchronized(this){if(epoch==captured){current=user;session.setValue(user);}}});
                }
            }catch(IllegalStateException missing){error=R.string.auth_configuration;}
            catch(Exception failure){error=R.string.auth_network;}
            Integer result=error;
            main.post(() -> { synchronized(this){if(epoch==captured)callback.complete(result);} });
        });
    }
    @Override public synchronized String accessToken() throws Exception {
        if(current==null||tokens==null)throw new IllegalStateException("No session");
        if(tokens.optLong("expires_at")>System.currentTimeMillis()/1000+60)return tokens.getString("access_token");
        SupabaseHttp.Response response=http.request("POST","/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",tokens.getString("refresh_token")),null);
        if(!response.successful())throw new java.io.IOException("Session refresh failed");
        JSONObject refreshed=response.json;
        refreshed.put("expires_at",System.currentTimeMillis()/1000+refreshed.optLong("expires_in",3600));
        store.save(refreshed.toString());tokens=refreshed;
        return tokens.getString("access_token");
    }
    public void resetPassword(String email,Callback callback){worker.execute(() -> {
        Integer error=null;
        try {if(!http.request("POST","/auth/v1/recover",new JSONObject().put("email",email),null).successful())error=R.string.auth_failed;}
        catch(Exception failure){error=R.string.auth_network;}
        Integer result=error;main.post(() -> callback.complete(result));
    });}
    public void signOut(){
        final String previous;
        synchronized(this){epoch++;previous=tokens==null?null:tokens.optString("access_token");tokens=null;current=null;try{store.clear();}catch(Exception ignored){}}
        session.setValue(null);
        if(previous!=null)worker.execute(() -> {try{http.request("POST","/auth/v1/logout?scope=local",null,previous);}catch(Exception ignored){}});
    }
}
