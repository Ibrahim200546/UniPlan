package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;

@Entity(tableName = "seed_state")
public class SeedStateEntity {
    @PrimaryKey @NonNull public String ownerUid = "";
    public long seededAt;

    public SeedStateEntity copy() {
        SeedStateEntity value = new SeedStateEntity();
        value.ownerUid = ownerUid;
        value.seededAt = seededAt;
        return value;
    }
}
