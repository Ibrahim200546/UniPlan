package kz.uniplan.app.data.sync;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.util.Validation;

/** Explicit wire format. No owner IDs, credentials, paths or device preferences are serialized. */
public final class SnapshotCodec {
    public static final int MAX_BYTES=1_500_000, MAX_ROWS=10000;
    private SnapshotCodec() {}
    public static JSONObject encode(StudyDao dao,String uid) throws JSONException {
        ProfileEntity profile=dao.profile(uid);
        JSONObject result=new JSONObject().put("schemaVersion",1).put("profile",new JSONObject()
                .put("displayName",profile==null?"":profile.displayName).put("studyGroup",profile==null?"":profile.studyGroup));
        JSONArray subjects=new JSONArray(),tasks=new JSONArray(),lessons=new JSONArray(),notes=new JSONArray();
        for(SubjectEntity e:dao.subjects(uid)) subjects.put(new JSONObject().put("id",e.id).put("name",e.name)
                .put("teacher",e.teacher).put("color",e.color).put("icon",e.icon));
        for(TaskEntity e:dao.tasks(uid)) tasks.put(new JSONObject().put("id",e.id).put("subjectId",e.subjectId)
                .put("title",e.title).put("description",e.description).put("kind",e.kind).put("dueAt",e.dueAt)
                .put("priority",e.priority).put("completed",e.completed).put("createdAt",e.createdAt).put("updatedAt",e.updatedAt));
        for(LessonEntity e:dao.lessons(uid)) lessons.put(new JSONObject().put("id",e.id).put("subjectId",e.subjectId)
                .put("dayOfWeek",e.dayOfWeek).put("startMinute",e.startMinute).put("endMinute",e.endMinute)
                .put("room",e.room).put("lessonNumber",e.lessonNumber).put("lessonType",e.lessonType).put("teacher",e.teacher).put("meetingUrl",e.meetingUrl));
        for(NoteEntity e:dao.notes(uid)) notes.put(new JSONObject().put("id",e.id).put("subjectId",e.subjectId==null?JSONObject.NULL:e.subjectId)
                .put("title",e.title).put("content",e.content).put("createdAt",e.createdAt).put("updatedAt",e.updatedAt));
        result.put("subjects",subjects).put("tasks",tasks).put("lessons",lessons).put("notes",notes);
        decode(result); // Reject oversized/invalid local snapshots before sending anything.
        return result;
    }
    public static final class Snapshot {
        public final ProfileEntity profile=new ProfileEntity();
        public final List<SubjectEntity> subjects=new ArrayList<>();
        public final List<TaskEntity> tasks=new ArrayList<>();
        public final List<LessonEntity> lessons=new ArrayList<>();
        public final List<NoteEntity> notes=new ArrayList<>();
    }
    private static void valid(boolean condition) throws JSONException { if(!condition) throw new JSONException("Invalid study snapshot"); }
    private static long number(JSONObject object,String key,long min,long max) throws JSONException {
        Object value=object.get(key);
        valid(value instanceof Integer || value instanceof Long);
        long n=((Number)value).longValue(); valid(n>=min && n<=max); return n;
    }
    private static String text(JSONObject object,String key,int max,boolean required) throws JSONException {
        Object value=object.get(key); valid(value instanceof String);
        String string=(String)value; valid(string.length()<=max && (!required || !string.trim().isEmpty())); return string;
    }
    private static JSONArray rows(JSONObject object,String key) throws JSONException {
        JSONArray array=object.getJSONArray(key); valid(array.length()<=MAX_ROWS); return array;
    }
    private static long id(JSONObject object,Set<Long> seen) throws JSONException {
        long value=number(object,"id",1,Long.MAX_VALUE); valid(seen.add(value)); return value;
    }
    private static long subject(JSONObject object,Set<Long> subjects) throws JSONException {
        long value=number(object,"subjectId",1,Long.MAX_VALUE); valid(subjects.contains(value)); return value;
    }
    public static Snapshot decode(JSONObject data) throws JSONException {
        valid(data.toString().getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES);
        valid(number(data,"schemaVersion",1,1)==1);
        Snapshot s=new Snapshot(); JSONObject p=data.getJSONObject("profile");
        s.profile.displayName=text(p,"displayName",200,false); s.profile.studyGroup=text(p,"studyGroup",100,false);
        Set<Long> subjects=new HashSet<>(), ids=new HashSet<>();
        JSONArray array=rows(data,"subjects");
        for(int i=0;i<array.length();i++) {
            JSONObject item=array.getJSONObject(i); SubjectEntity e=new SubjectEntity();
            e.id=id(item,subjects); e.name=text(item,"name",200,true); e.teacher=text(item,"teacher",10000,false);
            e.color=(int)number(item,"color",Integer.MIN_VALUE,Integer.MAX_VALUE); e.icon=(int)number(item,"icon",0,5); s.subjects.add(e);
        }
        array=rows(data,"tasks");
        for(int i=0;i<array.length();i++) {
            JSONObject item=array.getJSONObject(i); TaskEntity e=new TaskEntity();
            e.id=id(item,ids); e.subjectId=subject(item,subjects); e.title=text(item,"title",200,true);
            e.description=text(item,"description",MAX_BYTES,false); e.kind=(int)number(item,"kind",0,1);
            e.dueAt=number(item,"dueAt",1,Long.MAX_VALUE); e.priority=(int)number(item,"priority",0,2);
            valid(item.get("completed") instanceof Boolean); e.completed=item.getBoolean("completed");
            e.createdAt=number(item,"createdAt",0,Long.MAX_VALUE); e.updatedAt=number(item,"updatedAt",0,Long.MAX_VALUE); s.tasks.add(e);
        }
        ids.clear(); array=rows(data,"lessons");
        for(int i=0;i<array.length();i++) {
            JSONObject item=array.getJSONObject(i); LessonEntity e=new LessonEntity();
            e.id=id(item,ids); e.subjectId=subject(item,subjects); e.dayOfWeek=(int)number(item,"dayOfWeek",1,7);
            e.startMinute=(int)number(item,"startMinute",0,1439); e.endMinute=(int)number(item,"endMinute",1,1439);
            valid(Validation.lesson(e.dayOfWeek,e.startMinute,e.endMinute)); e.room=text(item,"room",10000,false);
            e.lessonNumber=(int)number(item,"lessonNumber",0,99); e.lessonType=text(item,"lessonType",100,false);
            e.teacher=text(item,"teacher",300,false); e.meetingUrl=text(item,"meetingUrl",2048,false);
            valid(Validation.webUrl(e.meetingUrl)); s.lessons.add(e);
        }
        ids.clear(); array=rows(data,"notes");
        for(int i=0;i<array.length();i++) {
            JSONObject item=array.getJSONObject(i); NoteEntity e=new NoteEntity();
            e.id=id(item,ids); valid(item.has("subjectId")); e.subjectId=item.isNull("subjectId")?null:subject(item,subjects);
            e.title=text(item,"title",200,true); e.content=text(item,"content",MAX_BYTES,false);
            e.createdAt=number(item,"createdAt",0,Long.MAX_VALUE); e.updatedAt=number(item,"updatedAt",0,Long.MAX_VALUE); s.notes.add(e);
        }
        valid(s.subjects.size()+s.tasks.size()+s.lessons.size()+s.notes.size()<=MAX_ROWS); return s;
    }
    /** Invoke inside a Room transaction, after full decode. IDs are local to each installation. */
    public static void replace(StudyDao dao,String uid,Snapshot s) {
        dao.clearTasks(uid); dao.clearLessons(uid); dao.clearNotes(uid); dao.clearSubjects(uid);
        Map<Long,Long> remap=new HashMap<>();
        for(SubjectEntity original:s.subjects) { SubjectEntity e=original.copy(); e.ownerUid=uid; e.id=0; remap.put(original.id,dao.insertSubject(e)); }
        for(TaskEntity original:s.tasks) { TaskEntity e=original.copy(); e.ownerUid=uid; e.id=0; e.subjectId=remap.get(original.subjectId); dao.insertTask(e); }
        for(LessonEntity original:s.lessons) { LessonEntity e=original.copy(); e.ownerUid=uid; e.id=0; e.subjectId=remap.get(original.subjectId); dao.insertLesson(e); }
        for(NoteEntity original:s.notes) { NoteEntity e=original.copy(); e.ownerUid=uid; e.id=0; e.subjectId=original.subjectId==null?null:remap.get(original.subjectId); dao.insertNote(e); }
        ProfileEntity p=s.profile.copy(); p.ownerUid=uid; dao.insertProfile(p); dao.updateProfile(uid,p.displayName,p.studyGroup);
    }
}
