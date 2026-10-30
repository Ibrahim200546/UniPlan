package kz.uniplan.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** A durable import marker, independent of optional sample data and individual lessons. */
@Entity(tableName = "dataset_imports", primaryKeys = {"ownerUid", "datasetKey"})
public class DatasetImportEntity {
    @NonNull public String ownerUid = "";
    @NonNull public String datasetKey = "";
    public long importedAt;
}
