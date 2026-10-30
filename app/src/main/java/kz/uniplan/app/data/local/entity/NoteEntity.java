package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;

@Entity(tableName = "notes", indices = {@Index(value = {"ownerUid", "subjectId"}), @Index(value = {"ownerUid", "updatedAt"})}, foreignKeys = {@ForeignKey(entity = SubjectEntity.class, parentColumns = {"ownerUid", "id"}, childColumns = {"ownerUid", "subjectId"}, onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.NO_ACTION)})
public class NoteEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String ownerUid = "";
    @Nullable public Long subjectId;
    @NonNull public String title = "";
    @NonNull public String content = "";
    public long createdAt;
    public long updatedAt;

    public NoteEntity copy() {
        NoteEntity value = new NoteEntity();
        value.id = id;
        value.ownerUid = ownerUid;
        value.subjectId = subjectId;
        value.title = title;
        value.content = content;
        value.createdAt = createdAt;
        value.updatedAt = updatedAt;
        return value;
    }
}
