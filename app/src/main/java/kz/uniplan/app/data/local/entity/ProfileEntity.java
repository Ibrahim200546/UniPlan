package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;

@Entity(tableName = "profiles")
public class ProfileEntity {
    @PrimaryKey @NonNull public String ownerUid = "";
    @NonNull public String displayName = "";
    @NonNull public String studyGroup = "";

    public ProfileEntity copy() {
        ProfileEntity value = new ProfileEntity();
        value.ownerUid = ownerUid;
        value.displayName = displayName;
        value.studyGroup = studyGroup;
        return value;
    }
}
