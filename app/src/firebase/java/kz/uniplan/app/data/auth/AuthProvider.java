package kz.uniplan.app.data.auth;
import android.content.Context;
public final class AuthProvider {
    private AuthProvider() {}
    public static AuthRepository create(Context context) { return new FirebaseAuthRepository(context); }
}
