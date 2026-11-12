package kz.uniplan.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.MutableLiveData;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.R;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.auth.SupabaseHttp;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.util.Validation;

/** Called only on StudyRepository's serial worker. CAS prevents silent overwrites. */
public final class CloudSync {
    private final AppDatabase db;
    private final StudyDao dao;
    private final AuthRepository auth;
    private final SharedPreferences prefs;
    private final SupabaseHttp http=new SupabaseHttp(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_KEY);
    public final MutableLiveData<Integer> status=new MutableLiveData<>(R.string.sync_local);
    public final MutableLiveData<Boolean> conflict=new MutableLiveData<>(false);
    public CloudSync(Context context,AppDatabase db,AuthRepository auth){this.db=db;this.dao=db.studyDao();this.auth=auth;prefs=context.getSharedPreferences("cloud_versions",Context.MODE_PRIVATE);}
    private void check(AuthRepository.Session user) throws Exception {if(auth.current()!=user)throw new Exception("Session changed");}
    public void dirty(String uid){if(!auth.isDemo())prefs.edit().putBoolean(uid+"_dirty",true).commit();}
    public void sync(AuthRepository.Session user,boolean preferLocal,boolean preferCloud){
        if(auth.isDemo()||!BuildConfig.FLAVOR.equals("supabase"))return;
        status.postValue(R.string.sync_busy);
        try {
            check(user);
            SupabaseHttp.Response response=http.request("GET","/rest/v1/uniplan_state?select=revision,payload",null,auth.accessToken());
            if(!response.successful())throw new Exception("Read failed");
            JSONArray rows=response.array();JSONObject remote=rows.length()==0?null:rows.getJSONObject(0);
            long remoteVersion=remote==null?0:remote.getLong("revision");
            long localVersion=prefs.getLong(user.uid+"_revision",0);
            boolean dirty=prefs.getBoolean(user.uid+"_dirty",false);
            check(user);
            if(preferCloud||(remote!=null&&!dirty&&!preferLocal)){
                if(remote!=null)restore(user,remote.getJSONObject("payload"));
                prefs.edit().putLong(user.uid+"_revision",remoteVersion).putBoolean(user.uid+"_dirty",false).commit();
            }else{
                if(!preferLocal&&remoteVersion!=localVersion){conflict.postValue(true);status.postValue(R.string.sync_conflict);return;}
                JSONObject data=snapshot(user.uid);
                JSONObject request=new JSONObject().put("expected_revision",preferLocal?remoteVersion:localVersion).put("data",data);
                check(user);
                SupabaseHttp.Response saved=http.request("POST","/rest/v1/rpc/save_uniplan_state",request,auth.accessToken());
                if(!saved.successful())throw new Exception("Write failed");
                check(user);
                if(!saved.json.optBoolean("ok")){conflict.postValue(true);status.postValue(R.string.sync_conflict);return;}
                prefs.edit().putLong(user.uid+"_revision",saved.json.getLong("revision")).putBoolean(user.uid+"_dirty",false).commit();
            }
            conflict.postValue(false);status.postValue(R.string.sync_done);
        }catch(Exception failure){status.postValue(R.string.sync_offline);}
    }
    private static JSONObject encode(Object value) throws Exception {
        JSONObject json=new JSONObject();
        for(Field field:value.getClass().getFields())if(!field.getName().equals("ownerUid"))json.put(field.getName(),field.get(value)==null?JSONObject.NULL:field.get(value));
        return json;
    }
    JSONObject snapshot(String uid) throws Exception {
        JSONObject data=new JSONObject().put("schemaVersion",1);
        ProfileEntity profile=dao.profile(uid);data.put("profile",profile==null?new JSONObject():encode(profile));
        JSONArray subjects=new JSONArray(),tasks=new JSONArray(),lessons=new JSONArray(),notes=new JSONArray();
        for(SubjectEntity item:dao.subjects(uid))subjects.put(encode(item));
        for(TaskEntity item:dao.tasks(uid))tasks.put(encode(item));
        for(LessonEntity item:dao.lessons(uid))lessons.put(encode(item));
        for(NoteEntity item:dao.notes(uid))notes.put(encode(item));
        return data.put("subjects",subjects).put("tasks",tasks).put("lessons",lessons).put("notes",notes);
    }
    private static <T> T decode(JSONObject json,Class<T> type,String uid) throws Exception {
        T value=type.getDeclaredConstructor().newInstance();
        for(Field field:type.getFields()){
            String name=field.getName();
            if(name.equals("ownerUid")){field.set(value,uid);continue;}
            if(!json.has(name)||json.isNull(name))continue;
            Class<?> target=field.getType();
            Object raw=json.get(name);
            if(target==String.class){if(!(raw instanceof String)||((String)raw).length()>200000)throw new Exception("Invalid text");field.set(value,raw);}
            else if(target==boolean.class){if(!(raw instanceof Boolean))throw new Exception("Invalid boolean");field.setBoolean(value,(Boolean)raw);}
            else if(target==long.class||target==Long.class){if(!(raw instanceof Number))throw new Exception("Invalid number");field.set(value,((Number)raw).longValue());}
            else if(target==int.class){if(!(raw instanceof Number))throw new Exception("Invalid number");field.setInt(value,((Number)raw).intValue());}
        }
        return value;
    }
    void restore(AuthRepository.Session user,JSONObject data) throws Exception {
        if(data.getInt("schemaVersion")!=1)throw new Exception("Unsupported schema");
        JSONArray subjects=data.getJSONArray("subjects"),tasks=data.getJSONArray("tasks"),lessons=data.getJSONArray("lessons"),notes=data.getJSONArray("notes");
        if(subjects.length()+tasks.length()+lessons.length()+notes.length()>10000)throw new Exception("Snapshot too large");
        check(user);
        db.runInTransaction(() -> {
            check(user);
            for(TaskEntity item:dao.tasks(user.uid))dao.deleteTask(user.uid,item.id);
            for(LessonEntity item:dao.lessons(user.uid))dao.deleteLesson(user.uid,item.id);
            for(NoteEntity item:dao.notes(user.uid))dao.deleteNote(user.uid,item.id);
            for(SubjectEntity item:dao.subjects(user.uid))dao.deleteSubject(user.uid,item.id);
            Map<Long,Long> ids=new HashMap<>();
            for(int i=0;i<subjects.length();i++){
                SubjectEntity item=decode(subjects.getJSONObject(i),SubjectEntity.class,user.uid);
                if(item.id<=0||!Validation.title(item.name)||ids.containsKey(item.id)||item.icon<0||item.icon>5)throw new Exception("Invalid subject");
                long old=item.id;item.id=0;ids.put(old,dao.insertSubject(item));
            }
            for(int i=0;i<tasks.length();i++){
                TaskEntity item=decode(tasks.getJSONObject(i),TaskEntity.class,user.uid);
                if(!ids.containsKey(item.subjectId)||!Validation.task(item.title,item.subjectId,item.kind,item.dueAt,item.priority))throw new Exception("Invalid task");
                item.id=0;item.subjectId=ids.get(item.subjectId);dao.insertTask(item);
            }
            for(int i=0;i<lessons.length();i++){
                LessonEntity item=decode(lessons.getJSONObject(i),LessonEntity.class,user.uid);
                if(!ids.containsKey(item.subjectId)||!Validation.lesson(item.dayOfWeek,item.startMinute,item.endMinute)||!Validation.webUrl(item.meetingUrl))throw new Exception("Invalid lesson");
                item.id=0;item.subjectId=ids.get(item.subjectId);dao.insertLesson(item);
            }
            for(int i=0;i<notes.length();i++){
                NoteEntity item=decode(notes.getJSONObject(i),NoteEntity.class,user.uid);
                if(!Validation.title(item.title)||(item.subjectId!=null&&!ids.containsKey(item.subjectId)))throw new Exception("Invalid note");
                item.id=0;if(item.subjectId!=null)item.subjectId=ids.get(item.subjectId);dao.insertNote(item);
            }
            ProfileEntity profile=decode(data.getJSONObject("profile"),ProfileEntity.class,user.uid);
            if(!Validation.title(profile.displayName)||profile.studyGroup.length()>100)throw new Exception("Invalid profile");
            dao.insertProfile(profile);dao.updateProfile(user.uid,profile.displayName,profile.studyGroup);
            check(user);return null;
        });
    }
}
