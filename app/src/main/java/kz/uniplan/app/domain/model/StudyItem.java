package kz.uniplan.app.domain.model;

import java.util.Comparator;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.util.Validation;

/** A domain object is immutable and validates its state independently from Room. */
public abstract class StudyItem {
    private final long id;
    private final String title;
    private final long dueAt;
    private final int priority;
    private final boolean completed;

    protected StudyItem(long id,String title,long dueAt,int priority,boolean completed) {
        if (!Validation.title(title) || dueAt <= 0 || priority < 0 || priority > 2) throw new IllegalArgumentException("Invalid study item");
        this.id = id; this.title = title.trim(); this.dueAt = dueAt; this.priority = priority; this.completed = completed;
    }
    public long getId() { return id; }
    public String getTitle() { return title; }
    public long getDueAt() { return dueAt; }
    public int getPriority() { return priority; }
    public boolean isCompleted() { return completed; }
    public boolean isOverdue(long now) { return Validation.overdue(completed,dueAt,now); }
    public boolean isOverdue() { return isOverdue(System.currentTimeMillis()); }
    public abstract int type();
    public abstract String typeKey();

    public static StudyItem from(TaskEntity value) {
        if (value.kind == 0) return new AssignmentItem(value.id,value.title,value.dueAt,value.priority,value.completed);
        if (value.kind == 1) return new ExamItem(value.id,value.title,value.dueAt,value.priority,value.completed);
        throw new IllegalArgumentException("Unknown study kind");
    }
    public static final Comparator<TaskEntity> BY_DUE = Comparator.comparingLong((TaskEntity value) -> value.dueAt).thenComparingLong(value -> value.id);
    public static final Comparator<TaskEntity> BY_PRIORITY = Comparator.comparingInt((TaskEntity value) -> value.priority).reversed().thenComparingLong(value -> value.dueAt).thenComparingLong(value -> value.id);
}
