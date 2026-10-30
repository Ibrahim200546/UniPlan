package kz.uniplan.app.data.auth;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import kz.uniplan.app.R;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk=28,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SupabaseAuthTest {
    private static final String UID="11111111-1111-4111-8111-111111111111";
    private static final String PROJECT="https://project.supabase.co";
    static class MemoryStore implements SessionStorage {
        volatile String value; boolean fail;
        public void save(String value) throws Exception { if(fail) throw new IOException("disk"); this.value=value; }
        public String load() { return value; }
        public void clear() { value=null; }
    }
    private JSONObject tokens(boolean expired) throws Exception {
        return new JSONObject().put("access_token",expired?"old-token":"new-token").put("refresh_token","refresh")
            .put("expires_at",System.currentTimeMillis()/1000+(expired?-10:3600))
            .put("user",new JSONObject().put("id",UID).put("email","student@example.com")
                .put("user_metadata",new JSONObject().put("display_name","Student")));
    }
    private SupabaseHttp.Response response(JSONObject value) { return new SupabaseHttp.Response(200,value.toString()); }
    interface Start { void run(AuthRepository.Callback cb); }
    private Integer await(Start start) throws Exception {
        CountDownLatch done=new CountDownLatch(1); AtomicReference<Integer> result=new AtomicReference<>();
        start.run(error->{ result.set(error); done.countDown(); });
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(done.getCount()>0 && System.nanoTime()<deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5); }
        assertEquals("Auth callback completed",0,done.getCount()); return result.get();
    }
    @Test public void confirmationRequiredNeverPublishesAnAuthenticatedSession() throws Exception {
        MemoryStore storage=new MemoryStore(),pending=new MemoryStore();
        SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->{
            assertTrue(path.startsWith("/auth/v1/signup?redirect_to="));
            assertEquals("s256",body.optString("code_challenge_method"));
            assertFalse(body.optString("code_challenge").isEmpty());
            assertNull(bearer); return new SupabaseHttp.Response(200,"{\"id\":\""+UID+"\"}");
        },storage,pending,PROJECT,true);
        assertEquals(Integer.valueOf(R.string.auth_confirm_email),await(cb->auth.register("Student","student@example.com","password",cb)));
        assertNull(auth.current()); assertNull(storage.value); assertNotNull(pending.value);
        assertFalse(pending.value.contains("password"));
    }
    @Test public void refreshKeepsTheSameIdentityAndPersistsTheRotatedToken() throws Exception {
        MemoryStore storage=new MemoryStore(); JSONObject first=tokens(true),next=tokens(false);
        SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->response(path.endsWith("password")?first:next),storage,new MemoryStore(),PROJECT,true);
        assertNull(await(cb->auth.signIn("student@example.com","password",cb)));
        AuthRepository.Session identity=auth.current(); assertNotNull(identity);
        assertEquals("new-token",auth.accessToken()); shadowOf(Looper.getMainLooper()).idle();
        assertSame(identity,auth.current()); assertSame(identity,auth.session().getValue());
        assertTrue(storage.value.contains("new-token"));
    }
    @Test public void lateLoginCannotRestoreAnAccountAfterSignOut() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        MemoryStore storage=new MemoryStore(); JSONObject result=tokens(false);
        SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->{
            entered.countDown(); try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new IOException(e); }
            return response(result);
        },storage,new MemoryStore(),PROJECT,true);
        CountDownLatch done=new CountDownLatch(1);
        auth.signIn("student@example.com","password",error->done.countDown());
        assertTrue(entered.await(5,TimeUnit.SECONDS)); auth.signOut(); release.countDown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(done.getCount()>0 && System.nanoTime()<deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5); }
        assertEquals(0,done.getCount()); assertNull(auth.current()); assertNull(storage.value);
    }
    @Test public void lateRefreshCannotReplaceOrClearANewerLogin() throws Exception {
        String newerUid="22222222-2222-4222-8222-222222222222";
        for(boolean revoked:new boolean[]{false,true}) {
            MemoryStore storage=new MemoryStore();
            JSONObject first=tokens(true),rotated=tokens(false),newer=tokens(false);
            newer.getJSONObject("user").put("id",newerUid);
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->{
                if(path.endsWith("password")) return response(body.optString("email").equals("new@example.com")?newer:first);
                entered.countDown();
                try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new IOException(e); }
                return revoked?new SupabaseHttp.Response(400,"{\"error_code\":\"refresh_token_not_found\"}"):response(rotated);
            },storage,new MemoryStore(),PROJECT,true);
            assertNull(await(cb->auth.signIn("student@example.com","password",cb)));
            AtomicReference<Exception> failure=new AtomicReference<>();
            Thread refresh=new Thread(()->{ try { auth.accessToken(); } catch(Exception e) { failure.set(e); } });
            refresh.start();
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                assertNull(await(cb->auth.signIn("new@example.com","password",cb)));
            } finally { release.countDown(); }
            refresh.join(5000); assertFalse(refresh.isAlive());
            shadowOf(Looper.getMainLooper()).idle();
            assertNotNull("Stale refresh must fail",failure.get());
            assertEquals(newerUid,auth.current().uid);
            assertEquals(newerUid,auth.session().getValue().uid);
            assertEquals(newerUid,new JSONObject(storage.value).getJSONObject("user").getString("id"));
        }
    }
    @Test public void failedEncryptedPersistenceDoesNotSignIn() throws Exception {
        MemoryStore storage=new MemoryStore(); storage.fail=true; JSONObject result=tokens(false);
        SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->response(result),storage,new MemoryStore(),PROJECT,true);
        assertNotNull(await(cb->auth.signIn("student@example.com","password",cb))); assertNull(auth.current());
    }
    @Test public void recoveryUsesPkceAndDoesNotOpenTheAppUntilPasswordIsUpdated() throws Exception {
        MemoryStore storage=new MemoryStore(),pending=new MemoryStore(); JSONObject result=tokens(false);
        SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->{
            if(path.contains("/recover")) return new SupabaseHttp.Response(200,"{}");
            if(path.contains("grant_type=pkce")) { assertTrue(body.optString("code_verifier").length()>=43); return response(result); }
            if(path.equals("/auth/v1/user")) { assertEquals("PUT",method); assertEquals("new-token",bearer); return response(result.optJSONObject("user")); }
            throw new IOException("Unexpected request");
        },storage,pending,PROJECT,true);
        assertNull(await(cb->auth.resetPassword("student@example.com",cb)));
        assertNotNull(await(cb->auth.handleRedirect("https://evil.example/auth/callback?code=code",cb)));
        assertNull(await(cb->auth.handleRedirect(SupabaseAuthRepository.REDIRECT+"?code=code",cb)));
        assertNull(auth.current()); assertEquals(Boolean.TRUE,auth.recovering().getValue());
        assertThrows(Exception.class,auth::accessToken);
        assertNull(await(cb->auth.updatePassword("new-password",cb)));
        assertEquals(UID,auth.current().uid); assertEquals(Boolean.FALSE,auth.recovering().getValue());
        assertNull(pending.value);
    }
    @Test public void revokedRefreshEndsSessionButTransientFailurePreservesOfflineIdentity() throws Exception {
        MemoryStore storage=new MemoryStore(); JSONObject first=tokens(true); boolean[] revoked={false};
        SupabaseAuthRepository auth=new SupabaseAuthRepository((method,path,body,bearer)->{
            if(path.endsWith("password")) return response(first);
            if(!revoked[0]) throw new IOException("offline");
            return new SupabaseHttp.Response(400,"{\"error_code\":\"refresh_token_not_found\"}");
        },storage,new MemoryStore(),PROJECT,true);
        assertNull(await(cb->auth.signIn("student@example.com","password",cb)));
        assertThrows(Exception.class,auth::accessToken); assertNotNull(auth.current());
        revoked[0]=true; assertThrows(Exception.class,auth::accessToken); assertNull(auth.current()); assertNull(storage.value);
    }
}
