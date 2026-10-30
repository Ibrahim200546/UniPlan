package kz.uniplan.app.domain.model;

public final class AssignmentItem extends StudyItem {
    public AssignmentItem(long id,String title,long dueAt,int priority,boolean completed) { super(id,title,dueAt,priority,completed); }
    @Override public int type() { return 0; }
    @Override public String typeKey() { return "assignment"; }
}
