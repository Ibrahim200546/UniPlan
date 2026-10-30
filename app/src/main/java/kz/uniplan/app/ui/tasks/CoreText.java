package kz.uniplan.app.ui.tasks;

import android.content.Context;
import java.util.Calendar;
import java.util.List;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.domain.model.StudyItem;
import kz.uniplan.app.ui.common.Ui;

public final class CoreText {
    private CoreText() { }
    public static int today() { return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1; }
    public static String subject(Context c, List<SubjectEntity> subjects, long id) {
        if (subjects != null) for (SubjectEntity s : subjects) if (s.id == id) return s.name;
        return c.getString(R.string.no_subject);
    }
    public static SubjectEntity findSubject(List<SubjectEntity> list,long id) {
        if (list != null) for (SubjectEntity s : list) if (s.id == id) return s;
        return null;
    }
    public static String priority(Context c,int priority) {
        return c.getString(priority == 2 ? R.string.core_high : priority == 0 ? R.string.core_low : R.string.core_normal);
    }
    public static String time(Context c,int minutes) { return c.getString(R.string.core_time, minutes / 60, minutes % 60); }
    public static String taskMeta(Context c, TaskEntity task, List<SubjectEntity> subjects) {
        StudyItem item = StudyItem.from(task);
        String type = c.getString("exam".equals(item.typeKey()) ? R.string.core_exam : R.string.core_assignment);
        String state = c.getString(task.completed ? R.string.core_completed : item.isOverdue() ? R.string.core_overdue : R.string.core_active);
        return c.getString(R.string.core_task_meta, subject(c,subjects,task.subjectId),type,
                Ui.dateTime(task.dueAt), priority(c,task.priority) + " · " + state);
    }
}
