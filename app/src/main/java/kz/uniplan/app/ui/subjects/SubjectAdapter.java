package kz.uniplan.app.ui.subjects;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import java.util.ArrayList;
import java.util.List;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.databinding.ItemCoreSubjectBinding;

/** ListView reuses each row binding through a tag. */
public final class SubjectAdapter extends BaseAdapter {
    private List<SubjectEntity> items=new ArrayList<>();
    public void submit(List<SubjectEntity> items){this.items=items==null?new ArrayList<>():new ArrayList<>(items);notifyDataSetChanged();}
    @Override public int getCount(){return items.size();}
    @Override public SubjectEntity getItem(int position){return items.get(position);}
    @Override public long getItemId(int position){return items.get(position).id;}
    @Override public boolean hasStableIds(){return true;}
    @Override public View getView(int position,View convertView,ViewGroup parent){
        ItemCoreSubjectBinding b;
        if(convertView==null){b=ItemCoreSubjectBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);convertView=b.getRoot();convertView.setTag(b);}else b=(ItemCoreSubjectBinding)convertView.getTag();
        SubjectEntity subject=getItem(position);b.titleText.setText(subject.name);b.teacherText.setText(subject.teacher);
        b.icon.setImageResource(subject.icon==1?R.drawable.ic_tasks:subject.icon==2?R.drawable.ic_schedule:R.drawable.ic_book);
        b.icon.setImageTintList(ColorStateList.valueOf(subject.color));
        kz.uniplan.app.util.Appearance.applyRow(b.getRoot());
        return convertView;
    }
}
