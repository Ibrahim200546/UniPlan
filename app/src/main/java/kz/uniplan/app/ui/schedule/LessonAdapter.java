package kz.uniplan.app.ui.schedule;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.LessonEntity;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.databinding.ItemCoreLessonBinding;
import kz.uniplan.app.ui.tasks.CoreText;

public final class LessonAdapter extends RecyclerView.Adapter<LessonAdapter.Holder> {
    private List<LessonEntity> items=new ArrayList<>();
    private List<SubjectEntity> subjects;
    private final LongConsumer open;
    private final boolean showDay;
    public LessonAdapter(LongConsumer open,boolean showDay){this.open=open;this.showDay=showDay;setHasStableIds(true);}
    public void submit(List<LessonEntity> items,List<SubjectEntity> subjects){this.items=new ArrayList<>(items);this.subjects=subjects;notifyDataSetChanged();}
    @Override public long getItemId(int position){return items.get(position).id;}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemCoreLessonBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder h,int position){
        LessonEntity l=items.get(position);Context c=h.itemView.getContext();
        h.b.titleText.setText(CoreText.subject(c,subjects,l.subjectId));
        String time=c.getString(R.string.core_time_range,CoreText.time(c,l.startMinute),CoreText.time(c,l.endMinute));
        if(showDay)time=c.getResources().getStringArray(R.array.core_days)[l.dayOfWeek-1]+" · "+time;
        h.b.timeText.setText(time);SubjectEntity subject=CoreText.findSubject(subjects,l.subjectId);
        String meta=l.lessonNumber>0?c.getString(R.string.lesson_number_value,l.lessonNumber):"";
        if(!l.lessonType.isEmpty())meta+=(meta.isEmpty()?"":" · ")+l.lessonType;
        h.b.typeText.setText(meta);h.b.typeText.setVisibility(meta.isEmpty()?View.GONE:View.VISIBLE);
        String teacher=l.teacher.isEmpty()?(subject==null?"":subject.teacher):l.teacher;
        // The full credentials remain editable in the lesson detail; cards show the name.
        String[] teacherLines=teacher.split("\n");
        h.b.teacherText.setText(teacherLines.length==0?teacher:teacherLines[teacherLines.length-1]);
        h.b.teacherText.setVisibility(teacher.isEmpty()?View.GONE:View.VISIBLE);
        h.b.roomText.setText(c.getString(R.string.core_room_value,l.room));
        h.b.roomText.setVisibility(l.room.isEmpty()?View.GONE:View.VISIBLE);
        h.b.subjectColor.setBackgroundColor(subject==null?0xFF52796F:subject.color);
        h.b.openLink.setVisibility(l.meetingUrl.isEmpty()?View.GONE:View.VISIBLE);
        h.b.openLink.setOnClickListener(v->LessonLinks.open(c,l.meetingUrl));
        h.b.getRoot().setOnClickListener(v->open.accept(l.id));
        kz.uniplan.app.util.Appearance.applyCard(h.b.getRoot());
    }
    @Override public int getItemCount(){return items.size();}
    static final class Holder extends RecyclerView.ViewHolder{final ItemCoreLessonBinding b;Holder(ItemCoreLessonBinding b){super(b.getRoot());this.b=b;}}
}
