package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;

@Entity(tableName = "tasks", indices = {@Index(value = {"ownerUid", "subjectId"}), @Index(value = {"ownerUid", "dueAt"})}, foreignKeys = {@ForeignKey(entity = SubjectEntity.class, parentColumns = {"ownerUid", "id"}, childColumns = {"ownerUid", "subjectId"}, onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.NO_ACTION)})
public class TaskEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String ownerUid = "";
    public long subjectId;
    @NonNull public String title = "";
    @NonNull public String description = "";
    public int kind;
    public long dueAt;
    public int priority = 1;
    public boolean completed;
    public long createdAt;
    public long updatedAt;

    public TaskEntity copy() {
        TaskEntity value = new TaskEntity();
        value.id = id;
        value.ownerUid = ownerUid;
        value.subjectId = subjectId;
        value.title = title;
        value.description = description;
        value.kind = kind;
        value.dueAt = dueAt;
        value.priority = priority;
        value.completed = completed;
        value.createdAt = createdAt;
        value.updatedAt = updatedAt;
        return value;
    }
}
