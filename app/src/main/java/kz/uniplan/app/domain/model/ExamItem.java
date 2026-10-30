package kz.uniplan.app.domain.model;

public final class ExamItem extends StudyItem {
    public ExamItem(long id,String title,long dueAt,int priority,boolean completed) { super(id,title,dueAt,priority,completed); }
    @Override public int type() { return 1; }
    @Override public String typeKey() { return "exam"; }
}
