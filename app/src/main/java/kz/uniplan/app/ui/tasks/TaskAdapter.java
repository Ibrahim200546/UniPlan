package kz.uniplan.app.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.databinding.ItemCoreTaskBinding;

public final class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Holder> {
    public interface Actions { void open(long id); void complete(long id,boolean completed); void delete(long id); }
    private final Actions actions;
    private final boolean compact;
    private List<TaskEntity> items = new ArrayList<>();
    private List<SubjectEntity> subjects = new ArrayList<>();
    public TaskAdapter(Actions actions,boolean compact) { this.actions=actions; this.compact=compact; setHasStableIds(true); }
    public void submit(List<TaskEntity> items,List<SubjectEntity> subjects) {
        this.items=new ArrayList<>(items); this.subjects=subjects; notifyDataSetChanged();
    }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType) {
        return new Holder(ItemCoreTaskBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int position) {
        TaskEntity t=items.get(position);
        holder.b.titleText.setText(t.title);
        holder.b.metaText.setText(CoreText.taskMeta(holder.itemView.getContext(),t,subjects));
        holder.b.completed.setChecked(t.completed);
        holder.b.completed.setOnClickListener(v -> { boolean requested=holder.b.completed.isChecked(); holder.b.completed.setChecked(t.completed); actions.complete(t.id,requested); });
        holder.b.deleteButton.setVisibility(compact ? View.GONE : View.VISIBLE);
        holder.b.deleteButton.setOnClickListener(v -> actions.delete(t.id));
        holder.b.getRoot().setOnClickListener(v -> actions.open(t.id));
        kz.uniplan.app.util.Appearance.applyCard(holder.b.getRoot());
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCoreTaskBinding b;
        Holder(ItemCoreTaskBinding binding) { super(binding.getRoot()); b=binding; }
    }
}
