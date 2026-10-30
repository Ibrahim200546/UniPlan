package kz.uniplan.app.data.sync;

import static org.junit.Assert.*;
import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.auth.SupabaseHttp;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.entity.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk=28,application=Application.class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class CloudSyncTest {
    private static final String A="11111111-1111-4111-8111-111111111111", B="22222222-2222-4222-8222-222222222222";
    private final List<AppDatabase> databases=new ArrayList<>();
    private AppDatabase database() {
        AppDatabase db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase.class).allowMainThreadQueries().build();
        databases.add(db); ProfileEntity profile=new ProfileEntity(); profile.ownerUid=A; profile.displayName="Student"; db.studyDao().insertProfile(profile);
        SyncStateEntity state=new SyncStateEntity(); state.ownerUid=A; db.studyDao().saveSyncState(state); return db;
    }
    @After public void close() { for(AppDatabase db:databases) db.close(); }
    private void dirty(AppDatabase db) { SyncStateEntity state=db.studyDao().syncState(A); state.dirty=true; db.studyDao().saveSyncState(state); }
    private long subject(AppDatabase db,String uid,String name) {
        SubjectEntity subject=new SubjectEntity(); subject.ownerUid=uid; subject.name=name; return db.studyDao().insertSubject(subject);
    }
    private void fixture(AppDatabase db) {
        long id=subject(db,A,"Пән / Предмет");
        TaskEntity task=new TaskEntity(); task.ownerUid=A; task.subjectId=id; task.title="Task"; task.dueAt=1000; db.studyDao().insertTask(task);
        LessonEntity lesson=new LessonEntity(); lesson.ownerUid=A; lesson.subjectId=id; db.studyDao().insertLesson(lesson);
        NoteEntity note=new NoteEntity(); note.ownerUid=A; note.subjectId=id; note.title="Note"; note.content="ӘҒҚҢӨҰҮҺІ\nСтрока"; db.studyDao().insertNote(note); dirty(db);
    }
    private static class Auth implements AuthRepository {
        Session user=new Session(A,"Student","student@example.com"); MutableLiveData<Session> state=new MutableLiveData<>(user);
        public LiveData<Session> session(){return state;} public Session current(){return user;} public boolean isDemo(){return false;}
        public boolean supportsCloud(){return true;} public String accessToken(){return user.uid;}
        public void openDemo(int i){} public void signIn(String e,String p,Callback c){} public void register(String n,String e,String p,Callback c){}
        public void resetPassword(String e,Callback c){} public void signOut(){user=null;state.setValue(null);}
    }
    private static class Server implements SupabaseHttp.Transport {
        long revision; JSONObject payload; Runnable duringGet; boolean race;
        public SupabaseHttp.Response request(String method,String path,JSONObject body,String bearer) throws java.io.IOException {
            try {
                assertEquals(A,bearer);
                if(method.equals("GET")) {
                    if(duringGet!=null) duringGet.run();
                    JSONArray result=new JSONArray();
                    if(payload!=null) result.put(new JSONObject().put("revision",revision).put("payload",payload));
                    return new SupabaseHttp.Response(200,result.toString());
                }
                assertEquals("/rest/v1/rpc/save_uniplan_state",path);
                if(race) { revision++; race=false; }
                if(body.getLong("expected_revision")!=revision) return new SupabaseHttp.Response(200,new JSONObject().put("ok",false).put("conflict",true).put("revision",revision).toString());
                revision++; payload=new JSONObject(body.getJSONObject("data").toString());
                return new SupabaseHttp.Response(200,new JSONObject().put("ok",true).put("revision",revision).toString());
            } catch(org.json.JSONException e) { throw new java.io.IOException(e); }
        }
    }
    @Test public void twoDevicesRoundTripAllDataAndRemapIdsWithoutTouchingAnotherOwner() throws Exception {
        AppDatabase first=database(),second=database(); fixture(first); long other=subject(second,B,"Private B");
        Auth auth=new Auth(); Server server=new Server();
        assertNull(new CloudSync(first,auth,server).sync(auth.current(),null,false));
        assertNull(new CloudSync(second,auth,server).sync(auth.current(),null,false));
        assertEquals("Private B",second.studyDao().subject(B,other).name);
        SubjectEntity restored=second.studyDao().subjects(A).get(0); assertNotEquals(other,restored.id);
        assertEquals(restored.id,second.studyDao().tasks(A).get(0).subjectId);
        assertEquals(restored.id,second.studyDao().lessons(A).get(0).subjectId);
        assertEquals(Long.valueOf(restored.id),second.studyDao().notes(A).get(0).subjectId);
        assertEquals("ӘҒҚҢӨҰҮҺІ\nСтрока",second.studyDao().notes(A).get(0).content);
        assertFalse(second.studyDao().syncState(A).dirty); assertEquals(1,second.studyDao().syncState(A).revision);
        assertFalse(server.payload.toString().contains(A));
    }
    @Test public void conflictsAndChangedChoicesNeverSilentlyOverwriteEitherCopy() throws Exception {
        AppDatabase db=database(); fixture(db); Auth auth=new Auth(); Server server=new Server(); CloudSync cloud=new CloudSync(db,auth,server);
        assertNull(cloud.sync(auth.current(),null,false));
        server.revision=2; dirty(db);
        CloudSync.Conflict conflict=cloud.sync(auth.current(),null,false); assertNotNull(conflict); assertEquals(2,server.revision);
        subject(db,A,"New local edit");
        CloudSync.Conflict renewed=cloud.sync(auth.current(),conflict,true); assertNotNull(renewed); assertEquals(2,server.revision);
        assertNull(cloud.sync(auth.current(),renewed,true)); assertEquals(3,server.revision); assertFalse(db.studyDao().syncState(A).dirty);
    }
    @Test public void compareAndSwapRacePreservesDirtyLocalCopy() throws Exception {
        AppDatabase db=database(); fixture(db); Auth auth=new Auth(); Server server=new Server(); CloudSync cloud=new CloudSync(db,auth,server);
        assertNull(cloud.sync(auth.current(),null,false)); dirty(db); server.race=true;
        assertNotNull(cloud.sync(auth.current(),null,false)); assertTrue(db.studyDao().syncState(A).dirty); assertEquals(1,db.studyDao().syncState(A).revision);
    }
    @Test public void malformedRemoteNeverPartiallyDeletesLocalData() throws Exception {
        AppDatabase db=database(); fixture(db); Auth auth=new Auth(); Server server=new Server();
        server.payload=SnapshotCodec.encode(db.studyDao(),A); server.payload.getJSONArray("tasks").getJSONObject(0).put("subjectId",999);
        server.revision=1; SyncStateEntity state=db.studyDao().syncState(A); state.dirty=false; db.studyDao().saveSyncState(state);
        CloudSync cloud=new CloudSync(db,auth,server);
        assertThrows(Exception.class,()->cloud.sync(auth.current(),null,false));
        assertEquals(1,db.studyDao().tasks(A).size()); assertEquals(1,db.studyDao().subjects(A).size()); assertEquals(0,db.studyDao().syncState(A).revision);
    }
    @Test public void logoutDuringNetworkRequestStopsLocalApplicationAndUpload() throws Exception {
        AppDatabase db=database(); fixture(db); Auth auth=new Auth(); Server server=new Server(); server.duringGet=auth::signOut;
        CloudSync cloud=new CloudSync(db,auth,server); AuthRepository.Session captured=auth.current();
        assertThrows(Exception.class,()->cloud.sync(captured,null,false));
        assertNull(server.payload); assertTrue(db.studyDao().syncState(A).dirty);
    }
    @Test public void duplicateIdsInvalidLinksAndWrongPrimitiveTypesAreRejected() throws Exception {
        AppDatabase db=database(); fixture(db); JSONObject good=SnapshotCodec.encode(db.studyDao(),A);
        JSONObject duplicate=new JSONObject(good.toString()); duplicate.getJSONArray("subjects").put(duplicate.getJSONArray("subjects").getJSONObject(0));
        assertThrows(Exception.class,()->SnapshotCodec.decode(duplicate));
        JSONObject typed=new JSONObject(good.toString()); typed.getJSONArray("tasks").getJSONObject(0).put("completed","false");
        assertThrows(Exception.class,()->SnapshotCodec.decode(typed));
        JSONObject link=new JSONObject(good.toString()); link.getJSONArray("lessons").getJSONObject(0).put("meetingUrl","javascript:alert(1)");
        assertThrows(Exception.class,()->SnapshotCodec.decode(link));
    }
}
