package kz.uniplan.app.data.auth;
import android.content.Context;
import kz.uniplan.app.BuildConfig;

public final class AuthProvider {
    private AuthProvider() {}
    public static AuthRepository create(Context context) {
        SupabaseHttp http=new SupabaseHttp(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY);
        return new SupabaseAuthRepository(http::request,new SecureSessionStore(context),
                new SecureSessionStore(context,"pkce"),BuildConfig.SUPABASE_URL,http.configured());
    }
}
