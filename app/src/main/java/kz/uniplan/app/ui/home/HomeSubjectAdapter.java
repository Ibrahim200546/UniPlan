package kz.uniplan.app.ui.home;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.databinding.ItemHomeSubjectBinding;
import kz.uniplan.app.util.Appearance;
import kz.uniplan.app.util.AppearancePalette;

/** A compact shortcut to each real subject on the dashboard. */
final class HomeSubjectAdapter extends RecyclerView.Adapter<HomeSubjectAdapter.Holder> {
    private final List<SubjectEntity> items=new ArrayList<>();
    private final LongConsumer open;
    HomeSubjectAdapter(LongConsumer open){this.open=open;setHasStableIds(true);}
    void submit(List<SubjectEntity> subjects){items.clear();if(subjects!=null)items.addAll(subjects);notifyDataSetChanged();}
    @Override public long getItemId(int position){return items.get(position).id;}
    @Override public int getItemCount(){return items.size();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
        return new Holder(ItemHomeSubjectBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h,int position){
        SubjectEntity subject=items.get(position);
        h.b.titleText.setText(subject.name);
        String[] teacherLines=subject.teacher.split("\n");
        h.b.teacherText.setText(teacherLines.length==0?subject.teacher:teacherLines[teacherLines.length-1]);
        h.b.getRoot().setContentDescription(subject.name);
        h.b.teacherText.setVisibility(subject.teacher.isEmpty()?View.GONE:View.VISIBLE);
        h.b.icon.setImageResource(subject.icon==1?R.drawable.ic_tasks:subject.icon==2?R.drawable.ic_schedule:R.drawable.ic_book);
        String tone=Appearance.subjectTone(subject.color);
        Appearance.applyCard(h.b.getRoot(),tone);
        AppearancePalette palette=Appearance.palette(h.itemView.getContext());
        h.b.icon.setImageTintList(ColorStateList.valueOf(AppearancePalette.legible(subject.color,palette.tone(tone))));
        h.b.getRoot().setOnClickListener(v->open.accept(subject.id));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemHomeSubjectBinding b;
        Holder(ItemHomeSubjectBinding b){super(b.getRoot());this.b=b;}
    }
}
