package kz.uniplan.app.data.repository;
import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk=28,application=Application.class)
public class CloudSnapshotTest {
    static final class Auth implements AuthRepository {
        final Session user=new Session("a","Student","a@example.invalid");
        public Session current(){return user;} public LiveData<Session> session(){return new MutableLiveData<>(user);}
        public boolean isDemo(){return false;} public void openDemo(int i){} public void signOut(){}
        public void signIn(String e,String p,Callback c){} public void register(String n,String e,String p,Callback c){} public void resetPassword(String e,Callback c){}
    }
    @Test public void restoreRemapsRelationsPreservesOtherOwnerAndRollsBackInvalidSnapshots() throws Exception {
        AppDatabase db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase.class).allowMainThreadQueries().build();
        try {
            StudyDao dao=db.studyDao();Auth auth=new Auth();CloudSync cloud=new CloudSync(ApplicationProvider.getApplicationContext(),db,auth);
            ProfileEntity p=new ProfileEntity();p.ownerUid="a";p.displayName="Student";dao.insertProfile(p);
            SubjectEntity own=new SubjectEntity();own.ownerUid="a";own.name="Math";long sourceId=dao.insertSubject(own);
            SubjectEntity other=new SubjectEntity();other.ownerUid="b";other.name="Private";long otherId=dao.insertSubject(other);
            TaskEntity task=new TaskEntity();task.ownerUid="a";task.subjectId=sourceId;task.title="Read";task.dueAt=System.currentTimeMillis()+86400000;dao.insertTask(task);
            NoteEntity note=new NoteEntity();note.ownerUid="a";note.subjectId=sourceId;note.title="Notes";note.content="Text";dao.insertNote(note);
            JSONObject snapshot=cloud.snapshot("a");assertFalse(snapshot.toString().contains("ownerUid"));
            cloud.restore(auth.user,snapshot);
            long restoredId=dao.subjects("a").get(0).id;
            assertNotEquals(sourceId,restoredId);assertEquals(restoredId,dao.tasks("a").get(0).subjectId);
            assertEquals(Long.valueOf(restoredId),dao.notes("a").get(0).subjectId);assertNotNull(dao.subject("b",otherId));
            snapshot.getJSONArray("tasks").getJSONObject(0).put("subjectId",999999);
            try{cloud.restore(auth.user,snapshot);fail("Broken reference accepted");}catch(Exception expected){}
            assertEquals(restoredId,dao.subjects("a").get(0).id);assertEquals(1,dao.tasks("a").size());assertNotNull(dao.subject("b",otherId));
        }finally{db.close();}
    }
}
