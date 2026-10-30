package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;

@Entity(tableName = "subjects", indices = {@Index(value = {"ownerUid", "id"}, unique = true), @Index(value = {"ownerUid", "name"})})
public class SubjectEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String ownerUid = "";
    @NonNull public String name = "";
    @NonNull public String teacher = "";
    public int color;
    public int icon;

    public SubjectEntity copy() {
        SubjectEntity value = new SubjectEntity();
        value.id = id;
        value.ownerUid = ownerUid;
        value.name = name;
        value.teacher = teacher;
        value.color = color;
        value.icon = icon;
        return value;
    }
}
