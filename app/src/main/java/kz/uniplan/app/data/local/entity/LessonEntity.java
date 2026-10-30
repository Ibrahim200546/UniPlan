package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "lessons", indices = {@Index(value = {"ownerUid", "subjectId"}), @Index(value = {"ownerUid", "dayOfWeek", "startMinute"})}, foreignKeys = {@ForeignKey(entity = SubjectEntity.class, parentColumns = {"ownerUid", "id"}, childColumns = {"ownerUid", "subjectId"}, onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.NO_ACTION)})
public class LessonEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String ownerUid = "";
    public long subjectId;
    public int dayOfWeek = 1;
    public int startMinute = 540;
    public int endMinute = 590;
    @NonNull public String room = "";
    @ColumnInfo(defaultValue = "0") public int lessonNumber;
    @NonNull @ColumnInfo(defaultValue = "''") public String lessonType = "";
    @NonNull @ColumnInfo(defaultValue = "''") public String teacher = "";
    @NonNull @ColumnInfo(defaultValue = "''") public String meetingUrl = "";

    public LessonEntity copy() {
        LessonEntity value = new LessonEntity();
        value.id = id;
        value.ownerUid = ownerUid;
        value.subjectId = subjectId;
        value.dayOfWeek = dayOfWeek;
        value.startMinute = startMinute;
        value.endMinute = endMinute;
        value.room = room;
        value.lessonNumber = lessonNumber;
        value.lessonType = lessonType;
        value.teacher = teacher;
        value.meetingUrl = meetingUrl;
        return value;
    }
}
