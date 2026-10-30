package kz.uniplan.app.data.sync;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.auth.SupabaseHttp;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.SyncStateEntity;

/** Invoked on StudyRepository's serial executor, in order with all local writes. */
public final class CloudSync {
    public static final class Conflict {
        public final AuthRepository.Session session;
        final long remoteRevision;
        final String localSnapshot;
        Conflict(AuthRepository.Session session,long revision,String snapshot) {
            this.session=session; remoteRevision=revision; localSnapshot=snapshot;
        }
    }
    private final AppDatabase db;
    private final StudyDao dao;
    private final AuthRepository auth;
    private final SupabaseHttp.Transport http;
    public CloudSync(AppDatabase db,AuthRepository auth,SupabaseHttp.Transport http) {
        this.db=db; dao=db.studyDao(); this.auth=auth; this.http=http;
    }
    private void authorized(AuthRepository.Session session) throws IOException {
        if(session==null || auth.current()!=session || !auth.supportsCloud()) throw new IOException("Session changed");
    }
    private SupabaseHttp.Response request(AuthRepository.Session session,String method,String path,JSONObject body) throws Exception {
        authorized(session); String token=auth.accessToken(); authorized(session);
        SupabaseHttp.Response response=http.request(method,path,body,token);
        authorized(session);
        if(!response.successful()) throw new IOException("Cloud request failed: "+response.status);
        return response;
    }
    /** Null means success; a conflict never mutates either copy. Choice is tied to both inspected versions. */
    public Conflict sync(AuthRepository.Session session,Conflict choice,boolean keepLocal) throws Exception {
        authorized(session);
        SyncStateEntity state=dao.syncState(session.uid);
        if(state==null) throw new IOException("Local profile is not ready");
        JSONObject local=SnapshotCodec.encode(dao,session.uid);
        String localText=local.toString();
        JSONArray rows=request(session,"GET","/rest/v1/uniplan_state?select=revision,payload&user_id=eq."+session.uid,null).array();
        if(rows.length()>1) throw new IOException("Unexpected cloud response");
        JSONObject remote=rows.length()==0?null:rows.getJSONObject(0);
        long revision=remote==null?0:remote.getLong("revision");
        if(remote!=null && revision<=0) throw new IOException("Invalid revision");
        if(choice!=null) {
            if(choice.session!=session || choice.remoteRevision!=revision || !choice.localSnapshot.equals(localText))
                return new Conflict(session,revision,localText);
            if(keepLocal) return push(session,revision,local);
            // A missing remote is never interpreted as permission to delete local records.
            if(remote==null) throw new IOException("Cloud snapshot is unavailable");
            pull(session,state,revision,remote.getJSONObject("payload")); return null;
        }
        if(revision!=state.revision && (state.dirty || remote==null)) return new Conflict(session,revision,localText);
        if(remote==null || state.dirty) return push(session,revision,local);
        if(revision!=state.revision) pull(session,state,revision,remote.getJSONObject("payload"));
        return null;
    }
    private Conflict push(AuthRepository.Session session,long expected,JSONObject local) throws Exception {
        JSONObject reply=request(session,"POST","/rest/v1/rpc/save_uniplan_state",
                new JSONObject().put("expected_revision",expected).put("data",local)).json;
        if(!reply.optBoolean("ok")) {
            if(!reply.optBoolean("conflict") || !reply.has("revision")) throw new IOException("Invalid save response");
            return new Conflict(session,reply.getLong("revision"),local.toString());
        }
        long revision=reply.getLong("revision");
        if(revision!=expected+1) throw new IOException("Invalid saved revision");
        authorized(session);
        SyncStateEntity state=dao.syncState(session.uid); state.revision=revision; state.dirty=false;
        dao.saveSyncState(state); return null;
    }
    private void pull(AuthRepository.Session session,SyncStateEntity state,long revision,JSONObject payload) throws Exception {
        SnapshotCodec.Snapshot snapshot=SnapshotCodec.decode(payload);
        db.runInTransaction(() -> {
            authorized(session); SnapshotCodec.replace(dao,session.uid,snapshot);
            state.revision=revision; state.dirty=false; dao.saveSyncState(state); authorized(session); return null;
        });
    }
}
