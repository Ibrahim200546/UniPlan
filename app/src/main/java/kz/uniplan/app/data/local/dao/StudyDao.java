package kz.uniplan.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import kz.uniplan.app.data.local.entity.*;

/** Every read and mutation names an owner; only StudyRepository is used by UI. */
@Dao
public abstract class StudyDao {
    @Query("SELECT * FROM profiles WHERE ownerUid = :uid LIMIT 1") public abstract LiveData<ProfileEntity> observeProfile(String uid);
    @Query("SELECT * FROM profiles WHERE ownerUid = :uid LIMIT 1") public abstract ProfileEntity profile(String uid);
    @Insert(onConflict = OnConflictStrategy.IGNORE) public abstract long insertProfile(ProfileEntity value);
    @Query("UPDATE profiles SET displayName=:name, studyGroup=:studyGroup WHERE ownerUid=:uid")
    public abstract int updateProfile(String uid, String name, String studyGroup);

    @Query("SELECT * FROM subjects WHERE ownerUid=:uid ORDER BY name COLLATE NOCASE, id") public abstract LiveData<List<SubjectEntity>> observeSubjects(String uid);
    @Query("SELECT * FROM tasks WHERE ownerUid=:uid ORDER BY dueAt, id") public abstract LiveData<List<TaskEntity>> observeTasks(String uid);
    @Query("SELECT * FROM lessons WHERE ownerUid=:uid ORDER BY dayOfWeek, startMinute, id") public abstract LiveData<List<LessonEntity>> observeLessons(String uid);
    @Query("SELECT * FROM notes WHERE ownerUid=:uid ORDER BY updatedAt DESC, id DESC") public abstract LiveData<List<NoteEntity>> observeNotes(String uid);
    @Query("SELECT * FROM subjects WHERE ownerUid=:uid ORDER BY id") public abstract List<SubjectEntity> subjects(String uid);
    @Query("SELECT * FROM tasks WHERE ownerUid=:uid ORDER BY dueAt, id") public abstract List<TaskEntity> tasks(String uid);
    @Query("SELECT * FROM lessons WHERE ownerUid=:uid ORDER BY dayOfWeek, startMinute, id") public abstract List<LessonEntity> lessons(String uid);
    @Query("SELECT * FROM notes WHERE ownerUid=:uid ORDER BY updatedAt DESC, id DESC") public abstract List<NoteEntity> notes(String uid);

    @Query("SELECT * FROM subjects WHERE id=:id AND ownerUid=:uid LIMIT 1") public abstract SubjectEntity subject(String uid,long id);
    @Query("SELECT * FROM tasks WHERE id=:id AND ownerUid=:uid LIMIT 1") public abstract TaskEntity task(String uid,long id);
    @Query("SELECT * FROM lessons WHERE id=:id AND ownerUid=:uid LIMIT 1") public abstract LessonEntity lesson(String uid,long id);
    @Query("SELECT * FROM notes WHERE id=:id AND ownerUid=:uid LIMIT 1") public abstract NoteEntity note(String uid,long id);

    @Insert public abstract long insertSubject(SubjectEntity value);
    @Insert public abstract long insertTask(TaskEntity value);
    @Insert public abstract long insertLesson(LessonEntity value);
    @Insert public abstract long insertNote(NoteEntity value);

    @Query("UPDATE subjects SET name=:name, teacher=:teacher, color=:color, icon=:icon WHERE id=:id AND ownerUid=:uid")
    public abstract int updateSubject(String uid,long id,String name,String teacher,int color,int icon);
    @Query("UPDATE tasks SET subjectId=:subjectId, title=:title, description=:description, kind=:kind, dueAt=:dueAt, priority=:priority, completed=:completed, updatedAt=:updatedAt WHERE id=:id AND ownerUid=:uid")
    public abstract int updateTask(String uid,long id,long subjectId,String title,String description,int kind,long dueAt,int priority,boolean completed,long updatedAt);
    @Query("UPDATE lessons SET subjectId=:subjectId, dayOfWeek=:day, startMinute=:start, endMinute=:end, room=:room, lessonNumber=:lessonNumber, lessonType=:lessonType, teacher=:teacher, meetingUrl=:meetingUrl WHERE id=:id AND ownerUid=:uid")
    public abstract int updateLesson(String uid,long id,long subjectId,int day,int start,int end,String room,int lessonNumber,String lessonType,String teacher,String meetingUrl);
    @Query("UPDATE notes SET subjectId=:subjectId, title=:title, content=:content, updatedAt=:updatedAt WHERE id=:id AND ownerUid=:uid")
    public abstract int updateNote(String uid,long id,Long subjectId,String title,String content,long updatedAt);

    @Query("DELETE FROM subjects WHERE id=:id AND ownerUid=:uid") public abstract int deleteSubject(String uid,long id);
    @Query("DELETE FROM tasks WHERE id=:id AND ownerUid=:uid") public abstract int deleteTask(String uid,long id);
    @Query("DELETE FROM lessons WHERE id=:id AND ownerUid=:uid") public abstract int deleteLesson(String uid,long id);
    @Query("DELETE FROM notes WHERE id=:id AND ownerUid=:uid") public abstract int deleteNote(String uid,long id);
    @Query("UPDATE tasks SET completed=:completed, updatedAt=:now WHERE id=:id AND ownerUid=:uid") public abstract int completeTask(String uid,long id,boolean completed,long now);
    @Query("SELECT (SELECT COUNT(*) FROM tasks WHERE ownerUid=:uid AND subjectId=:id) + (SELECT COUNT(*) FROM lessons WHERE ownerUid=:uid AND subjectId=:id) + (SELECT COUNT(*) FROM notes WHERE ownerUid=:uid AND subjectId=:id)")
    public abstract int subjectLinks(String uid,long id);
    @Query("SELECT COUNT(*) FROM seed_state WHERE ownerUid=:uid") public abstract int seeded(String uid);
    @Insert public abstract long insertSeed(SeedStateEntity value);
    @Query("SELECT COUNT(*) FROM dataset_imports WHERE ownerUid=:uid AND datasetKey=:datasetKey")
    public abstract int datasetImported(String uid,String datasetKey);
    @Insert public abstract void insertDatasetImport(DatasetImportEntity value);
}
